#include "uac.h"

#include <cstdio>

namespace {
constexpr uint8_t CS_INTERFACE = 0x24;
constexpr uint8_t USB_CLASS_AUDIO = 1;
constexpr uint8_t SUBCLASS_CONTROL = 1;
constexpr uint8_t SUBCLASS_STREAMING = 2;

constexpr uint8_t AC_HEADER = 0x01;
constexpr uint8_t AC_INPUT_TERMINAL = 0x02;
constexpr uint8_t AC_CLOCK_SOURCE = 0x0A;
constexpr uint8_t AC_CLOCK_SELECTOR = 0x0B;
constexpr uint8_t AC_CLOCK_MULTIPLIER = 0x0C;
constexpr uint8_t AS_GENERAL = 0x01;
constexpr uint8_t AS_FORMAT_TYPE = 0x02;

constexpr uint8_t REQ_CUR = 0x01;  // SET_CUR or GET_CUR, by direction
constexpr uint8_t REQ_RANGE = 0x02;
constexpr uint16_t CS_SAM_FREQ_CONTROL = 0x0100;

uint32_t le16(const uint8_t* p) { return p[0] | (p[1] << 8); }
uint32_t le24(const uint8_t* p) { return p[0] | (p[1] << 8) | (p[2] << 16); }
uint32_t le32(const uint8_t* p) { return p[0] | (p[1] << 8) | (p[2] << 16) | (static_cast<uint32_t>(p[3]) << 24); }

// Calls [f](descriptor, length) for each class-specific interface descriptor in [extra].
template <typename F>
void forEachCsInterface(const unsigned char* extra, int length, F f) {
    for (int i = 0; i + 2 < length && extra[i] >= 3; i += extra[i]) {
        if (i + extra[i] > length) break;
        if (extra[i + 1] == CS_INTERFACE) f(extra + i, static_cast<int>(extra[i]));
    }
}

void parseControl(const libusb_interface_descriptor& alt, UacDevice& out) {
    forEachCsInterface(alt.extra, alt.extra_length, [&](const uint8_t* d, int len) {
        switch (d[2]) {
            case AC_INPUT_TERMINAL:
                if (out.version >= 2 && len >= 8) out.terminalClock[d[3]] = d[7];
                break;
            case AC_CLOCK_SOURCE:
                if (len >= 4) out.clocks[d[3]] = {d[2], {}};
                break;
            case AC_CLOCK_SELECTOR:
                if (len >= 6) {
                    UacDevice::Clock c{d[2], {}};
                    for (int p = 0; p < d[4] && 5 + p < len; p++) c.inputs.push_back(d[5 + p]);
                    out.clocks[d[3]] = c;
                }
                break;
            case AC_CLOCK_MULTIPLIER:
                if (len >= 5) out.clocks[d[3]] = {d[2], {d[4]}};
                break;
            default:
                break;
        }
    });
}

void parseFormat(const libusb_interface_descriptor& alt, UacAlt& out, int version) {
    forEachCsInterface(alt.extra, alt.extra_length, [&](const uint8_t* d, int len) {
        if (d[2] == AS_GENERAL) {
            out.terminalLink = d[3];
            if (version >= 2 && len >= 11) {
                uint32_t formats = le32(d + 6);
                out.pcm = (formats & 1) != 0;
                out.rawData = (formats & 0x80000000u) != 0;
                out.channels = d[10];
            } else if (version == 1 && len >= 7) {
                out.pcm = le16(d + 5) == 1;  // wFormatTag PCM
            }
        } else if (d[2] == AS_FORMAT_TYPE && len >= 4 && d[3] == 1) {  // Type I
            if (version >= 2 && len >= 6) {
                out.subslotBytes = d[4];
                out.bitResolution = d[5];
            } else if (version == 1 && len >= 8) {
                out.channels = d[4];
                out.subslotBytes = d[5];
                out.bitResolution = d[6];
                int types = d[7];
                if (types == 0 && len >= 14) {
                    out.rates.push_back({le24(d + 8), le24(d + 11), 0});
                } else {
                    for (int r = 0; r < types && 8 + 3 * r + 3 <= len; r++) {
                        uint32_t hz = le24(d + 8 + 3 * r);
                        out.rates.push_back({hz, hz, 0});
                    }
                }
            }
        }
    });
}

uint8_t resolveClockSource(const UacDevice& dev, uint8_t id) {
    for (int hops = 0; hops < 8; hops++) {
        auto it = dev.clocks.find(id);
        if (it == dev.clocks.end()) return 0;
        if (it->second.type == AC_CLOCK_SOURCE) return id;
        if (it->second.inputs.empty()) return 0;
        id = it->second.inputs.front();
    }
    return 0;
}

bool inRange(const UacRange& r, uint32_t rate) {
    if (rate < r.min || rate > r.max) return false;
    return r.res == 0 || (rate - r.min) % r.res == 0;
}

uint16_t clockIndex(const UacDevice& dev) { return static_cast<uint16_t>((dev.clockSource << 8) | dev.controlInterface); }
}  // namespace

