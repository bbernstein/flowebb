# firmware/: ESP32-S3 tide LED device

Read the root `CLAUDE.md` first. Run every command below from `firmware/`.

## Stack

- ESP32-S3 (`esp32-s3-devkitm-1`), Arduino framework via PlatformIO.
- Libraries: Adafruit NeoPixel, Arduino_JSON (`platformio.ini`).
- One WS2812 LED on GPIO48. The device wakes, fetches tides over Wi-Fi, updates
  the LED, and deep-sleeps.

## Directory map

| Path | Contents |
|---|---|
| `src/main.cpp` | setup/loop, sleep cycle |
| `src/services/TideService.*` | GraphQL request to the API and response parsing |
| `src/services/WiFiService.*`, `src/services/TimeService.*` | Wi-Fi connection, NTP time |
| `src/display/` | LED controller (colors, wave animation) |
| `src/models/`, `src/storage/`, `src/utils/` | tide data model, Preferences storage, helpers |
| `src/config/config.h` | station ID, location, timing, LED settings (committed) |
| `src/config/wifi_credentials.h.template` | template for Wi-Fi credentials and `TIDE_API_ENDPOINT` |
| `test/` | PlatformIO unit tests (none yet) |

## Commands

```bash
# One-time: create the local credentials file (gitignored), then edit it
cp src/config/wifi_credentials.h.template src/config/wifi_credentials.h

pio run                  # build
pio run -t upload        # flash a connected device
pio device monitor       # serial monitor (115200 baud)
pio test                 # run unit tests in test/ (when present)
```

The API endpoint is `TIDE_API_ENDPOINT` (`https://api.flowebb.com/graphql`) in
`src/config/`. CI does not build firmware yet, so run `pio run` yourself before
reporting.

## Rules

- The device depends on the API fields it reads. Do not break them. It sends
  `tides(stationId, startDateTime, endDateTime)` and reads `localTime`,
  `waterLevel`, `tideType`, `timeZoneOffsetSeconds`, and
  `extremes { type timestamp height }`. Devices in the field update rarely
  and by hand, so the backend must keep serving these fields unchanged.
- New logic needs unit tests. Put hardware-independent logic where `pio test`
  can cover it. If something can only be verified on hardware, say so in your
  report.
- Keep memory and power in mind: deep sleep between checks, and no blocking
  waits beyond `WIFI_TIMEOUT`.
- Never flash a device unless the owner asks you to.

## Never commit secrets or private data

The root rule applies in full. Here: never commit
`src/config/wifi_credentials.h`. Only the `.template` with placeholder values
is tracked. Never put real SSIDs, passwords, or tokens in `config.h` or the
template.

## Decisions

See `docs/decisions/` (records will be added there).
