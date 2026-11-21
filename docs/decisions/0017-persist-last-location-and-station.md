# 0017: Remember the last location and station in browser storage

**Status:** Accepted

**Date:** 2024-12-31

## Context

Returning users should see tide data immediately without picking again. The
site is static and has no server-side user state.

## Decision

- Two browser-storage records, accessed through `locationStorage` and
  `stationStorage` in `web/src/utils/storage.ts` (keys `lastLocation` and
  `lastStation`).
- The station record stores id, name and time-zone offset, so `TideInfo` can
  render in the station's zone before nearby stations load
  ([0011](0011-web-displays-station-local-time.md)).
- On load, `web/src/app/page.tsx` reads both, restores the location, fetches
  nearby stations and selects the stored station. Every map or station click
  updates the stored values.
- Reads are guarded for a missing `window` (static export prerender) and for
  corrupt JSON (return null).

## Alternatives rejected

- Server-side or account-based storage: there is no user model and no server.
- Always starting at a fixed city: a poor repeat-visit experience.

## Platforms

web

## Source

Summarized claude.ai project history: "Saving and Restoring Last Selected
Station in Frontend" (2024-12-31).
