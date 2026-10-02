#include "iso_stream.h"

#include <android/log.h>
#include <sched.h>
#include <sys/resource.h>
#include <unistd.h>

#include <algorithm>
#include <chrono>
#include <cstring>

#define LOG_TAG "SamSonicUsb"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {
constexpr int kUrbMillis = 4;  // audio in each transfer
constexpr int kUrbCount = 8;   // transfers in flight, so about 32 ms is queued

// How often the endpoint is served: every 2^(bInterval-1) frames (1 ms) or microframes (125 us).
uint32_t serviceHz(libusb_device_handle* handle, int interval) {
    int speed = libusb_get_device_speed(libusb_get_device(handle));
    uint32_t base = speed >= LIBUSB_SPEED_HIGH ? 8000 : 1000;
    return base >> std::clamp(interval - 1, 0, 8);
}

// Audio threads want to run on time. Real-time scheduling usually isn't allowed for an app, so
// fall back to the highest ordinary priority.
void raiseThreadPriority() {
    sched_param param{};
    param.sched_priority = 2;
    if (sched_setscheduler(0, SCHED_FIFO, &param) == 0) {
        LOGI("USB audio thread: SCHED_FIFO");
    } else if (setpriority(PRIO_PROCESS, 0, -19) == 0) {
        LOGI("USB audio thread: nice -19");
    } else {
        LOGW("USB audio thread: normal priority");
    }
}
}  // namespace

std::string IsoStream::start(libusb_context* ctx, libusb_device_handle* handle, const UacDevice& dev,
                             const UacAlt& alt, uint32_t rate) {
    stop();
    ctx_ = ctx;
    handle_ = handle;
    alt_ = alt;
    controlInterface_ = dev.controlInterface;
    frameBytes_ = static_cast<size_t>(alt.channels) * alt.subslotBytes;
    if (frameBytes_ == 0 || alt.dataMaxPacket <= 0) return "the alt setting has no usable format";
    maxFramesPerPacket_ = alt.dataMaxPacket / frameBytes_;

    uint32_t hz = serviceHz(handle, alt.dataInterval);
    nominalQ16_ = (static_cast<uint64_t>(rate) << 16) / hz;
    if ((nominalQ16_ >> 16) + 1 > maxFramesPerPacket_) return "the endpoint's packets are too small for that rate";
    rateQ16_ = nominalQ16_;
    phase_ = 0;
    feedbackShiftKnown_ = false;
    errorLogs_ = 0;
    epoch_ = 0;
    transfersDone_ = packetsBad_ = underruns_ = feedbackCount_ = 0;
    playedFrames_ = 0;
    paused_ = false;
    silence_ = 0;
    lastMarker_ = 0xFA;
    flushRequested_ = false;
    packetsPerUrb_ = std::max<int>(1, hz * kUrbMillis / 1000);
    // About half a second of audio.
    ring_.reset(static_cast<size_t>(rate) * frameBytes_ / 2);

    // The same file descriptor is claimed on the Java side; this registers the claim with libusb.
    int r = libusb_claim_interface(handle, controlInterface_);
    if (r != 0) LOGW("claiming control interface: %s", libusb_error_name(r));
    r = libusb_claim_interface(handle, alt.interfaceNumber);
    if (r != 0) return std::string("claiming the streaming interface failed: ") + libusb_error_name(r);
    // Some DACs want the rate before the stream starts, some after: set it on both sides.
    std::string rateError = setSampleRate(handle, dev, alt, rate);
    if (!rateError.empty()) LOGW("rate before alt: %s", rateError.c_str());
    r = libusb_set_interface_alt_setting(handle, alt.interfaceNumber, alt.altSetting);
    if (r != 0) {
        libusb_release_interface(handle, alt.interfaceNumber);
        return std::string("selecting the alt setting failed: ") + libusb_error_name(r);
    }
    rateError = setSampleRate(handle, dev, alt, rate);
    if (!rateError.empty()) LOGW("rate after alt: %s", rateError.c_str());

    stopping_ = false;
    inflight_ = 0;
    started_ = true;  // from here stop() tears down whatever was set up
    size_t bufferBytes = static_cast<size_t>(packetsPerUrb_) * alt.dataMaxPacket;
    for (int i = 0; i < kUrbCount; i++) {
        libusb_transfer* t = libusb_alloc_transfer(packetsPerUrb_);
        if (!t) {
            running_ = true;
            worker_ = std::thread(&IsoStream::run, this);
            stop();
            return "out of memory";
        }
        buffers_.emplace_back(bufferBytes);
        transfers_.push_back(std::make_unique<Transfer>());
        transfers_.back()->stream = this;
        libusb_fill_iso_transfer(t, handle, alt.dataEp, buffers_.back().data(), static_cast<int>(bufferBytes),
                                 packetsPerUrb_, &IsoStream::onData, transfers_.back().get(), 0);
        data_.push_back(t);
    }
    if (alt.feedbackEp != 0 && alt.feedbackMaxPacket > 0) {
        feedback_ = libusb_alloc_transfer(1);
        feedbackBuffer_.assign(alt.feedbackMaxPacket, 0);
        libusb_fill_iso_transfer(feedback_, handle, alt.feedbackEp, feedbackBuffer_.data(), alt.feedbackMaxPacket, 1,
                                 &IsoStream::onFeedback, this, 0);
        libusb_set_iso_packet_lengths(feedback_, alt.feedbackMaxPacket);
    }

    std::string error;
    for (libusb_transfer* t : data_) {
        fill(t);
        r = libusb_submit_transfer(t);
        if (r != 0) {
            error = std::string("submitting audio failed: ") + libusb_error_name(r);
            break;
        }
        inflight_++;
    }
    if (error.empty() && feedback_ && libusb_submit_transfer(feedback_) == 0) inflight_++;
    // Even on failure the worker runs, to cancel what was submitted and let it all come back.
    running_ = true;
    worker_ = std::thread(&IsoStream::run, this);
    if (!error.empty()) {
        stop();
        return error;
    }
    LOGI("streaming %u Hz, %d-byte frames, %d packets per transfer, %s feedback", rate,
         static_cast<int>(frameBytes_), packetsPerUrb_, feedback_ ? "with" : "without");
    return "";
}

