#include <jni.h>

#include <android/log.h>

#include <cstdio>
#include <memory>
#include <mutex>
#include <string>

#include "iso_stream.h"
#include "uac.h"
#include "usb_context.h"

#define LOG_TAG "SamSonicUsb"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {
// The DAC the player is using: a libusb handle wrapping the Java side's descriptor.
struct Session {
    libusb_device_handle* handle = nullptr;
    UacDevice device;
    IsoStream stream;
};
std::mutex sessionLock;
std::unique_ptr<Session> session;

// The rates a player is likely to ask for, from CD to DSD512 as DoP.
constexpr uint32_t kCandidateRates[] = {44100,  48000,  88200,  96000,  176400, 192000,
                                        352800, 384000, 705600, 768000, 1411200};

jstring text(JNIEnv* env, const std::string& s) { return env->NewStringUTF(s.c_str()); }

// Logs the interfaces, endpoints and class-specific descriptors of [cfg].
void logConfig(const libusb_config_descriptor* cfg) {
    LOGI(" configuration %d: %d interface(s)", cfg->bConfigurationValue, cfg->bNumInterfaces);
    for (int i = 0; i < cfg->bNumInterfaces; i++) {
        for (int a = 0; a < cfg->interface[i].num_altsetting; a++) {
            const libusb_interface_descriptor& alt = cfg->interface[i].altsetting[a];
            LOGI("  intf %d alt %d: class %d subclass %d protocol %d, %d endpoint(s), %d extra byte(s)",
                 alt.bInterfaceNumber, alt.bAlternateSetting, alt.bInterfaceClass, alt.bInterfaceSubClass,
                 alt.bInterfaceProtocol, alt.bNumEndpoints, alt.extra_length);
            std::string hex;
            for (int b = 0; b < alt.extra_length && b < 96; b++) {
                char byte[4];
                snprintf(byte, sizeof(byte), "%02x ", alt.extra[b]);
                hex += byte;
            }
            if (!hex.empty()) LOGI("    extra: %s", hex.c_str());
            for (int e = 0; e < alt.bNumEndpoints; e++) {
                const libusb_endpoint_descriptor& ep = alt.endpoint[e];
                LOGI("    ep 0x%02x attributes 0x%02x max packet %d interval %d", ep.bEndpointAddress, ep.bmAttributes,
                     ep.wMaxPacketSize, ep.bInterval);
            }
        }
    }
}

// Logs every configuration of a device the parser rejected, the active one first.
void logRejected(libusb_device* dev, const libusb_config_descriptor* active) {
    libusb_device_descriptor desc{};
    libusb_get_device_descriptor(dev, &desc);
    LOGI("rejected %04x:%04x, device class %d, %d configuration(s), active is %d", desc.idVendor, desc.idProduct,
         desc.bDeviceClass, desc.bNumConfigurations, active->bConfigurationValue);
    logConfig(active);
    for (int c = 0; c < desc.bNumConfigurations; c++) {
        libusb_config_descriptor* cfg = nullptr;
        if (libusb_get_config_descriptor(dev, static_cast<uint8_t>(c), &cfg) != 0) continue;
        if (cfg->bConfigurationValue != active->bConfigurationValue) logConfig(cfg);
        libusb_free_config_descriptor(cfg);
    }
}

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
    if (!ok) logRejected(dev, cfg);
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
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_libusbVersion(JNIEnv* env, jobject /* this */) {
    const libusb_version* v = libusb_get_version();
    char buf[48];
    snprintf(buf, sizeof(buf), "%d.%d.%d.%d", v->major, v->minor, v->micro, v->nano);
    return text(env, buf);
}

// Opens the DAC behind [fd] for playing; "" on success, otherwise what went wrong. Any DAC
// opened before is closed. Describes the DAC in the log.
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_open(JNIEnv* env, jobject /* this */, jint fd) {
    std::lock_guard<std::mutex> guard(sessionLock);
    closeSession();
    auto s = std::make_unique<Session>();
    std::string error = openDevice(fd, &s->handle, s->device);
    if (!error.empty()) return text(env, error);

    libusb_device_descriptor desc{};
    libusb_get_device_descriptor(libusb_get_device(s->handle), &desc);
    libusb_claim_interface(s->handle, s->device.controlInterface);
    for (const UacAlt& alt : s->device.alts) {
        if (alt.pcm) {
            queryClockRates(s->handle, s->device, alt);
            break;
        }
    }
    libusb_release_interface(s->handle, s->device.controlInterface);
    LOGI("%04x:%04x, speed %d\n%s", desc.idVendor, desc.idProduct,
         libusb_get_device_speed(libusb_get_device(s->handle)), describeUac(s->device).c_str());
    session = std::move(s);
    return text(env, "");
}

