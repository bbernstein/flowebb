// Host-side unit tests for the LED's tide interval selection (issue #2).
// Run from firmware/: pio test -e native

#include <unity.h>

#include "display/TideInterval.h"

namespace {

TideExtreme ext(time_t ts, bool isHigh) {
    TideExtreme e;
    e.timestamp = ts;
    e.height = isHigh ? 2.0f : 0.1f;
    e.isHigh = isHigh;
    return e;
}

const TideExtreme kLastPast = ext(1000, false);

}  // namespace

void setUp() {}
void tearDown() {}

void test_no_extremes_returns_false() {
    TideInterval out;
    TEST_ASSERT_FALSE(selectTideInterval(kLastPast, nullptr, 0, 1500, out));
}

void test_single_extreme_before_it_uses_last_past_as_current() {
    // The bug: numExtremes == 1 used to read extremes[-1].
    TideExtreme extremes[] = {ext(2000, true)};
    TideInterval out;
    TEST_ASSERT_TRUE(selectTideInterval(kLastPast, extremes, 1, 1500, out));
    TEST_ASSERT_EQUAL_INT64(1000, out.current.timestamp);
    TEST_ASSERT_EQUAL_INT64(2000, out.next.timestamp);
    TEST_ASSERT_TRUE(out.next.isHigh);
}

void test_single_extreme_at_its_timestamp_is_inclusive() {
    TideExtreme extremes[] = {ext(2000, true)};
    TideInterval out;
    TEST_ASSERT_TRUE(selectTideInterval(kLastPast, extremes, 1, 2000, out));
    TEST_ASSERT_EQUAL_INT64(1000, out.current.timestamp);
    TEST_ASSERT_EQUAL_INT64(2000, out.next.timestamp);
}

void test_single_extreme_after_it_returns_false() {
    TideExtreme extremes[] = {ext(2000, true)};
    TideInterval out;
    TEST_ASSERT_FALSE(selectTideInterval(kLastPast, extremes, 1, 2001, out));
}

void test_before_first_extreme_uses_last_past() {
    TideExtreme extremes[] = {ext(2000, true), ext(3000, false), ext(4000, true)};
    TideInterval out;
    TEST_ASSERT_TRUE(selectTideInterval(kLastPast, extremes, 3, 1200, out));
    TEST_ASSERT_EQUAL_INT64(1000, out.current.timestamp);
    TEST_ASSERT_EQUAL_INT64(2000, out.next.timestamp);
}

void test_middle_interval() {
    TideExtreme extremes[] = {ext(2000, true), ext(3000, false), ext(4000, true)};
    TideInterval out;
    TEST_ASSERT_TRUE(selectTideInterval(kLastPast, extremes, 3, 2500, out));
    TEST_ASSERT_EQUAL_INT64(2000, out.current.timestamp);
    TEST_ASSERT_EQUAL_INT64(3000, out.next.timestamp);
    TEST_ASSERT_TRUE(out.current.isHigh);
    TEST_ASSERT_FALSE(out.next.isHigh);
}

void test_exactly_on_middle_extreme_ends_that_interval() {
    TideExtreme extremes[] = {ext(2000, true), ext(3000, false), ext(4000, true)};
    TideInterval out;
    TEST_ASSERT_TRUE(selectTideInterval(kLastPast, extremes, 3, 3000, out));
    TEST_ASSERT_EQUAL_INT64(2000, out.current.timestamp);
    TEST_ASSERT_EQUAL_INT64(3000, out.next.timestamp);
}

void test_final_interval_keeps_updating() {
    // The bug: the old check stopped at the penultimate extreme, so the LED
    // froze for the whole final interval.
    TideExtreme extremes[] = {ext(2000, true), ext(3000, false), ext(4000, true)};
    TideInterval out;
    TEST_ASSERT_TRUE(selectTideInterval(kLastPast, extremes, 3, 3500, out));
    TEST_ASSERT_EQUAL_INT64(3000, out.current.timestamp);
    TEST_ASSERT_EQUAL_INT64(4000, out.next.timestamp);
}

void test_at_last_extreme_is_inclusive_and_in_bounds() {
    TideExtreme extremes[] = {ext(2000, true), ext(3000, false), ext(4000, true)};
    TideInterval out;
    TEST_ASSERT_TRUE(selectTideInterval(kLastPast, extremes, 3, 4000, out));
    TEST_ASSERT_EQUAL_INT64(3000, out.current.timestamp);
    TEST_ASSERT_EQUAL_INT64(4000, out.next.timestamp);
}

void test_past_last_extreme_returns_false() {
    TideExtreme extremes[] = {ext(2000, true), ext(3000, false), ext(4000, true)};
    TideInterval out;
    TEST_ASSERT_FALSE(selectTideInterval(kLastPast, extremes, 3, 4001, out));
}

void test_progress_start_middle_end() {
    TideExtreme a = ext(1000, false);
    TideExtreme b = ext(2000, true);
    TEST_ASSERT_FLOAT_WITHIN(1e-6, 0.0f, tideProgress(a, b, 1000));
    TEST_ASSERT_FLOAT_WITHIN(1e-6, 0.5f, tideProgress(a, b, 1500));
    TEST_ASSERT_FLOAT_WITHIN(1e-6, 1.0f, tideProgress(a, b, 2000));
}

void test_progress_is_clamped() {
    TideExtreme a = ext(1000, false);
    TideExtreme b = ext(2000, true);
    TEST_ASSERT_FLOAT_WITHIN(1e-6, 0.0f, tideProgress(a, b, 500));
    TEST_ASSERT_FLOAT_WITHIN(1e-6, 1.0f, tideProgress(a, b, 2500));
}

void test_progress_zero_length_interval_does_not_divide_by_zero() {
    TideExtreme a = ext(1000, false);
    TEST_ASSERT_FLOAT_WITHIN(1e-6, 1.0f, tideProgress(a, a, 1000));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_no_extremes_returns_false);
    RUN_TEST(test_single_extreme_before_it_uses_last_past_as_current);
    RUN_TEST(test_single_extreme_at_its_timestamp_is_inclusive);
    RUN_TEST(test_single_extreme_after_it_returns_false);
    RUN_TEST(test_before_first_extreme_uses_last_past);
    RUN_TEST(test_middle_interval);
    RUN_TEST(test_exactly_on_middle_extreme_ends_that_interval);
    RUN_TEST(test_final_interval_keeps_updating);
    RUN_TEST(test_at_last_extreme_is_inclusive_and_in_bounds);
    RUN_TEST(test_past_last_extreme_returns_false);
    RUN_TEST(test_progress_start_middle_end);
    RUN_TEST(test_progress_is_clamped);
    RUN_TEST(test_progress_zero_length_interval_does_not_divide_by_zero);
    return UNITY_END();
}
