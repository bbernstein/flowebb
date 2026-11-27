#pragma once
// Hardware-independent tide interval math for the LED (issue #2).
// No Arduino.h here: host unit tests in test/native/ compile this file.
#include "../models/TideExtreme.h"

// The tide interval the LED shows: the extreme the water is moving away from
// and the extreme it is moving toward.
struct TideInterval {
    TideExtreme current;
    TideExtreme next;
};

// Picks the interval that contains `now`. `lastPast` is the most recent extreme
// before the stored ones (TideData::current); `extremes` are the stored future
// extremes in ascending time order. Interval ends are inclusive, so the final
// interval runs up to and including the last stored extreme.
// Returns false (leaving `out` unchanged) when there are no extremes, when
// `now` is past the last one, or when `now` is in the first interval and
// `lastPast` is unknown (timestamp 0). The caller then keeps the LED as is.
bool selectTideInterval(const TideExtreme& lastPast, const TideExtreme* extremes,
                        int numExtremes, time_t now, TideInterval& out);

// Fraction of the way from `current` to `next` at `now`, clamped to [0, 1].
// A zero-length interval counts as complete (1).
float tideProgress(const TideExtreme& current, const TideExtreme& next, time_t now);
