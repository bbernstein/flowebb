# 0027: Deep sleep must keep a programming-mode escape (GPIO0)

**Status:** Partly superseded (deep sleep is off in current code; the guard rule stands)

**Date:** 2025-01-22

## Context

Deep sleep was added to save battery: a 5-minute wake interval, with tide data
cached in NVS (Preferences) so Wi-Fi is used only when data is stale. After
flashing that version the board could no longer be programmed. The ESP32-S3
slept, and its USB CDC port disappeared, before the uploader could connect
("failed to write to target RAM" timeouts).

## Decision

- Early in `setup()`, check GPIO0 (`PROG_PIN`, after `PROG_MODE_CHECK_DELAY`)
  and skip the sleep path if programming mode is detected. Anyone re-enabling
  deep sleep must keep this escape hatch.
- Recovery when locked out: enter download mode by holding BOOT while resetting
  or plugging in USB, then erase flash and upload again.
- On a timer wake, `setup()` loads cached data from Preferences and connects to
  Wi-Fi only if `needsUpdate()`.

**Note:** current code keeps `DEEP_SLEEP_DURATION` in
`firmware/src/config/config.h` and the wake-cause cache logic in
`firmware/src/main.cpp`, but `loop()` runs continuously with a 10 ms delay and
never calls `esp_deep_sleep_start()`. `firmware/CLAUDE.md` still describes the
device as deep-sleeping. The owner should confirm which is intended.

## Alternatives rejected

- Only a longer delay before sleeping: did not help once the device was locked
  up.
- Wi-Fi always on with no cache: higher power draw.

## Platforms

firmware

## Source

Summarized claude.ai project history: "Troubleshooting ESP32 Deep Sleep Upload
Failure" (2025-01-22).
