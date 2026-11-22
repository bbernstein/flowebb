# 0010: Subordinate stations: interpolate smoothly between NOAA extremes

**Status:** Accepted

**Date:** 2024-12-20

## Context

NOAA reference stations (type R) provide 6-minute predictions. Subordinate
stations (type S) provide only high and low times and heights. Straight lines
between extremes looked unrealistic. A first attempt with a library cubic
spline threw out-of-range errors whenever the requested time fell outside the
first or last extreme.

## Decision

- For S stations, and whenever the prediction series is empty, build 6-minute
  values by cubic (Hermite-style) interpolation between consecutive extremes.
  Outside the range, clamp to the first or last extreme.
- Fetch extremes from one extra day earlier, so the start of the requested day
  is bracketed by a known extreme.
- R stations use linear interpolation over their 6-minute predictions.
- Code: `interpolateExtremes` in `backend/internal/tide/service.go`. It relies
  on the station type being kept from the NOAA station list.

## Alternatives rejected

- Linear interpolation between extremes: unrealistic tide shape.
- A library spline over the extremes only: throws when the query time is
  outside the data, so extremes must cover the whole window.

## Platforms

backend

## Source

Summarized claude.ai project history: "Improving Tide Level Interpolation with
Cubic Splines" (2024-12-20), "Implementing NOAA Tide Prediction API in Go"
(2024-12-30).
