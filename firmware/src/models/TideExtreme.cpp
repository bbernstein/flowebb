#include "TideExtreme.h"

#include <cmath>
#include <cstring>

bool parseApiExtreme(double timestampMs, double height, const char* typeJson,
                     TideExtreme& out) {
    if (!std::isfinite(timestampMs) || !std::isfinite(height) || typeJson == nullptr) {
        return false;
    }
    time_t ts = (time_t)(timestampMs / 1000);
    if (ts == 0) return false;  // 0 is the "unknown" marker
    out.timestamp = ts;
    out.height = (float)height;
    out.isHigh = std::strstr(typeJson, "HIGH") != nullptr;
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
        if (pos >= maxFuture) continue;
        int last = (numFuture < maxFuture) ? numFuture : maxFuture - 1;
        for (int j = last; j > pos; j--) future[j] = future[j - 1];
        future[pos] = e;
        if (numFuture < maxFuture) numFuture++;
    }
    return numFuture;
}