// Whether the DAC behind [fd] is one the driver understands. Reads its descriptors only: nothing is
// claimed, so Android's own USB audio driver stays attached to a DAC that's turned down.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_understands(JNIEnv* /* env */, jobject /* this */, jint fd) {
    libusb_device_handle* handle = nullptr;
    UacDevice device;
    if (!openDevice(fd, &handle, device).empty()) return JNI_FALSE;
    libusb_close(handle);
    return JNI_TRUE;
}

// The alt settings that play audio, five ints each: index, channels, bytes per sample, bits, and the
// kind: 1 PCM, 2 raw data (native DSD, on DACs that take it), 0 anything else.
extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_formats(JNIEnv* env, jobject /* this */) {
    std::lock_guard<std::mutex> guard(sessionLock);
    std::vector<jint> flat;
    if (session) {
        jint index = 0;
        for (const UacAlt& alt : session->device.alts) {
            flat.insert(flat.end(), {index++, alt.channels, alt.subslotBytes, alt.bitResolution,
                        alt.pcm ? 1 : (alt.rawData ? 2 : 0)});
        }
    }
    jintArray result = env->NewIntArray(static_cast<jsize>(flat.size()));
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(flat.size()), flat.data());
    return result;
}

// Which of the common rates the DAC takes in alt setting [index].
extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_rates(JNIEnv* env, jobject /* this */, jint index) {
    std::lock_guard<std::mutex> guard(sessionLock);
    std::vector<jint> rates;
    if (session && index >= 0 && index < static_cast<jint>(session->device.alts.size())) {
        const UacAlt& alt = session->device.alts[index];
        for (uint32_t rate : kCandidateRates) {
            if (supportsRate(session->device, alt, rate)) rates.push_back(static_cast<jint>(rate));
        }
    }
    jintArray result = env->NewIntArray(static_cast<jsize>(rates.size()));
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(rates.size()), rates.data());
    return result;
}

// Starts streaming alt setting [index] at [rate]; "" on success, otherwise what went wrong.
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_start(JNIEnv* env, jobject /* this */, jint index, jint rate) {
    std::lock_guard<std::mutex> guard(sessionLock);
    if (!session) return text(env, "no DAC is open");
    if (index < 0 || index >= static_cast<jint>(session->device.alts.size())) return text(env, "no such alt setting");
    UacAlt alt = session->device.alts[index];
    libusb_claim_interface(session->handle, session->device.controlInterface);
    queryClockRates(session->handle, session->device, alt);
    libusb_release_interface(session->handle, session->device.controlInterface);
    return text(env, session->stream.start(sharedUsbContext(), session->handle, session->device, alt, rate));
}

// Queues up to [length] bytes of whole frames from a direct buffer; returns the bytes taken.
extern "C" JNIEXPORT jint JNICALL Java_com_example_samsonic_playback_usb_NativeUsb_write(
    JNIEnv* env, jobject /* this */, jobject buffer, jint offset, jint length) {
    auto* base = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    if (!base || !session) return 0;
    return static_cast<jint>(session->stream.write(base + offset, static_cast<size_t>(length)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_freeBytes(JNIEnv* /* env */, jobject /* this */) {
    return session ? static_cast<jint>(session->stream.freeBytes()) : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_bufferedBytes(JNIEnv* /* env */, jobject /* this */) {
    return session ? static_cast<jint>(session->stream.bufferedBytes()) : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_playedFrames(JNIEnv* /* env */, jobject /* this */) {
    return session ? static_cast<jlong>(session->stream.playedFrames()) : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_setPaused(JNIEnv* /* env */, jobject /* this */, jboolean paused) {
    if (session) session->stream.setPaused(paused);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_setSilence(JNIEnv* /* env */, jobject /* this */, jint mode) {
    if (session) session->stream.setSilence(mode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_flush(JNIEnv* /* env */, jobject /* this */) {
    if (session) session->stream.flush();
}

// Stops streaming but keeps the DAC open, for the next format.
extern "C" JNIEXPORT void JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_stop(JNIEnv* /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> guard(sessionLock);
    if (session) session->stream.stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_close(JNIEnv* /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> guard(sessionLock);
    closeSession();
}
