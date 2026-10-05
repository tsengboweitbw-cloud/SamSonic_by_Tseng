#include "byte_ring.h"

#include <cstdio>
#include <functional>
#include <thread>
#include <vector>

// A minimal runner, so the tests need no download and build anywhere with a C++17 compiler.
static int failures = 0;
#define CHECK(cond)                                                          \
    do {                                                                     \
        if (!(cond)) {                                                       \
            std::printf("  FAILED %s:%d: %s\n", __FILE__, __LINE__, #cond);  \
            failures++;                                                      \
        }                                                                    \
    } while (0)

static uint8_t byteAt(size_t i) { return static_cast<uint8_t>(i * 31 + 7); }

static std::vector<uint8_t> sequence(size_t from, size_t count) {
    std::vector<uint8_t> v(count);
    for (size_t i = 0; i < count; i++) v[i] = byteAt(from + i);
    return v;
}

static void capacityRoundsUpToPowerOfTwo() {
    ByteRing r;
    r.reset(1);
    CHECK(r.capacity() == 4096);
    r.reset(4096);
    CHECK(r.capacity() == 4096);
    r.reset(4097);
    CHECK(r.capacity() == 8192);
    r.reset(100000);
    CHECK(r.capacity() == 131072);
}

static void startsEmpty() {
    ByteRing r;
    r.reset(4096);
    CHECK(r.readable() == 0);
    CHECK(r.writable() == r.capacity());
    uint8_t out[8];
    CHECK(r.read(out, sizeof out) == 0);
}

static void roundTrip() {
    ByteRing r;
    r.reset(4096);
    auto in = sequence(0, 1000);
    CHECK(r.write(in.data(), in.size()) == in.size());
    CHECK(r.readable() == 1000);
    CHECK(r.writable() == r.capacity() - 1000);
    std::vector<uint8_t> out(1000);
    CHECK(r.read(out.data(), out.size()) == 1000);
    CHECK(out == in);
    CHECK(r.readable() == 0);
}

static void writeStopsWhenFull() {
    ByteRing r;
    r.reset(4096);
    const size_t cap = r.capacity();
    auto in = sequence(0, cap + 100);
    CHECK(r.write(in.data(), in.size()) == cap);
    CHECK(r.writable() == 0);
    CHECK(r.write(in.data(), 1) == 0);
    std::vector<uint8_t> out(cap);
    CHECK(r.read(out.data(), out.size()) == cap);
    CHECK(std::vector<uint8_t>(in.begin(), in.begin() + cap) == out);
}

static void readGivesWhatThereIs() {
    ByteRing r;
    r.reset(4096);
    auto in = sequence(0, 10);
    r.write(in.data(), in.size());
    std::vector<uint8_t> out(50, 0xEE);
    CHECK(r.read(out.data(), out.size()) == 10);
    CHECK(std::vector<uint8_t>(out.begin(), out.begin() + 10) == in);
    CHECK(out[10] == 0xEE);  // nothing is written past what was read
}

static void zeroLengthIsHarmless() {
    ByteRing r;
    r.reset(4096);
    uint8_t b = 1;
    CHECK(r.write(&b, 0) == 0);
    CHECK(r.read(&b, 0) == 0);
    CHECK(b == 1);
    CHECK(r.readable() == 0);
}

// The positions go round the end of the buffer, with sizes that straddle it.
static void wrapsAround() {
    ByteRing r;
    r.reset(4096);
    const size_t cap = r.capacity();
    size_t written = 0, consumed = 0;
    const size_t chunks[] = {1000, 3000, 4095, 4096, 1, 2500, 777, 4096, 3333};
    for (size_t chunk : chunks) {
        auto in = sequence(written, chunk);
        size_t put = r.write(in.data(), in.size());
        CHECK(put == chunk);
        written += put;
        std::vector<uint8_t> out(chunk);
        CHECK(r.read(out.data(), out.size()) == chunk);
        CHECK(out == sequence(consumed, chunk));
        consumed += chunk;
        CHECK(r.readable() == 0);
    }
    CHECK(written > cap * 4);  // the positions went past the end several times
}

static void partialReadsKeepOrder() {
    ByteRing r;
    r.reset(4096);
    size_t written = 0, consumed = 0;
    // Write 100 and read 70 at a time, so a few hundred bytes build up and the ring fills.
    for (int i = 0; i < 500; i++) {
        auto in = sequence(written, 100);
        written += r.write(in.data(), in.size());
        std::vector<uint8_t> out(70);
        size_t got = r.read(out.data(), out.size());
        CHECK(std::vector<uint8_t>(out.begin(), out.begin() + got) == sequence(consumed, got));
        consumed += got;
        CHECK(r.readable() == written - consumed);
    }
}

static void discardDropsEverythingAndKeepsWorking() {
    ByteRing r;
    r.reset(4096);
    auto in = sequence(0, 3000);
    r.write(in.data(), in.size());
    r.discard();
    CHECK(r.readable() == 0);
    CHECK(r.writable() == r.capacity());
    auto next = sequence(500, 200);
    r.write(next.data(), next.size());
    std::vector<uint8_t> out(200);
    CHECK(r.read(out.data(), out.size()) == 200);
    CHECK(out == next);
}

static void resetEmptiesAndResizes() {
    ByteRing r;
    r.reset(4096);
    auto in = sequence(0, 100);
    r.write(in.data(), in.size());
    r.reset(20000);
    CHECK(r.capacity() == 32768);
    CHECK(r.readable() == 0);
}

// The real use: one thread writes, another reads, and neither waits for the other. Every
// byte must come out once, in order. Run with -DSAMSONIC_SANITIZE=thread to check the
// memory ordering too.
static void oneWriterOneReader() {
    ByteRing r;
    r.reset(4096);
    const size_t total = 4u * 1024 * 1024;
    std::thread writer([&] {
        size_t sent = 0;
        size_t chunk = 1;
        while (sent < total) {
            size_t n = std::min(chunk, total - sent);
            auto in = sequence(sent, n);
            size_t put = r.write(in.data(), in.size());
            sent += put;
            if (put == 0) std::this_thread::yield();
            chunk = chunk % 1500 + 1 + (sent % 7);  // sizes of every kind, past the end often
        }
    });
    size_t got = 0;
    bool inOrder = true;
    std::vector<uint8_t> out(1024);
    while (got < total) {
        size_t n = r.read(out.data(), 1 + got % 1023);
        for (size_t i = 0; i < n; i++) {
            if (out[i] != byteAt(got + i)) inOrder = false;
        }
        got += n;
        if (n == 0) std::this_thread::yield();
    }
    writer.join();
    CHECK(inOrder);
    CHECK(got == total);
    CHECK(r.readable() == 0);
}

int main() {
    struct Case {
        const char* name;
        std::function<void()> run;
    };
    const Case cases[] = {
        {"capacityRoundsUpToPowerOfTwo", capacityRoundsUpToPowerOfTwo},
        {"startsEmpty", startsEmpty},
        {"roundTrip", roundTrip},
        {"writeStopsWhenFull", writeStopsWhenFull},
        {"readGivesWhatThereIs", readGivesWhatThereIs},
        {"zeroLengthIsHarmless", zeroLengthIsHarmless},
        {"wrapsAround", wrapsAround},
        {"partialReadsKeepOrder", partialReadsKeepOrder},
        {"discardDropsEverythingAndKeepsWorking", discardDropsEverythingAndKeepsWorking},
        {"resetEmptiesAndResizes", resetEmptiesAndResizes},
        {"oneWriterOneReader", oneWriterOneReader},
    };
    for (const Case& c : cases) {
        int before = failures;
        c.run();
        std::printf("%s %s\n", failures == before ? "ok     " : "FAILED ", c.name);
    }
    std::printf("%d failure(s)\n", failures);
    return failures == 0 ? 0 : 1;
}
