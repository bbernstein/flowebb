#pragma once
// Hardware-independent: no Arduino.h, so host unit tests can include it.
#include <ctime>

struct TideExtreme {
    time_t timestamp;  // UTC epoch seconds; 0 means unknown
    float height;
    bool isHigh;
};

// True when the extreme holds a real time (timestamp 0 marks it unknown).
inline bool hasTimestamp(const TideExtreme& e) { return e.timestamp != 0; }

// 2024-01-01T00:00:00Z. Before NTP sync, time(nullptr) counts from about 0,
// so anything earlier means the device clock has not been set.
const time_t kMinValidEpochSec = 1704067200;
inline bool isClockSet(time_t now) { return now >= kMinValidEpochSec; }

// Builds an extreme from one API entry: `timestampMs` is UTC epoch
// milliseconds, and `typeJson` is the JSON-stringified type ("\"HIGH\"" or
// "\"LOW\""). Returns false, leaving `out` unchanged, when the height is not
// finite, the timestamp is not in (0, 2^31) seconds (the 32-bit time_t range
// on the ESP32), or the type is not exactly "\"HIGH\"" or "\"LOW\"".
bool parseApiExtreme(double timestampMs, double height, const char* typeJson,
                     TideExtreme& out);

// Splits extremes from the API into the most recent one at or before `now`
// (written to `lastPast`, or zeroed when there is none) and the ones after
// `now`. The future ones are copied to `future` in ascending time order: the
// earliest `maxFuture` of them if there are more. All times
// are UTC epoch seconds, the same base as time(nullptr) and the API.
// Returns how many future extremes were written.
int splitTideExtremes(const TideExtreme* all, int count, time_t now,
                      TideExtreme& lastPast, TideExtreme* future, int maxFuture);
