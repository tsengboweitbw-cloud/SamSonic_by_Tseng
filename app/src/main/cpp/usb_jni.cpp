#include <jni.h>

#include <android/log.h>
#include <cstdio>
#include <libusb.h>

#define LOG_TAG "SamSonicUsb"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_libusbVersion(JNIEnv* env, jobject /* this */) {
    const libusb_version* v = libusb_get_version();
    char text[48];
    snprintf(text, sizeof(text), "%d.%d.%d.%d", v->major, v->minor, v->micro, v->nano);
    LOGI("libusb %s", text);
    return env->NewStringUTF(text);
}

namespace {
// One libusb context for the app's life. Device discovery is off: Android hands us the
// device as a file descriptor, and scanning /dev/bus/usb isn't allowed.
libusb_context* context() {
    static libusb_context* ctx = [] {
        libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
        libusb_context* c = nullptr;
        if (libusb_init(&c) != 0) return static_cast<libusb_context*>(nullptr);
        return c;
    }();
    return ctx;
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_samsonic_playback_usb_NativeUsb_probe(JNIEnv* env, jobject /* this */, jint fd) {
    char text[160];
    libusb_context* ctx = context();
    libusb_device_handle* handle = nullptr;
    int r = ctx ? libusb_wrap_sys_device(ctx, static_cast<intptr_t>(fd), &handle) : LIBUSB_ERROR_OTHER;
    if (r != 0) {
        snprintf(text, sizeof(text), "wrap failed: %s", libusb_error_name(r));
    } else {
        libusb_device_descriptor desc{};
        r = libusb_get_device_descriptor(libusb_get_device(handle), &desc);
        if (r != 0) {
            snprintf(text, sizeof(text), "descriptor failed: %s", libusb_error_name(r));
        } else {
            snprintf(text, sizeof(text), "%04x:%04x, %d configuration(s), USB %x.%02x",
                     desc.idVendor, desc.idProduct, desc.bNumConfigurations,
                     desc.bcdUSB >> 8, desc.bcdUSB & 0xff);
        }
        // The wrapped fd stays open: it belongs to the UsbDeviceConnection.
        libusb_close(handle);
    }
    LOGI("probe(%d): %s", fd, text);
    return env->NewStringUTF(text);
}
