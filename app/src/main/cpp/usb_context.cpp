#include "usb_context.h"

// Device discovery is off: Android hands us the device as a file descriptor, and scanning
// /dev/bus/usb isn't allowed.
libusb_context* sharedUsbContext() {
    static libusb_context* ctx = [] {
        libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
        libusb_context* c = nullptr;
        return libusb_init(&c) == 0 ? c : nullptr;
    }();
    return ctx;
}
