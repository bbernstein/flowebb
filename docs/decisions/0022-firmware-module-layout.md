# 0022: Firmware is split into config, models, services, storage, display and utils

**Status:** Accepted

**Date:** 2025-01-20

## Context

All firmware logic started in one `main.cpp`. Splitting it caused compile and
link errors (non-const references to temporaries in JSON helpers, header and
implementation content swapped for the time service). A large stack array of
extremes also raised a memory concern.

## Decision

- `firmware/src/` layout: `main.cpp` (setup and loop only), `config/`
  (constants in `config.h`; secrets in the gitignored `wifi_credentials.h` with
  a tracked `.template`), `models/` (TideData, TideExtreme), `services/` (Wi-Fi,
  time/NTP, tide API client), `storage/` (NVS Preferences), `display/`
  (LedController), `utils/` (JsonHelper).
- JSON serialization lives only in `JsonHelper`. `PreferencesManager` delegates
  to it rather than duplicating it.
- Credentials never go in `config.h`.
- The extremes array is capped and the fetch window is about 12 hours, to keep
  memory bounded.

## Alternatives rejected

- A single `main.cpp`: hard to maintain and to test.
- JSON code duplicated in storage and the helper: replaced by one source of
  truth in `JsonHelper`.

## Platforms

firmware

## Source

Summarized claude.ai project history: "Reorganizing Code into Proposed Folder
Structure" and "Resolving Compile Errors from Modularized C++ Code"
(2025-01-20), "Managing Multiple CMakeLists.txt Files" (2025-01-21).
