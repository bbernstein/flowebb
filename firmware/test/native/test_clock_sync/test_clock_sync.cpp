// Host-side unit tests for the clock re-sync decision in loop().
// Run from firmware/: pio test -e native

#include <unity.h>

#include "services/ClockSync.h"

namespace {
const time_t kSynced = 1764259200;  // 2025-11-27
const time_t kUnsynced = 42;        // seconds since boot, no NTP
const uint32_t kRetry = 60000;
}  // namespace

void setUp() {}
void tearDown() {}

void test_synced_clock_never_needs_sync() {
    ClockSyncState s;
    TEST_ASSERT_FALSE(shouldAttemptClockSync(s, kSynced, 0, kRetry));
    TEST_ASSERT_FALSE(shouldAttemptClockSync(s, kSynced, 999999, kRetry));
}

void test_unsynced_clock_syncs_at_once_when_never_tried() {
    // Boot without Wi-Fi: setup() never ran NTP, so loop() must try.
    ClockSyncState s;
    TEST_ASSERT_TRUE(shouldAttemptClockSync(s, kUnsynced, 5, kRetry));
}

void test_unsynced_clock_backs_off_between_attempts() {
    ClockSyncState s;
    recordClockSyncAttempt(s, 1000);
    TEST_ASSERT_FALSE(shouldAttemptClockSync(s, kUnsynced, 1000, kRetry));
    TEST_ASSERT_FALSE(shouldAttemptClockSync(s, kUnsynced, 1000 + kRetry - 1, kRetry));
    TEST_ASSERT_TRUE(shouldAttemptClockSync(s, kUnsynced, 1000 + kRetry, kRetry));
}

void test_backoff_survives_millis_wraparound() {
    ClockSyncState s;
    recordClockSyncAttempt(s, 0xFFFFFFF0UL);
    TEST_ASSERT_FALSE(shouldAttemptClockSync(s, kUnsynced, 0x10UL, kRetry));
    TEST_ASSERT_TRUE(shouldAttemptClockSync(s, kUnsynced, (uint32_t)(0xFFFFFFF0UL + kRetry), kRetry));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_synced_clock_never_needs_sync);
    RUN_TEST(test_unsynced_clock_syncs_at_once_when_never_tried);
    RUN_TEST(test_unsynced_clock_backs_off_between_attempts);
    RUN_TEST(test_backoff_survives_millis_wraparound);
    return UNITY_END();
}
