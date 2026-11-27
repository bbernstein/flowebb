#pragma once
// Hardware-independent tide interval math for the LED (issue #2).
// No Arduino.h here: host unit tests in test/native/ compile this file.
#include "../models/TideExtreme.h"

// The tide interval the LED shows: the extreme the water is moving away from
// and the extreme it is moving toward.
// Typical time between a high and the next low (half of 12h25m).
const time_t kTypicalTideHalfCycleSec = 22350;

struct TideInterval {
    TideExtreme current;
    TideExtreme next;
};

// Picks the interval that contains `now`. `lastPast` is the most recent extreme
// before the stored ones (TideData::current); `extremes` are the stored future
// extremes in ascending time order. Interval ends are inclusive, so the final
// interval runs up to and including the last stored extreme.
// If `lastPast` is unknown (timestamp 0) and `now` is in the first interval,
// the start is estimated: the opposite type, one interval before the first
// stored extreme (the gap to the second one, or a typical half tide cycle).
// Returns false (leaving `out` unchanged) when there are no extremes or `now`
// is past the last one; the caller then keeps the LED as is.
bool selectTideInterval(const TideExtreme& lastPast, const TideExtreme* extremes,
                        int numExtremes, time_t now, TideInterval& out);

// Fraction of the way from `current` to `next` at `now`, clamped to [0, 1].
// A zero-length interval counts as complete (1).
float tideProgress(const TideExtreme& current, const TideExtreme& next, time_t now);
