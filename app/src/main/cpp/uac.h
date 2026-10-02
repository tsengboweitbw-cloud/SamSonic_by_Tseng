#pragma once

#include <libusb.h>

#include <cstdint>
#include <map>
#include <string>
#include <vector>

/** A range of sample rates the DAC takes; res == 0 or min == max means "just these". */
struct UacRange {
    uint32_t min = 0;
    uint32_t max = 0;
    uint32_t res = 0;
};

/** One alternate setting of a USB audio streaming interface that plays audio (it has an endpoint). */
struct UacAlt {
    int interfaceNumber = 0;
    int altSetting = 0;
    int channels = 0;
    int subslotBytes = 0;
    int bitResolution = 0;
    bool pcm = false;
    uint8_t terminalLink = 0;
    uint8_t dataEp = 0;
    int dataMaxPacket = 0;
    int dataInterval = 1;  // bInterval: the endpoint is served every 2^(n-1) (micro)frames
    int syncType = 0;      // 0 none, 1 asynchronous, 2 adaptive, 3 synchronous
    uint8_t feedbackEp = 0;
    int feedbackMaxPacket = 0;
    std::vector<UacRange> rates;  // UAC1 only: from the format descriptor
};

/** What the active configuration says about a USB audio class 1 or 2 DAC. */
struct UacDevice {
    struct Clock {
        uint8_t type = 0;  // descriptor subtype: 0x0A source, 0x0B selector, 0x0C multiplier
        std::vector<uint8_t> inputs;
    };

    int version = 0;  // 1 or 2
    int controlInterface = -1;
    std::vector<UacAlt> alts;
    std::map<uint8_t, uint8_t> terminalClock;  // UAC2: input terminal id -> clock entity id
    std::map<uint8_t, Clock> clocks;
    std::vector<UacRange> clockRates;  // UAC2: asked from the clock by queryClockRates
    uint8_t clockSource = 0;
};

/** Reads [cfg] into [out]; false when it has no audio control interface or nothing to play on. */
bool parseUac(libusb_device* dev, const libusb_config_descriptor* cfg, UacDevice& out);

/** UAC2: finds the clock behind [alt] and asks it which rates it takes. */
bool queryClockRates(libusb_device_handle* handle, UacDevice& dev, const UacAlt& alt);

bool supportsRate(const UacDevice& dev, const UacAlt& alt, uint32_t rate);

/** Tells the DAC to run at [rate]; an empty string on success, otherwise what went wrong. */
std::string setSampleRate(libusb_device_handle* handle, const UacDevice& dev, const UacAlt& alt, uint32_t rate);

/** The device's audio layout, one line per alternate setting, for the log. */
std::string describeUac(const UacDevice& dev);
