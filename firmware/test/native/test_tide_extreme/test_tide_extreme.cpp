// Host-side unit tests for splitting API extremes into past and future.
// Run from firmware/: pio test -e native

#include <unity.h>

#include <cmath>

#include "models/TideExtreme.h"

namespace {

TideExtreme ext(time_t ts, bool isHigh) {
    TideExtreme e;
    e.timestamp = ts;
    e.height = isHigh ? 2.0f : 0.1f;
    e.isHigh = isHigh;
    return e;
}

const int kMaxFuture = 20;

}  // namespace

void setUp() {}
void tearDown() {}

void test_recent_past_extreme_is_found() {
    // The bug: the past search shifted timestamps by the GMT+DST offset, so an
    // extreme 0-4h old (EDT) was in neither the past nor the future list.
    // Times are UTC epoch seconds, like time(nullptr) and the API.
    const time_t now = 100000;
    TideExtreme all[] = {
        ext(now - 8 * 3600, true),   // high 8h ago
        ext(now - 2 * 3600, false),  // low 2h ago: the real last past
        ext(now + 4 * 3600, true),   // high in 4h
    };
    TideExtreme lastPast;
    TideExtreme future[kMaxFuture];
    int n = splitTideExtremes(all, 3, now, lastPast, future, kMaxFuture);
    TEST_ASSERT_EQUAL_INT64(now - 2 * 3600, lastPast.timestamp);
    TEST_ASSERT_FALSE(lastPast.isHigh);
    TEST_ASSERT_EQUAL_INT(1, n);
    TEST_ASSERT_EQUAL_INT64(now + 4 * 3600, future[0].timestamp);
}

void test_extreme_exactly_now_is_past() {
    TideExtreme all[] = {ext(1000, true), ext(2000, false)};
    TideExtreme lastPast;
    TideExtreme future[kMaxFuture];
    int n = splitTideExtremes(all, 2, 1000, lastPast, future, kMaxFuture);
    TEST_ASSERT_EQUAL_INT64(1000, lastPast.timestamp);
    TEST_ASSERT_EQUAL_INT(1, n);
    TEST_ASSERT_EQUAL_INT64(2000, future[0].timestamp);
}

void test_latest_past_wins_regardless_of_order() {
    TideExtreme all[] = {ext(900, false), ext(500, true), ext(950, true), ext(1200, false)};
    TideExtreme lastPast;
    TideExtreme future[kMaxFuture];
    int n = splitTideExtremes(all, 4, 1000, lastPast, future, kMaxFuture);
    TEST_ASSERT_EQUAL_INT64(950, lastPast.timestamp);
    TEST_ASSERT_TRUE(lastPast.isHigh);
    TEST_ASSERT_EQUAL_INT(1, n);
}

void test_no_past_extreme_clears_last_past() {
    TideExtreme all[] = {ext(2000, true), ext(3000, false)};
    TideExtreme lastPast = ext(42, true);  // stale value from an earlier fetch
    TideExtreme future[kMaxFuture];
    int n = splitTideExtremes(all, 2, 1000, lastPast, future, kMaxFuture);
    TEST_ASSERT_FALSE(hasTimestamp(lastPast));
    TEST_ASSERT_EQUAL_INT(2, n);
}

void test_future_is_capped() {
    TideExtreme all[] = {ext(2000, true), ext(3000, false), ext(4000, true)};
    TideExtreme lastPast;
    TideExtreme future[2];
    int n = splitTideExtremes(all, 3, 1000, lastPast, future, 2);
    TEST_ASSERT_EQUAL_INT(2, n);
    TEST_ASSERT_EQUAL_INT64(3000, future[1].timestamp);
}

void test_empty_input() {
    TideExtreme lastPast = ext(42, true);
    TideExtreme future[kMaxFuture];
    TEST_ASSERT_EQUAL_INT(0, splitTideExtremes(nullptr, 0, 1000, lastPast, future, kMaxFuture));
    TEST_ASSERT_FALSE(hasTimestamp(lastPast));
}

