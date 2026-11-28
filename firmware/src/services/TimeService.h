#pragma once
#include <Arduino.h>
#include "time.h"
#include "../config/config.h"
#include "../models/TideExtreme.h"

class TimeService {
public:
    static void initialize();
    static String formatSecondsToTime(unsigned long totalSeconds);
    static String formatLocalTime(time_t timestamp = 0);
    static void printLocalTime(time_t timestamp = 0);
    
    // Waits up to timeoutMs for NTP after initialize(); true once the clock
    // is set (see isClockSet).
    static bool waitForSync(uint32_t timeoutMs = 5000);

    static time_t getCurrentTime() {
        return time(nullptr);
    }
};