bool parseUac(libusb_device* dev, const libusb_config_descriptor* cfg, UacDevice& out) {
    out = UacDevice{};
    // The header names the version, which the other descriptors have to be read by.
    for (int i = 0; i < cfg->bNumInterfaces; i++) {
        const libusb_interface_descriptor& alt = cfg->interface[i].altsetting[0];
        if (alt.bInterfaceClass == USB_CLASS_AUDIO && alt.bInterfaceSubClass == SUBCLASS_CONTROL) {
            out.controlInterface = alt.bInterfaceNumber;
            forEachCsInterface(alt.extra, alt.extra_length, [&](const uint8_t* d, int len) {
                if (d[2] == AC_HEADER && len >= 5) out.version = le16(d + 3) >> 8;
            });
            parseControl(alt, out);
        }
    }
    if (out.controlInterface < 0 || out.version < 1) return false;

    for (int i = 0; i < cfg->bNumInterfaces; i++) {
        for (int a = 0; a < cfg->interface[i].num_altsetting; a++) {
            const libusb_interface_descriptor& alt = cfg->interface[i].altsetting[a];
            if (alt.bInterfaceClass != USB_CLASS_AUDIO || alt.bInterfaceSubClass != SUBCLASS_STREAMING) continue;
            UacAlt u;
            u.interfaceNumber = alt.bInterfaceNumber;
            u.altSetting = alt.bAlternateSetting;
            for (int e = 0; e < alt.bNumEndpoints; e++) {
                const libusb_endpoint_descriptor& ep = alt.endpoint[e];
                if ((ep.bmAttributes & 3) != LIBUSB_TRANSFER_TYPE_ISOCHRONOUS) continue;
                bool in = (ep.bEndpointAddress & 0x80) != 0;
                int usage = (ep.bmAttributes >> 4) & 3;  // 0 data, 1 feedback, 2 implicit feedback
                if (!in && usage == 0 && u.dataEp == 0) {
                    u.dataEp = ep.bEndpointAddress;
                    u.dataMaxPacket = libusb_get_max_iso_packet_size(dev, ep.bEndpointAddress);
                    u.dataInterval = ep.bInterval;
                    u.syncType = (ep.bmAttributes >> 2) & 3;
                } else if (in && usage != 2 && u.feedbackEp == 0) {
                    u.feedbackEp = ep.bEndpointAddress;
                    u.feedbackMaxPacket = libusb_get_max_iso_packet_size(dev, ep.bEndpointAddress);
                }
            }
            if (u.dataEp == 0) continue;  // alt 0 (no audio) or a recording alt
            parseFormat(alt, u, out.version);
            out.alts.push_back(u);
        }
    }
    return !out.alts.empty();
}

