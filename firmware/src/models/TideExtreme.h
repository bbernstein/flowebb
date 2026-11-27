#pragma once
// Hardware-independent: no Arduino.h, so host unit tests can include it.
#include <ctime>

struct TideExtreme {
    time_t timestamp;
    float height;
    bool isHigh;
};
