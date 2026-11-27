#include "TideExtreme.h"

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
        } else if (numFuture < maxFuture) {
            future[numFuture++] = e;
        }
    }
    return numFuture;
}
