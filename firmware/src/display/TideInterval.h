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
// Returns false (leaving `out` unchanged) when there are no extremes or `now`
// is past the last one; the caller should keep the LED as is until new data.
bool selectTideInterval(const TideExtreme& lastPast, const TideExtreme* extremes,
                        int numExtremes, time_t now, TideInterval& out);

// Fraction of the way from `current` to `next` at `now`, clamped to [0, 1].
// A zero-length interval counts as complete (1).
float tideProgress(const TideExtreme& current, const TideExtreme& next, time_t now);
