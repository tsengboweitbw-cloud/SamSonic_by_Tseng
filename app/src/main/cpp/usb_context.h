#pragma once

#include <libusb.h>

/** The one libusb context for the app's life, or null if libusb couldn't start. */
libusb_context* sharedUsbContext();
