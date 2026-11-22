# 0019: Build firmware with PlatformIO and the Arduino framework

**Status:** Accepted

**Date:** 2025-01-06

## Context

Before writing the device firmware the owner compared ESP32 development setups
(Arduino IDE, PlatformIO, ESP-IDF, Eclipse, JetBrains tools). The project was
small but expected to grow into several source files and libraries.

## Decision

- PlatformIO with the Arduino framework on the `esp32-s3-devkitm-1` board
  (`firmware/platformio.ini`), used from VS Code.
- Libraries are managed through `lib_deps` (Adafruit NeoPixel, Arduino_JSON).
- Commands: `pio run`, `pio run -t upload`, `pio device monitor` at 115200
  baud. USB CDC on boot is enabled with build flags.

**Note:** ESP-IDF style `CMakeLists.txt` files remain at the firmware root and
in `src/` from the initial project template. A chat concluded they were needed
for ESP-IDF builds, but the active build path is PlatformIO with Arduino.

## Alternatives rejected

- Arduino IDE: easy to start, weak for a multi-file project and debugging.
- ESP-IDF directly: the most powerful, but a steeper setup than a one-LED device
  needs.
- CLion with PlatformIO: needs a paid license; VS Code with PlatformIO is free.

## Platforms

firmware

## Source

Summarized claude.ai project history: "Choosing the Right IDE for ESP32
Development" (2025-01-06).
