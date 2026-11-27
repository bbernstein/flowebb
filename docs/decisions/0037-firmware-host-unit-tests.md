# 0037: Firmware logic is unit-tested on the host through a native PlatformIO env

**Status:** Accepted

**Date:** 2026-11-27

## Context

The firmware had no tests. Issue #2 (the LED freezing in the final tide
interval and reading `extremes[-1]`) and a related bug in splitting API
extremes into past and future were boundary errors in plain time arithmetic.
That arithmetic sat inside classes that include `Arduino.h`, call
`millis()`, and drive the NeoPixel, so it could only be checked on a device.
CI cannot flash hardware.

## Decision

- Hardware-independent logic goes in pure source files that do not include
  `Arduino.h` (today `src/display/TideInterval.*` and
  `src/models/TideExtreme.*`). The Arduino classes (`LedController`,
  `TideService`) stay thin and call into them.
- `platformio.ini` has a `native` env (`platform = native`, pinned) that
  compiles only the pure files listed in its `build_src_filter`, with Unity
  tests in `test/native/`. Run them with `pio test -e native`.
- `default_envs` is the ESP32 env, so `pio run` still builds only the device
  image. The ESP32 env sets `test_ignore = native/*`, so its tests are never
  uploaded to a device.
- CI job `firmware-test` in `verify.yml` runs `pio test -e native` with a
  pinned PlatformIO, and is part of `ci-gate`. CI does not build the ESP32
  image, because that needs the gitignored `wifi_credentials.h`.
- A new pure file must be added to `build_src_filter`, or its tests will not
  link.

## Alternatives rejected

- On-device Unity tests (`pio test` on the ESP32 env): they need hardware
  attached, so they cannot run in CI.
- Mocking Arduino APIs (a fake `Arduino.h`, `String`, `millis`): more code to
  maintain than the logic under test, and it drifts from the real core.
- Compiling all of `src/` natively: it pulls in Wi-Fi, HTTP, and NeoPixel
  headers that have no host build.
- A separate host build (CMake or Makefile) outside PlatformIO: a second build
  system to keep in sync. PlatformIO's test runner and Unity are already in
  the toolchain ([0019](0019-platformio-arduino-toolchain.md)).

## Platforms

firmware

## Source

PR #18 (issue #2), including the `/code-review` follow-up that found the
past/future split bug in `TideService::processTideExtremes`.