void test_future_is_sorted_ascending() {
    TideExtreme all[] = {ext(4000, true), ext(2000, false), ext(3000, true)};
    TideExtreme lastPast;
    TideExtreme future[kMaxFuture];
    int n = splitTideExtremes(all, 3, 1000, lastPast, future, kMaxFuture);
    TEST_ASSERT_EQUAL_INT(3, n);
    TEST_ASSERT_EQUAL_INT64(2000, future[0].timestamp);
    TEST_ASSERT_FALSE(future[0].isHigh);
    TEST_ASSERT_EQUAL_INT64(3000, future[1].timestamp);
    TEST_ASSERT_EQUAL_INT64(4000, future[2].timestamp);
}

void test_cap_keeps_earliest_when_unsorted() {
    TideExtreme all[] = {ext(5000, true), ext(4000, false), ext(2000, true), ext(3000, false)};
    TideExtreme lastPast;
    TideExtreme future[2];
    int n = splitTideExtremes(all, 4, 1000, lastPast, future, 2);
    TEST_ASSERT_EQUAL_INT(2, n);
    TEST_ASSERT_EQUAL_INT64(2000, future[0].timestamp);
    TEST_ASSERT_EQUAL_INT64(3000, future[1].timestamp);
}

void test_duplicate_future_timestamps_are_dropped() {
    // Overlapping station-day records can repeat an extreme; a duplicate would
    // become a zero-length interval.
    TideExtreme all[] = {ext(2000, false), ext(2000, false), ext(3000, true)};
    TideExtreme lastPast;
    TideExtreme future[kMaxFuture];
    int n = splitTideExtremes(all, 3, 1000, lastPast, future, kMaxFuture);
    TEST_ASSERT_EQUAL_INT(2, n);
    TEST_ASSERT_EQUAL_INT64(2000, future[0].timestamp);
    TEST_ASSERT_EQUAL_INT64(3000, future[1].timestamp);
}

void test_apply_with_no_future_extremes_keeps_existing_data() {
    // A fetch whose extremes were all rejected (or all in the past) must not
    // wipe the good cached data.
    TideExtreme current = ext(900, true);
    TideExtreme stored[kMaxFuture] = {ext(1500, false), ext(2500, true)};
    int numStored = 2;
    TideExtreme parsedPastOnly[] = {ext(800, false)};
    TEST_ASSERT_FALSE(applyTideExtremes(parsedPastOnly, 1, 1000, current, stored, numStored, kMaxFuture));
    TEST_ASSERT_FALSE(applyTideExtremes(nullptr, 0, 1000, current, stored, numStored, kMaxFuture));
    TEST_ASSERT_EQUAL_INT(2, numStored);
    TEST_ASSERT_EQUAL_INT64(900, current.timestamp);
    TEST_ASSERT_EQUAL_INT64(1500, stored[0].timestamp);
}

void test_apply_with_future_extremes_replaces_data() {
    TideExtreme current = ext(900, true);
    TideExtreme stored[kMaxFuture] = {ext(1500, false), ext(2500, true)};
    int numStored = 2;
    TideExtreme parsed[] = {ext(950, false), ext(3000, true)};
    TEST_ASSERT_TRUE(applyTideExtremes(parsed, 2, 1000, current, stored, numStored, kMaxFuture));
    TEST_ASSERT_EQUAL_INT(1, numStored);
    TEST_ASSERT_EQUAL_INT64(950, current.timestamp);
    TEST_ASSERT_EQUAL_INT64(3000, stored[0].timestamp);
}

