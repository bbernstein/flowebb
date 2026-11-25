# 0036: A first visit always ends on the map, with a location fallback

**Status:** Accepted

**Date:** 2026-11-25

## Context

A visitor with no saved location could stay on the loading spinner forever
(issue #13). The mount effect in `web/src/app/page.tsx` cleared `loading`
only for returning visitors. The geolocation branches and the "no
geolocation" case never did. Some browsers also never call either
geolocation callback when the visitor dismisses the prompt. The effect also
re-ran whenever `fetchStations` changed identity, which happens on every
`StationProvider` render.

## Decision

- Every first-load path goes through one helper that fetches stations, selects
  a station, and clears `loading` in a `finally`. A failed or empty fetch shows
  an error, but the map and **Use My Location** button still appear. The error
  clears on the next successful load.
- `fetchStations` in `StationContext` rethrows after logging, so callers can
  tell a failed request ("Failed to load stations") from an empty result ("No
  tide stations found near this location"). It used to return `[]` either way.
- A first visit selects the nearest station, the same as a map click
  ([0029](0029-auto-select-nearest-station.md)). A returning visitor keeps the
  stored station if it is still in the fetched list. If it is not, the nearest
  station is shown and saved, since only a fetched station can be displayed.
  **Use My Location** now follows 0029 too: it moves the map and selects the
  nearest station.
- If geolocation is unavailable or denied, the app uses Seattle and saves it,
  as before. If the prompt gets no answer within 10 seconds, the app shows
  Seattle but does not save it, so the next visit asks again. If the visitor
  allows location after the fallback, their real position replaces Seattle,
  unless they have already picked a spot on the map or pressed **Use My
  Location**.
- The first-load effect runs only on mount and reads `fetchStations` through a
  ref. Its cleanup cancels the fallback timer and ignores any callbacks or
  fetches that resolve after unmount. A request counter keeps a stale fetch
  from overwriting a newer one.
- `locationStorage.get()` returns `null` for a stored location that is not a
  pair of finite numbers, so a corrupt record is treated as a first visit.

## Alternatives rejected

- No timeout: a dismissed prompt leaves the spinner up in some browsers.
- Ignoring a late answer after the fallback: a visitor who takes more than 10
  seconds to click Allow would be stuck at Seattle.
- Checking `navigator.permissions` to start the timer only while the prompt
  is open: not available in every browser, and it does not cover a slow fix
  after permission is granted.
- Showing the map at the default location while the prompt is open: the map
  would jump once the answer arrives.

## Platforms

web
