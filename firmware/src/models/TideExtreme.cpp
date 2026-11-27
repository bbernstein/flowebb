#include "TideExtreme.h"

#include <cmath>
#include <cstring>

bool parseApiExtreme(double timestampMs, double height, const char* typeJson,
                     TideExtreme& out) {
    if (!std::isfinite(timestampMs) || !std::isfinite(height) || typeJson == nullptr) {
        return false;
    }
    // Range-check before the cast: an out-of-range double-to-integer cast is
    // undefined behavior, and the ESP32's time_t is 32-bit.
    const double seconds = timestampMs / 1000.0;
    if (!(seconds >= 1.0 && seconds < 2147483648.0)) return false;

    bool isHigh;
    if (std::strcmp(typeJson, "\"HIGH\"") == 0) {
        isHigh = true;
    } else if (std::strcmp(typeJson, "\"LOW\"") == 0) {
        isHigh = false;
    } else {
        return false;  // null, empty, or an unknown type: do not guess
    }
    out.timestamp = (time_t)seconds;
    out.height = (float)height;
    out.isHigh = isHigh;
    return true;
}

int splitTideExtremes(const TideExtreme* all, int count, time_t now,
                      TideExtreme& lastPast, TideExtreme* future, int maxFuture) {
    lastPast = TideExtreme{0, 0.0f, false};
    int numFuture = 0;
    for (int i = 0; all != nullptr && i < count; i++) {
        const TideExtreme& e = all[i];
        if (e.timestamp <= now) {
            if (!hasTimestamp(lastPast) || e.timestamp > lastPast.timestamp) {
                lastPast = e;
            }
            continue;
        }
        // Insert in ascending order, keeping the earliest maxFuture.
        int pos = numFuture;
        while (pos > 0 && future[pos - 1].timestamp > e.timestamp) pos--;
        if (pos > 0 && future[pos - 1].timestamp == e.timestamp) continue;  // duplicate
        if (pos >= maxFuture) continue;
        int last = (numFuture < maxFuture) ? numFuture : maxFuture - 1;
        for (int j = last; j > pos; j--) future[j] = future[j - 1];
        future[pos] = e;
        if (numFuture < maxFuture) numFuture++;
    }
    return numFuture;
}
