# 0008: Use NOAA's predictions, not our own harmonic tide calculation

**Status:** Accepted

**Date:** 2024-12-18

## Context

The original backend computed tide heights from NOAA harmonic constituents,
with a flag to switch to NOAA's own predictions for comparison. Over several
sessions (2024-12-04 to 2024-12-17) the computed values never matched NOAA:
heights were offset by a datum amount, highs and lows were about an hour off,
and amplitudes were damped. Closing the gap would have needed node factors,
equilibrium arguments, shallow-water constituents and datum handling. Some
stations also lack constituents, which forced a "stations that can show tides"
filter.

## Decision

- NOAA predictions are the only source of tide data. The backend fetches
  6-minute predictions and high/low extremes from the NOAA data API
  (`backend/internal/tide/service.go`).
- There is no harmonic-constituent code, no stored constants, and no fallback
  calculation. If NOAA is unavailable the request fails, or uses only
  interpolation over data already cached from NOAA.
- Subordinate stations, which have extremes but no 6-minute series, are handled
  by interpolation ([0010](0010-subordinate-stations-interpolate-extremes.md)).

## Alternatives rejected

- Keep computing from harmonic constituents with a NOAA toggle: never reached
  NOAA's accuracy, and added constants caching, per-station availability
  filtering and comparison parameters.
- Computation as a fallback when NOAA fails: silently wrong tide heights are
  worse than a clear failure.

## Platforms

backend

## Source

Summarized claude.ai project history: "Replacing Tide Level Calculation with
Harmonic Analysis" (2024-12-04), "Reconciling Tidal Harmonic Calculations with
NOAA API" (2024-12-17), "Replacing Tide Harmonic Calculations with NOAA API"
(2024-12-18).
