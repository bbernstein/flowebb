#include "TideInterval.h"

bool selectTideInterval(const TideExtreme& lastPast, const TideExtreme* extremes,
                        int numExtremes, time_t now, TideInterval& out) {
    if (extremes == nullptr || numExtremes <= 0) return false;

    // The next extreme is the first one at or after now.
    for (int i = 0; i < numExtremes; i++) {
        if (extremes[i].timestamp >= now) {
            if (i > 0) {
                out.current = extremes[i - 1];
            } else if (hasTimestamp(lastPast)) {
                out.current = lastPast;
            } else {
                // No known start: estimate one rather than leave the LED dark.
                time_t span = kTypicalTideHalfCycleSec;
                if (numExtremes >= 2 && extremes[1].timestamp > extremes[0].timestamp) {
                    span = extremes[1].timestamp - extremes[0].timestamp;
                }
                out.current.timestamp = extremes[0].timestamp - span;
                out.current.height = 0.0f;
                out.current.isHigh = !extremes[0].isHigh;
            }
            out.next = extremes[i];
            return true;
        }
    }
    return false;  // past the last stored extreme
}

float tideProgress(const TideExtreme& current, const TideExtreme& next, time_t now) {
    double total = (double)(next.timestamp - current.timestamp);
    if (total <= 0) return 1.0f;
    double progress = (double)(now - current.timestamp) / total;
    if (progress < 0) return 0.0f;
    if (progress > 1) return 1.0f;
    return (float)progress;
}
