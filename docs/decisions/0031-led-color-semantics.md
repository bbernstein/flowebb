# 0031: The LED color tracks tide level, red at low water and green at high, with a faint blue wave

**Status:** Accepted

**Date:** 2025-01-31

## Context

The device has one WS2812 NeoPixel (GPIO48) and no screen. That single LED has
to show where the tide is between low and high water, and which way it is
heading.

## Decision

- `firmware/src/display/LedController.cpp` finds the previous and next tide
  extreme and computes progress as the fraction of time elapsed between them.
- Color tracks the tide level as a continuous red-green blend
  (`calculateTideColor`): full red at low water, full green at high water.
  Both phases pass through a mixed yellow at the midpoint between extremes.
- Direction is not a separate color. It shows only in which way the color is
  fading: red toward green after a low (rising), green toward red after a high
  (falling). A single reading near the midpoint looks the same on a rising and
  a falling tide.
- A small blue component (at most 16 of 255) follows a slow sine wave with a
  random 30 to 60 second period, as a sign the device is alive.
- Global brightness is 64 of 255 (`BRIGHTNESS`) to save power. The LED
  refreshes once per second.
- If the current time is past the stored extremes, the LED is left unchanged
  until fresh data arrives.

**Note:** the comments in `firmware/src/config/config.h` (`COLOR_RED` "For
falling tide", `COLOR_GREEN` "For rising tide") are misleading. Red and green
mark low and high water, not direction, and `calculateTideColor` does not use
those constants; it builds the red and green channels directly.

## Alternatives rejected

- Full brightness and fast animation: wasteful on battery. Brightness and the
  blue amplitude were deliberately reduced.
- Discrete rising and falling colors: a continuous blend also shows how close
  the tide is to the next extreme.

## Platforms

firmware

## Source

Summarized claude.ai project history: "Powering an ESP32-S3 with Batteries"
(2025-01-31). Semantics checked against the current code.
