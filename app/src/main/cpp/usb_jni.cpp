#include <jni.h>

#include <android/log.h>

#include <cmath>
#include <cstdio>
#include <memory>
#include <mutex>

#include "iso_stream.h"
#include "uac.h"
#include "usb_context.h"

#define LOG_TAG "SamSonicUsb"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {
// The DAC currently playing the test tone: the libusb handle wrapping the Java side's descriptor.
struct Session {
    libusb_device_handle* handle = nullptr;
    UacDevice device;
    IsoStream stream;
};
std::mutex sessionLock;
std::unique_ptr<Session> session;

jstring text(JNIEnv* env, const std::string& s) { return env->NewStringUTF(s.c_str()); }

// Wraps [fd] and reads the active configuration. Returns an error text, or empty on success.
std::string openDevice(int fd, libusb_device_handle** handle, UacDevice& out) {
    libusb_context* ctx = sharedUsbContext();
    if (!ctx) return "libusb didn't start";
    int r = libusb_wrap_sys_device(ctx, static_cast<intptr_t>(fd), handle);
    if (r != 0) return std::string("wrap failed: ") + libusb_error_name(r);
    libusb_device* dev = libusb_get_device(*handle);
    libusb_config_descriptor* cfg = nullptr;
    r = libusb_get_active_config_descriptor(dev, &cfg);
    if (r != 0) {
        libusb_close(*handle);
        *handle = nullptr;
        return std::string("reading the configuration failed: ") + libusb_error_name(r);
    }
    bool ok = parseUac(dev, cfg, out);
    libusb_free_config_descriptor(cfg);
    if (!ok) {
        libusb_close(*handle);
        *handle = nullptr;
        return "not a USB audio device the driver understands";
    }
    return "";
}

void closeSession() {
    if (!session) return;
    session->stream.stop();
    // The wrapped fd stays open: it belongs to the UsbDeviceConnection.
    libusb_close(session->handle);
    session.reset();
}

// A quiet sine, faded in and out, written in the alt setting's layout.
IsoStream::Pull sineSource(const UacAlt& alt, uint32_t rate, double seconds) {
    struct State {
        uint64_t frame = 0;
    };
    auto state = std::make_shared<State>();
    const uint64_t total = static_cast<uint64_t>(rate * seconds);
    const uint64_t fade = rate / 20;
    const double amplitude = 0.0316;  // -30 dBFS
    const int channels = alt.channels;
    const int subslot = alt.subslotBytes;
    return [=](uint8_t* dst, size_t frames) {
        for (size_t i = 0; i < frames; i++) {
            double v = 0;
            if (state->frame < total) {
                double gain = 1.0;
                if (state->frame < fade) gain = static_cast<double>(state->frame) / fade;
                if (total - state->frame < fade) gain = static_cast<double>(total - state->frame) / fade;
                v = std::sin(2.0 * M_PI * 440.0 * static_cast<double>(state->frame) / rate) * amplitude * gain;
                state->frame++;
            }
            int32_t sample = static_cast<int32_t>(v * 2147483647.0);
            for (int c = 0; c < channels; c++) {
                // Left-justified in the subslot: the top bytes of the 32-bit sample, low byte first.
                for (int b = 0; b < subslot; b++) {
                    *dst++ = static_cast<uint8_t>(sample >> (32 - 8 * subslot + 8 * b));
                }
            }
        }
    };
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_libusbVersion(JNIEnv* env, jobject /* this */) {
    const libusb_version* v = libusb_get_version();
    char buf[48];
    snprintf(buf, sizeof(buf), "%d.%d.%d.%d", v->major, v->minor, v->micro, v->nano);
    return text(env, buf);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_describe(JNIEnv* env, jobject /* this */, jint fd) {
    libusb_device_handle* handle = nullptr;
    UacDevice dev;
    std::string error = openDevice(fd, &handle, dev);
    if (!error.empty()) return text(env, error);
    libusb_device_descriptor desc{};
    libusb_get_device_descriptor(libusb_get_device(handle), &desc);
    char head[96];
    snprintf(head, sizeof(head), "%04x:%04x, %d configuration(s), USB %x.%02x, speed %d\n", desc.idVendor,
             desc.idProduct, desc.bNumConfigurations, desc.bcdUSB >> 8, desc.bcdUSB & 0xff,
             libusb_get_device_speed(libusb_get_device(handle)));
    // Rates are read per alt setting's clock; the first alt is enough for the log.
    libusb_claim_interface(handle, dev.controlInterface);
    queryClockRates(handle, dev, dev.alts.front());
    libusb_release_interface(handle, dev.controlInterface);
    std::string result = head + describeUac(dev);
    libusb_close(handle);
    return text(env, result);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_startTestTone(JNIEnv* env, jobject /* this */, jint fd, jint rate) {
    std::lock_guard<std::mutex> guard(sessionLock);
    closeSession();
    auto s = std::make_unique<Session>();
    std::string error = openDevice(fd, &s->handle, s->device);
    if (!error.empty()) return text(env, error);

    libusb_claim_interface(s->handle, s->device.controlInterface);
    const UacAlt* chosen = nullptr;
    for (const UacAlt& alt : s->device.alts) {
        if (!alt.pcm || alt.channels < 2) continue;
        queryClockRates(s->handle, s->device, alt);
        if (!supportsRate(s->device, alt, rate)) continue;
        // The widest format that takes the rate.
        if (!chosen || alt.subslotBytes > chosen->subslotBytes) chosen = &alt;
    }
    libusb_release_interface(s->handle, s->device.controlInterface);
    if (!chosen) {
        libusb_close(s->handle);
        return text(env, "no PCM alt setting takes " + std::to_string(rate) + " Hz");
    }
    UacAlt alt = *chosen;
    queryClockRates(s->handle, s->device, alt);
    error = s->stream.start(sharedUsbContext(), s->handle, s->device, alt, rate, sineSource(alt, rate, 2.0));
    if (!error.empty()) {
        libusb_close(s->handle);
        return text(env, error);
    }
    session = std::move(s);
    char buf[128];
    snprintf(buf, sizeof(buf), "playing: intf %d alt %d, %d-bit in %d byte(s), %d Hz", alt.interfaceNumber,
             alt.altSetting, alt.bitResolution, alt.subslotBytes, rate);
    LOGI("%s", buf);
    return text(env, buf);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_stopTestTone(JNIEnv* /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> guard(sessionLock);
    closeSession();
}