void IsoStream::stop() {
    if (!started_) return;
    stopping_ = true;
    if (worker_.joinable()) worker_.join();
    running_ = false;
    LOGI("stopped: %llu transfers, %llu bad packets, %llu underruns, %llu feedback values, rate %.4f per interval",
         static_cast<unsigned long long>(transfersDone_), static_cast<unsigned long long>(packetsBad_),
         static_cast<unsigned long long>(underruns_), static_cast<unsigned long long>(feedbackCount_),
         rateQ16_ / 65536.0);
    for (libusb_transfer* t : data_) libusb_free_transfer(t);
    data_.clear();
    buffers_.clear();
    transfers_.clear();
    if (feedback_) libusb_free_transfer(feedback_);
    feedback_ = nullptr;
    libusb_set_interface_alt_setting(handle_, alt_.interfaceNumber, 0);
    libusb_release_interface(handle_, alt_.interfaceNumber);
    libusb_release_interface(handle_, controlInterface_);
    started_ = false;
}

size_t IsoStream::write(const uint8_t* data, size_t bytes) {
    if (!started_ || frameBytes_ == 0) return 0;
    return ring_.write(data, bytes - bytes % frameBytes_);
}

void IsoStream::flush() {
    if (!started_) return;
    flushRequested_ = true;
    // The worker answers at its next refill, a few milliseconds away.
    auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(100);
    while (flushRequested_ && running_ && std::chrono::steady_clock::now() < deadline) {
        usleep(500);
    }
    if (flushRequested_.exchange(false)) LOGW("flush wasn't answered in time");
}

void IsoStream::run() {
    raiseThreadPriority();
    bool cancelled = false;
    while (true) {
        if (stopping_ && !cancelled) {
            // Cancelled here, on the thread that handles completions, so nothing races a resubmit.
            for (libusb_transfer* t : data_) libusb_cancel_transfer(t);
            if (feedback_) libusb_cancel_transfer(feedback_);
            cancelled = true;
        }
        if (stopping_ && inflight_ <= 0) break;
        timeval tv{0, 50000};
        libusb_handle_events_timeout(ctx_, &tv);
    }
}

void IsoStream::fill(libusb_transfer* t) {
    auto* transfer = static_cast<Transfer*>(t->user_data);
    if (flushRequested_.exchange(false)) {
        ring_.discard();
        epoch_++;
    }
    transfer->epoch = epoch_;
    transfer->realFrames = 0;
    const bool paused = paused_.load(std::memory_order_acquire);
    uint8_t* out = t->buffer;
    int total = 0;
    for (int i = 0; i < t->num_iso_packets; i++) {
        phase_ += rateQ16_;
        size_t frames = std::min<size_t>(phase_ >> 16, maxFramesPerPacket_);
        phase_ &= 0xFFFF;
        size_t bytes = frames * frameBytes_;
        size_t real = 0;
        if (!paused) {
            // Whole frames only: the ring holds whole frames, so this is exact.
            real = ring_.read(out, bytes);
            if (real < bytes) underruns_++;
        }
        const int mode = silence_.load(std::memory_order_acquire);
        if (mode == 2 && real > 0) lastMarker_ = out[real - frameBytes_ + alt_.subslotBytes - 1];
        if (real < bytes) fillSilence(out + real, (bytes - real) / frameBytes_, mode);
        transfer->realFrames += real / frameBytes_;
        t->iso_packet_desc[i].length = static_cast<unsigned int>(bytes);
        out += bytes;
        total += static_cast<int>(bytes);
    }
    t->length = total;
}

