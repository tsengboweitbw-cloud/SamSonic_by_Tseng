#pragma once

#include <libusb.h>

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>
#include <thread>
#include <vector>

#include "byte_ring.h"
#include "uac.h"

/**
 * Plays audio to a DAC's isochronous OUT endpoint. The player writes whole frames into a ring;
 * a worker thread keeps several transfers in flight and refills each from the ring as it
 * completes, with silence when the ring runs dry or playback is paused. Asynchronous DACs set
 * the pace: their feedback endpoint says how many samples they want per (micro)frame.
 */
class IsoStream {
public:
    ~IsoStream() { stop(); }

    /** Selects [alt], sets [rate] and starts streaming. An empty string on success, otherwise what went wrong. */
    std::string start(libusb_context* ctx, libusb_device_handle* handle, const UacDevice& dev, const UacAlt& alt,
                      uint32_t rate);

    /** Stops streaming and gives the interface back at alt 0. Safe to call when not started. */
    void stop();

    /** Player thread: queues whole frames; returns the bytes taken (less than [bytes] when the ring is full). */
    size_t write(const uint8_t* data, size_t bytes);
    size_t freeBytes() const { return ring_.writable(); }
    size_t bufferedBytes() const { return ring_.readable(); }
    size_t frameBytes() const { return frameBytes_; }

    /** Real frames (not silence) that have gone out to the DAC since [start], less those dropped by [flush]. */
    uint64_t playedFrames() const { return playedFrames_.load(std::memory_order_acquire); }

    /** While paused the DAC gets silence and the ring keeps its audio. */
    void setPaused(bool paused) { paused_.store(paused, std::memory_order_release); }

    /** Drops the queued audio; returns once the worker has, so [playedFrames] is settled. */
    void flush();

private:
    // One per transfer: the audio it carries, and which flush it came after.
    struct Transfer {
        IsoStream* stream = nullptr;
        uint32_t epoch = 0;
        uint64_t realFrames = 0;
    };

    static void LIBUSB_CALL onData(libusb_transfer* t);
    static void LIBUSB_CALL onFeedback(libusb_transfer* t);
    void fill(libusb_transfer* t);
    void takeFeedback(libusb_transfer* t);
    void run();

    libusb_context* ctx_ = nullptr;
    libusb_device_handle* handle_ = nullptr;
    UacAlt alt_;
    int controlInterface_ = -1;
    size_t frameBytes_ = 0;
    size_t maxFramesPerPacket_ = 0;
    int packetsPerUrb_ = 0;
    ByteRing ring_;

    // Samples per service interval in Q16.16, and the fraction carried to the next packet.
    // Only the worker thread touches these once streaming has started.
    uint64_t nominalQ16_ = 0;
    uint64_t rateQ16_ = 0;
    uint64_t phase_ = 0;
    int feedbackShift_ = 0;
    bool feedbackShiftKnown_ = false;
    int errorLogs_ = 0;
    uint32_t epoch_ = 0;

    // For the log when streaming stops.
    uint64_t transfersDone_ = 0;
    uint64_t packetsBad_ = 0;
    uint64_t underruns_ = 0;
    uint64_t feedbackCount_ = 0;

    std::vector<libusb_transfer*> data_;
    std::vector<std::vector<uint8_t>> buffers_;
    std::vector<std::unique_ptr<Transfer>> transfers_;
    libusb_transfer* feedback_ = nullptr;
    std::vector<uint8_t> feedbackBuffer_;
    int inflight_ = 0;

    std::atomic<uint64_t> playedFrames_{0};
    std::atomic<bool> paused_{false};
    std::atomic<bool> flushRequested_{false};
    std::atomic<bool> stopping_{false};
    std::atomic<bool> running_{false};
    std::thread worker_;
    bool started_ = false;
};
