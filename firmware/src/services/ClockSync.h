#pragma once
// Hardware-independent clock re-sync decision for loop(). No Arduino.h, so
// host unit tests in test/native/ can include it.
#include <cstdint>

#include "../models/TideExtreme.h"

// How often loop() retries NTP while the clock is unset (Wi-Fi was down at
// boot). Each attempt can block for up to WIFI_TIMEOUT.
const uint32_t CLOCK_SYNC_RETRY_MS = 60000;

struct ClockSyncState {
    bool attempted = false;
    uint32_t lastAttemptMs = 0;  // millis() is 32-bit on the ESP32
};

inline void recordClockSyncAttempt(ClockSyncState& s, uint32_t nowMs) {
    s.attempted = true;
    s.lastAttemptMs = nowMs;
}

// True when the clock is unset and either no sync was tried yet or the last
// try was at least `retryMs` ago. Unsigned subtraction handles millis() wrap.
inline bool shouldAttemptClockSync(const ClockSyncState& s, time_t now,
                                   uint32_t nowMs, uint32_t retryMs) {
    if (isClockSet(now)) return false;
    return !s.attempted || (uint32_t)(nowMs - s.lastAttemptMs) >= retryMs;
}