void IsoStream::fillSilence(uint8_t* out, size_t frames, int mode) {
    const size_t slot = alt_.subslotBytes;
    if (mode == 0) {
        memset(out, 0, frames * frameBytes_);
    } else if (mode == 1) {
        memset(out, 0x69, frames * frameBytes_);  // 0x69 is a DSD byte of silence
    } else {
        for (size_t f = 0; f < frames; f++) {
            lastMarker_ = lastMarker_ == 0x05 ? 0xFA : 0x05;
            for (int c = 0; c < alt_.channels; c++) {
                // The sample's top byte is the marker, the two below it are DSD silence.
                uint8_t* s = out;
                memset(s, 0, slot);
                s[slot - 1] = lastMarker_;
                s[slot - 2] = 0x69;
                s[slot - 3] = 0x69;
                out += slot;
            }
        }
    }
}

void LIBUSB_CALL IsoStream::onData(libusb_transfer* t) {
    auto* transfer = static_cast<Transfer*>(t->user_data);
    IsoStream* self = transfer->stream;
    if (self->stopping_ || t->status == LIBUSB_TRANSFER_CANCELLED || t->status == LIBUSB_TRANSFER_NO_DEVICE) {
        self->inflight_--;
        return;
    }
    self->transfersDone_++;
    for (int i = 0; i < t->num_iso_packets; i++) {
        if (t->iso_packet_desc[i].status != LIBUSB_TRANSFER_COMPLETED) {
            self->packetsBad_++;
            if (self->errorLogs_++ < 5) LOGW("audio packet status %d", t->iso_packet_desc[i].status);
        }
    }
    if (t->status != LIBUSB_TRANSFER_COMPLETED && self->errorLogs_++ < 5) {
        LOGW("audio transfer status %d", t->status);
    }
    // Frames from before a flush don't count: they're no longer in the player's timeline.
    if (transfer->epoch == self->epoch_) self->playedFrames_ += transfer->realFrames;
    self->fill(t);
    if (libusb_submit_transfer(t) != 0) {
        self->inflight_--;
        LOGW("couldn't resubmit audio");
    }
}

void LIBUSB_CALL IsoStream::onFeedback(libusb_transfer* t) {
    auto* self = static_cast<IsoStream*>(t->user_data);
    if (self->stopping_ || t->status == LIBUSB_TRANSFER_CANCELLED || t->status == LIBUSB_TRANSFER_NO_DEVICE) {
        self->inflight_--;
        return;
    }
    self->feedbackCount_++;
    self->takeFeedback(t);
    if (libusb_submit_transfer(t) != 0) self->inflight_--;
}

// The feedback value is the samples the DAC wants per service interval, 16.16 on high speed and
// 10.14 on full speed. Devices disagree, so the first value is shifted until it matches the
// nominal rate, as Linux does.
void IsoStream::takeFeedback(libusb_transfer* t) {
    const libusb_iso_packet_descriptor& p = t->iso_packet_desc[0];
    if (p.status != LIBUSB_TRANSFER_COMPLETED || p.actual_length < 3) return;
    uint64_t f = t->buffer[0] | (t->buffer[1] << 8) | (t->buffer[2] << 16);
    if (p.actual_length >= 4) f |= static_cast<uint64_t>(t->buffer[3]) << 24;
    if (f == 0) return;
    if (!feedbackShiftKnown_) {
        int shift = 0;
        uint64_t v = f;
        while (v < nominalQ16_ - nominalQ16_ / 4 && shift < 16) { v <<= 1; shift++; }
        while (v > nominalQ16_ + nominalQ16_ / 2 && shift > -16) { v >>= 1; shift--; }
        feedbackShift_ = shift;
        feedbackShiftKnown_ = true;
        LOGI("feedback: first value %llu, shift %d, nominal %llu", static_cast<unsigned long long>(f), shift,
             static_cast<unsigned long long>(nominalQ16_));
    }
    uint64_t v = feedbackShift_ >= 0 ? f << feedbackShift_ : f >> -feedbackShift_;
    // Ignore values far from the nominal rate, and smooth the rest.
    if (v < nominalQ16_ - nominalQ16_ / 10 || v > nominalQ16_ + nominalQ16_ / 10) return;
    rateQ16_ = (rateQ16_ * 3 + v) / 4;
}
