# 0029: Picking a location always selects the nearest station

**Status:** Accepted

**Date:** 2025-01-27

## Context

A map click finds the five nearest stations. Showing the nearest one's tide
data worked only some of the time, because of a race between the click handler
and React state updates.

## Decision

- On any location pick, the nearest station is selected and its `TideInfo` and
  `TideChart` are shown immediately. The click also updates the location pin.
- The station fetch function in `StationContext`
  (`web/src/context/StationContext.tsx`) returns the fetched list, and the
  click handler takes the first element of that returned list. It must not read
  the `stations` state, which may still hold the previous result. This matters
  most when the new click is far from the previous one.

## Alternatives rejected

- Select from React state after the fetch: a stale value, which caused the
  intermittent bug.
- Select nothing until the user picks a station: an extra click for the common
  case.

## Platforms

web

## Source

Summarized claude.ai project history: "Displaying Tide Info for Nearest
Station" (2025-01-08), "Automatically Display Nearest Station on Location
Selection" and "Updating Tide Info and Chart on Location Change" (2025-01-27).
