#pragma once

#include <libusb.h>

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <string>
#include <thread>
#include <vector>

#include "uac.h"

/**
 * Plays audio to a DAC's isochronous OUT endpoint. A worker thread keeps several transfers in
 * flight and refills each one from [Pull] as it completes. Asynchronous DACs set the pace:
 * their feedback endpoint says how many samples they want per (micro)frame.
 */
class IsoStream {
public:
    /** Writes [frames] frames, in the alt setting's sample layout, to [dst]; silence when there's nothing to play. */
    using Pull = std::function<void(uint8_t* dst, size_t frames)>;

    ~IsoStream() { stop(); }

    /** Selects [alt], sets [rate] and starts streaming. An empty string on success, otherwise what went wrong. */
    std::string start(libusb_context* ctx, libusb_device_handle* handle, const UacDevice& dev, const UacAlt& alt,
                      uint32_t rate, Pull pull);

    /** Stops streaming and gives the interface back at alt 0. Safe to call when not started. */
    void stop();

private:
    static void LIBUSB_CALL onData(libusb_transfer* t);
    static void LIBUSB_CALL onFeedback(libusb_transfer* t);
    void fill(libusb_transfer* t);
    void takeFeedback(libusb_transfer* t);
    void run();

    libusb_context* ctx_ = nullptr;
    libusb_device_handle* handle_ = nullptr;
    UacAlt alt_;
    int controlInterface_ = -1;
    Pull pull_;
    size_t frameBytes_ = 0;
    size_t maxFramesPerPacket_ = 0;
    int packetsPerUrb_ = 0;

    // Samples per service interval in Q16.16, and the fraction carried to the next packet.
    // Only the worker thread touches these once streaming has started.
    uint64_t nominalQ16_ = 0;
    uint64_t rateQ16_ = 0;
    uint64_t phase_ = 0;
    int feedbackShift_ = 0;
    bool feedbackShiftKnown_ = false;
    int errorLogs_ = 0;
    // For the log when streaming stops.
    uint64_t transfersDone_ = 0;
    uint64_t packetsBad_ = 0;
    uint64_t bytesSent_ = 0;
    uint64_t feedbackCount_ = 0;

    std::vector<libusb_transfer*> data_;
    std::vector<std::vector<uint8_t>> buffers_;
    libusb_transfer* feedback_ = nullptr;
    std::vector<uint8_t> feedbackBuffer_;
    int inflight_ = 0;

    std::atomic<bool> stopping_{false};
    std::thread worker_;
    bool started_ = false;
};
