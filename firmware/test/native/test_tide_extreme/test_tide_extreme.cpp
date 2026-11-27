// Host-side unit tests for splitting API extremes into past and future.
// Run from firmware/: pio test -e native

#include <unity.h>

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

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_recent_past_extreme_is_found);
    RUN_TEST(test_extreme_exactly_now_is_past);
    RUN_TEST(test_latest_past_wins_regardless_of_order);
    RUN_TEST(test_no_past_extreme_clears_last_past);
    RUN_TEST(test_future_is_capped);
    RUN_TEST(test_empty_input);
    return UNITY_END();
}
