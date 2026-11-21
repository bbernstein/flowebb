# 0031: The LED shows tide direction and progress as a red-green blend with a faint blue wave

**Status:** Accepted

**Date:** 2025-01-31

## Context

The device has one WS2812 NeoPixel (GPIO48) and no screen. That single LED has
to show whether the tide is rising or falling, and how far through the current
phase it is.

## Decision

- `firmware/src/display/LedController.cpp` finds the previous and next tide
  extreme and computes progress as the fraction of time elapsed between them.
- Color is a continuous blend, not discrete states. Green means rising and red
  means falling (`COLOR_GREEN` and `COLOR_RED` in
  `firmware/src/config/config.h`). After a low tide the LED fades from red
  toward green as the next high approaches. After a high tide it fades from
  green toward red. The color shows direction and time remaining to the next
  extreme.
- A small blue component (at most 16 of 255) follows a slow sine wave with a
  random 30 to 60 second period, as a sign the device is alive.
- Global brightness is 64 of 255 (`BRIGHTNESS`) to save power. The LED
  refreshes once per second.
- If the current time is past the stored extremes, the LED is left unchanged
  until fresh data arrives.

## Alternatives rejected

- Full brightness and fast animation: wasteful on battery. Brightness and the
  blue amplitude were deliberately reduced.
- Discrete rising and falling colors: a continuous blend also conveys the time
  to the next extreme.

## Platforms

firmware

## Source

Summarized claude.ai project history: "Powering an ESP32-S3 with Batteries"
(2025-01-31). Semantics checked against the current code.
