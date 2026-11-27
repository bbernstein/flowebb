# firmware/: ESP32-S3 tide LED device

Read the root `CLAUDE.md` first. Run every command below from `firmware/`.

## Stack

- ESP32-S3 (`esp32-s3-devkitm-1`), Arduino framework via PlatformIO.
- Libraries: Adafruit NeoPixel, Arduino_JSON (`platformio.ini`).
- One WS2812 LED on GPIO48. The device fetches tides over Wi-Fi, caches them in
  Preferences, and updates the LED. The main loop stays awake: it does not
  deep-sleep today, and `DEEP_SLEEP_DURATION` in `config.h` is unused. See
  [0027](../docs/decisions/0027-deep-sleep-programming-guard.md) before
  re-enabling deep sleep.

## Directory map

| Path | Contents |
|---|---|
| `src/main.cpp` | setup/loop (always-awake loop; wake-cause cache logic) |
| `src/services/TideService.*` | GraphQL request to the API and response parsing |
| `src/services/WiFiService.*`, `src/services/TimeService.*` | Wi-Fi connection, NTP time |
| `src/display/` | LED controller (colors, wave animation) |
| `src/models/`, `src/storage/`, `src/utils/` | tide data model, Preferences storage, helpers |
| `src/config/config.h` | station ID, location, timing, LED settings (committed) |
| `src/config/wifi_credentials.h.template` | template for Wi-Fi credentials and `TIDE_API_ENDPOINT` |
| `test/native/` | host-side Unity tests for hardware-independent logic (`pio test -e native`) |

## Commands

```bash
# One-time: create the local credentials file (gitignored), then edit it
cp src/config/wifi_credentials.h.template src/config/wifi_credentials.h

pio run                  # build
pio run -t upload        # flash a connected device
pio device monitor       # serial monitor (115200 baud)
pio test -e native       # host-side unit tests in test/native/ (no device needed)
```

`pio run` builds only the ESP32 env (`default_envs`). The `native` env compiles
just the pure sources listed in its `build_src_filter` (today
`src/display/TideInterval.cpp` and `src/models/TideExtreme.cpp`). Code in that
list must not include `Arduino.h`; put the testable logic there and keep the
Arduino glue thin. See
[0037](../docs/decisions/0037-firmware-host-unit-tests.md).

The API endpoint is `TIDE_API_ENDPOINT` (`https://api.flowebb.com/graphql`) in
`src/config/`. CI runs `pio test -e native` (the `firmware-test` job) but does
not build the ESP32 image, so run `pio run` yourself before reporting.

## Rules

- The device depends on the API fields it reads. Do not break them. It sends
  `tides(stationId, startDateTime, endDateTime)` and reads `localTime`,
  `waterLevel`, `tideType`, `timeZoneOffsetSeconds`, and
  `extremes { type timestamp height }`. Devices in the field update rarely
  and by hand, so the backend must keep serving these fields unchanged.
- New logic needs unit tests. Put hardware-independent logic where `pio test`
  can cover it. If something can only be verified on hardware, say so in your
  report.
- Keep memory and power in mind: Wi-Fi off between updates, and no blocking
  waits beyond `WIFI_TIMEOUT`. Any return to deep sleep must keep the GPIO0
  programming-mode guard ([0027](../docs/decisions/0027-deep-sleep-programming-guard.md)).
- Never flash a device unless the owner asks you to.

## Never commit secrets or private data

The root rule applies in full. Here: never commit
`src/config/wifi_credentials.h`. Only the `.template` with placeholder values
is tracked. Never put real SSIDs, passwords, or tokens in `config.h` or the
template.

## Decisions

Check [`docs/decisions/`](../docs/decisions/README.md) before changing behavior here.

Key decisions:

- [0026: The device is a thin GraphQL client and depends on fixed tide fields](../docs/decisions/0026-device-graphql-contract.md)
- [0031: The LED color tracks tide level, red at low water and green at high, with a faint blue wave](../docs/decisions/0031-led-color-semantics.md)
- [0027: Deep sleep must keep a programming-mode escape (GPIO0)](../docs/decisions/0027-deep-sleep-programming-guard.md)
- [0022: Firmware is split into config, models, services, storage, display and utils](../docs/decisions/0022-firmware-module-layout.md)
- [0037: Firmware logic is unit-tested on the host through a native PlatformIO env](../docs/decisions/0037-firmware-host-unit-tests.md)
- [0032: Battery power: 2xAA through a boost converter, sized by duty cycle](../docs/decisions/0032-battery-power-budget.md)
