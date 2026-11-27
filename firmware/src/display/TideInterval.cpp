#include "TideInterval.h"

bool selectTideInterval(const TideExtreme& lastPast, const TideExtreme* extremes,
                        int numExtremes, time_t now, TideInterval& out) {
    if (extremes == nullptr || numExtremes <= 0) return false;

    // The next extreme is the first one at or after now.
    for (int i = 0; i < numExtremes; i++) {
        if (extremes[i].timestamp >= now) {
            out.current = (i == 0) ? lastPast : extremes[i - 1];
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