void test_parse_converts_ms_to_seconds_and_type() {
    TideExtreme e;
    TEST_ASSERT_TRUE(parseApiExtreme(1764259200000.0, 1.75, "\"HIGH\"", e));
    TEST_ASSERT_EQUAL_INT64(1764259200, e.timestamp);
    TEST_ASSERT_FLOAT_WITHIN(1e-6, 1.75f, e.height);
    TEST_ASSERT_TRUE(e.isHigh);

    TEST_ASSERT_TRUE(parseApiExtreme(1764281580000.0, -0.2, "\"LOW\"", e));
    TEST_ASSERT_EQUAL_INT64(1764281580, e.timestamp);
    TEST_ASSERT_FALSE(e.isHigh);
}

void test_parse_rejects_non_finite_or_missing() {
    TideExtreme e;
    TEST_ASSERT_FALSE(parseApiExtreme(NAN, 1.0, "\"HIGH\"", e));
    TEST_ASSERT_FALSE(parseApiExtreme(INFINITY, 1.0, "\"HIGH\"", e));
    TEST_ASSERT_FALSE(parseApiExtreme(1764259200000.0, NAN, "\"HIGH\"", e));
    TEST_ASSERT_FALSE(parseApiExtreme(0.0, 1.0, "\"HIGH\"", e));  // 0 means unknown
    TEST_ASSERT_FALSE(parseApiExtreme(1764259200000.0, 1.0, nullptr, e));
}

void test_parse_rejects_unknown_type() {
    // A null or odd type must not silently become LOW.
    TideExtreme e;
    TEST_ASSERT_FALSE(parseApiExtreme(1764259200000.0, 1.0, "null", e));
    TEST_ASSERT_FALSE(parseApiExtreme(1764259200000.0, 1.0, "\"\"", e));
    TEST_ASSERT_FALSE(parseApiExtreme(1764259200000.0, 1.0, "\"SLACK\"", e));
    TEST_ASSERT_FALSE(parseApiExtreme(1764259200000.0, 1.0, "HIGH", e));  // unquoted
}

void test_parse_rejects_out_of_range_timestamp() {
    TideExtreme e;
    TEST_ASSERT_FALSE(parseApiExtreme(-1000.0, 1.0, "\"HIGH\"", e));
    TEST_ASSERT_FALSE(parseApiExtreme(1e20, 1.0, "\"HIGH\"", e));
    // Past the 32-bit time_t limit (2038) is rejected even where time_t is 64-bit.
    TEST_ASSERT_FALSE(parseApiExtreme(2147483648000.0, 1.0, "\"HIGH\"", e));
    TEST_ASSERT_TRUE(parseApiExtreme(2147483647000.0, 1.0, "\"HIGH\"", e));
}

void test_clock_set_threshold() {
    TEST_ASSERT_FALSE(isClockSet(0));
    TEST_ASSERT_FALSE(isClockSet(86400));  // a day after boot with no NTP
    TEST_ASSERT_FALSE(isClockSet(kMinValidEpochSec - 1));
    TEST_ASSERT_TRUE(isClockSet(kMinValidEpochSec));
    TEST_ASSERT_TRUE(isClockSet(1764259200));  // 2025-11-27
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_recent_past_extreme_is_found);
    RUN_TEST(test_extreme_exactly_now_is_past);
    RUN_TEST(test_latest_past_wins_regardless_of_order);
    RUN_TEST(test_no_past_extreme_clears_last_past);
    RUN_TEST(test_future_is_capped);
    RUN_TEST(test_empty_input);
    RUN_TEST(test_future_is_sorted_ascending);
    RUN_TEST(test_cap_keeps_earliest_when_unsorted);
    RUN_TEST(test_duplicate_future_timestamps_are_dropped);
    RUN_TEST(test_apply_with_no_future_extremes_keeps_existing_data);
    RUN_TEST(test_apply_with_future_extremes_replaces_data);
    RUN_TEST(test_parse_converts_ms_to_seconds_and_type);
    RUN_TEST(test_parse_rejects_non_finite_or_missing);
    RUN_TEST(test_parse_rejects_unknown_type);
    RUN_TEST(test_parse_rejects_out_of_range_timestamp);
    RUN_TEST(test_clock_set_threshold);
    return UNITY_END();
}
