# 0032: Battery power: 2xAA through a boost converter, sized by duty cycle

**Status:** Accepted (hardware recommendation)

**Date:** 2025-01-31

## Context

The owner wanted the device to run untethered. A first chat (2025-01-18) sized
a battery for continuous operation (Li-Po or 3xAA on the 5V pin). A later chat
analyzed the real firmware's duty cycle.

## Decision

- Power the device from 2xAA (around 2000 mAh) through a boost converter (for
  example a TPS61200-class part) with a bulk capacitor.
- Budget assumptions: ESP32-S3 about 40 mA active and about 20 uA in deep
  sleep, Wi-Fi about 120 mA only during brief updates, LED about 5 mA at low
  brightness. The estimated life was 3 to 4 months.
- The firmware supports this by keeping Wi-Fi off between updates
  (`WiFiService` disconnects, `WiFi.setSleep(true)`), low LED brightness
  ([0031](0031-led-color-semantics.md)), and a bounded Wi-Fi connect
  (`WIFI_TIMEOUT`).
- Low-battery detection was suggested but is not implemented.

**Note:** the figures are assistant estimates from the chat, not measurements.
The 3 to 4 month estimate assumes deep sleep between updates, which the current
firmware does not do ([0027](0027-deep-sleep-programming-guard.md)), so real
battery life with the current code will be much shorter.

## Alternatives rejected

- 2xAAA: smaller, but only about 50 days by the same estimate.
- LiFePO4 or an 18650 Li-ion cell with a buck converter: more capacity or a
  steadier voltage, but costlier and needs more protection circuitry.
- Li-Po or 3xAA straight to the 5V pin: sized for continuous operation and
  replaced by the duty-cycle analysis.

## Platforms

firmware

## Source

Summarized claude.ai project history: "Powering ESP32-S3 with Battery for
NeoPixel LED Project" (2025-01-18), "Powering an ESP32-S3 with Batteries"
(2025-01-31).