bool queryClockRates(libusb_device_handle* handle, UacDevice& dev, const UacAlt& alt) {
    dev.clockRates.clear();
    if (dev.version < 2) return true;
    auto link = dev.terminalClock.find(alt.terminalLink);
    if (link == dev.terminalClock.end()) return false;
    dev.clockSource = resolveClockSource(dev, link->second);
    if (dev.clockSource == 0) return false;

    uint8_t buf[256];
    int n = libusb_control_transfer(handle, 0xA1, REQ_RANGE, CS_SAM_FREQ_CONTROL, clockIndex(dev), buf,
                                    sizeof(buf), 1000);
    if (n < 2) return false;
    int count = le16(buf);
    for (int r = 0; r < count && 2 + 12 * (r + 1) <= n; r++) {
        const uint8_t* p = buf + 2 + 12 * r;
        dev.clockRates.push_back({le32(p), le32(p + 4), le32(p + 8)});
    }
    return !dev.clockRates.empty();
}

bool supportsRate(const UacDevice& dev, const UacAlt& alt, uint32_t rate) {
    const std::vector<UacRange>& ranges = dev.version >= 2 ? dev.clockRates : alt.rates;
    for (const UacRange& r : ranges) {
        if (inRange(r, rate)) return true;
    }
    return false;
}

std::string setSampleRate(libusb_device_handle* handle, const UacDevice& dev, const UacAlt& alt, uint32_t rate) {
    uint8_t data[4] = {static_cast<uint8_t>(rate), static_cast<uint8_t>(rate >> 8),
                       static_cast<uint8_t>(rate >> 16), static_cast<uint8_t>(rate >> 24)};
    if (dev.version >= 2) {
        int n = libusb_control_transfer(handle, 0x21, REQ_CUR, CS_SAM_FREQ_CONTROL, clockIndex(dev), data, 4, 1000);
        if (n != 4) return std::string("setting the clock failed: ") + libusb_error_name(n);
        uint8_t got[4] = {};
        n = libusb_control_transfer(handle, 0xA1, REQ_CUR, CS_SAM_FREQ_CONTROL, clockIndex(dev), got, 4, 1000);
        if (n == 4 && le32(got) != rate) return "the DAC's clock stayed at " + std::to_string(le32(got)) + " Hz";
    } else {
        // UAC1 sets the rate on the streaming endpoint, as three bytes.
        int n = libusb_control_transfer(handle, 0x22, REQ_CUR, CS_SAM_FREQ_CONTROL, alt.dataEp, data, 3, 1000);
        if (n != 3) return std::string("setting the endpoint rate failed: ") + libusb_error_name(n);
    }
    return "";
}

std::string describeUac(const UacDevice& dev) {
    std::string s;
    char line[256];
    snprintf(line, sizeof(line), "UAC%d, control interface %d, clock %u, %zu streaming alt(s)\n", dev.version,
             dev.controlInterface, dev.clockSource, dev.alts.size());
    s += line;
    for (const UacAlt& a : dev.alts) {
        snprintf(line, sizeof(line),
                 "  intf %d alt %d: %s %dch %d-bit in %d byte(s), ep 0x%02x max %d interval %d sync %d, feedback ep 0x%02x\n",
                 a.interfaceNumber, a.altSetting, a.pcm ? "PCM" : a.rawData ? "raw data" : "non-PCM", a.channels, a.bitResolution,
                 a.subslotBytes, a.dataEp, a.dataMaxPacket, a.dataInterval, a.syncType, a.feedbackEp);
        s += line;
        for (const UacRange& r : a.rates) {
            snprintf(line, sizeof(line), "    rate %u-%u\n", r.min, r.max);
            s += line;
        }
    }
    for (const UacRange& r : dev.clockRates) {
        snprintf(line, sizeof(line), "  clock rate %u-%u step %u\n", r.min, r.max, r.res);
        s += line;
    }
    return s;
}
