#pragma once

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>

/**
 * A single-producer, single-consumer byte ring: the player's thread writes, the USB thread
 * reads, and neither waits for the other. The capacity is rounded up to a power of two.
 */
class ByteRing {
public:
    void reset(size_t minCapacity) {
        size_t cap = 4096;
        while (cap < minCapacity) cap <<= 1;
        buf_.assign(cap, 0);
        mask_ = cap - 1;
        head_.store(0, std::memory_order_relaxed);
        tail_.store(0, std::memory_order_relaxed);
    }

    size_t capacity() const { return buf_.size(); }
    size_t readable() const { return head_.load(std::memory_order_acquire) - tail_.load(std::memory_order_acquire); }
    size_t writable() const { return capacity() - readable(); }

    /** Producer: copies in as much of [data] as fits; returns the bytes taken. */
    size_t write(const uint8_t* data, size_t bytes) {
        size_t n = std::min(bytes, writable());
        size_t head = head_.load(std::memory_order_relaxed);
        copyIn(head, data, n);
        head_.store(head + n, std::memory_order_release);
        return n;
    }

    /** Consumer: copies out up to [bytes]; returns the bytes given. */
    size_t read(uint8_t* out, size_t bytes) {
        size_t n = std::min(bytes, readable());
        size_t tail = tail_.load(std::memory_order_relaxed);
        copyOut(tail, out, n);
        tail_.store(tail + n, std::memory_order_release);
        return n;
    }

    /** Consumer: drops everything written so far. */
    void discard() { tail_.store(head_.load(std::memory_order_acquire), std::memory_order_release); }

private:
    void copyIn(size_t pos, const uint8_t* data, size_t n) {
        size_t start = pos & mask_;
        size_t first = std::min(n, capacity() - start);
        std::memcpy(buf_.data() + start, data, first);
        std::memcpy(buf_.data(), data + first, n - first);
    }

    void copyOut(size_t pos, uint8_t* out, size_t n) const {
        size_t start = pos & mask_;
        size_t first = std::min(n, capacity() - start);
        std::memcpy(out, buf_.data() + start, first);
        std::memcpy(out + first, buf_.data(), n - first);
    }

    std::vector<uint8_t> buf_;
    size_t mask_ = 0;
    std::atomic<size_t> head_{0};
    std::atomic<size_t> tail_{0};
};
