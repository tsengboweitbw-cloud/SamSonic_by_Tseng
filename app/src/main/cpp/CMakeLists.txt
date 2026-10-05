cmake_minimum_required(VERSION 3.22.1)
project(samsonic_usb LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Opt-in memory checking for the driver and libusb: build with -PnativeSanitize=hwasan. HWASan
# turns an out-of-bounds or use-after-free access into a crash with a report in logcat.
option(SAMSONIC_HWASAN "Build the native code with HWAddressSanitizer" OFF)
if(SAMSONIC_HWASAN)
    add_compile_options(-fsanitize=hwaddress -fno-omit-frame-pointer -g)
    add_link_options(-fsanitize=hwaddress)
endif()

# libusb 1.0.29, vendored and built as a static library for Android's usbfs backend.
set(LIBUSB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb)
add_library(usb1.0 STATIC
    ${LIBUSB_DIR}/libusb/core.c
    ${LIBUSB_DIR}/libusb/descriptor.c
    ${LIBUSB_DIR}/libusb/hotplug.c
    ${LIBUSB_DIR}/libusb/io.c
    ${LIBUSB_DIR}/libusb/strerror.c
    ${LIBUSB_DIR}/libusb/sync.c
    ${LIBUSB_DIR}/libusb/os/events_posix.c
    ${LIBUSB_DIR}/libusb/os/linux_netlink.c
    ${LIBUSB_DIR}/libusb/os/linux_usbfs.c
    ${LIBUSB_DIR}/libusb/os/threads_posix.c)
# config.h sits beside the libusb folder; libusb.h is public to the driver.
target_include_directories(usb1.0
    PRIVATE ${LIBUSB_DIR} ${LIBUSB_DIR}/libusb
    PUBLIC ${LIBUSB_DIR}/libusb)
target_compile_options(usb1.0 PRIVATE -w)

add_library(samsonic_usb SHARED usb_jni.cpp usb_context.cpp uac.cpp iso_stream.cpp)
target_link_libraries(samsonic_usb usb1.0 log)
# Android 15+ devices use 16 KB pages.
target_link_options(samsonic_usb PRIVATE -Wl,-z,max-page-size=16384)
