# Web: station-day window (#5) and stable fetchStations (#4)

Branch: `fix/web-station-day-and-fetchstations`. One web PR closes #5 and #4.
All work is under `web/`. Run every command from `web/`.

## Global Constraints

- Only `web/` files change, plus this plan. No schema or backend change.
- Run `npm run lint` and `npm test` from `web/` before each commit. Both must pass.
- Do not change an existing test's expectations. Adding new tests is fine.
  If an existing test fails, stop and report it (owner rule).
- Commit messages are descriptive and end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01HTgPMHqzrQDCgdrRUaQ6kL
  ```
- Never commit to `main`. Never push (the controller pushes).
- The API takes `startDateTime` / `endDateTime` as **station-local wall-clock**
  strings with no zone, format `YYYY-MM-DDTHH:mm:ss` (decision 0015).
- The window is the station's calendar day padded by 12 hours on each side:
  start = station-local midnight − 12h, end = start + 48h. Keep that padding.

## Task 1: getStationDayBounds ignores the browser timezone (#5)

**Files:** `web/src/utils/dateTime.tsx`, `web/src/utils/__tests__/dateTime.test.ts`

**Bug:** `getStationDayBounds` uses browser-local `Date` methods (`setHours`,
`getFullYear`, `getHours`…), so the browser's zone leaks into the result.
`setHours(h + offsetSeconds / 3600)` also truncates half-hour offsets. Example:
an LA browser, a PST station (`-28800`) and `2024-01-01T12:00:00Z` currently
gives `2023-12-30T12:00:00`..`2024-01-01T12:00:00`. The correct answer is
`2023-12-31T12:00:00`..`2024-01-02T12:00:00`.

**Step 1: tests first (TDD).** Add a `describe` block that runs the cases below
under several browser zones by setting `process.env.TZ` at runtime. Node
re-reads `TZ` on assignment. Save the original value in `beforeAll` and restore
it in `afterAll`. Zones: `UTC`, `America/Los_Angeles`, `Asia/Kolkata`,
`Pacific/Kiritimati`. Inside each zone block, first assert that the zone change
took effect, so the test can't pass vacuously. For example, under
`America/Los_Angeles`, `new Date('2024-01-01T12:00:00Z').getTimezoneOffset()` is
`480`. Under `Asia/Kolkata` it is `-330`, under `Pacific/Kiritimati` `-840`, and
under `UTC` `0`.

Cases: `(timestamp ISO, offsetSeconds) → startDateTime, endDateTime, startOfDay.toISOString()`

| # | timestamp | offset | startDateTime | endDateTime | startOfDay ISO |
|---|---|---|---|---|---|
| a | 2024-01-01T12:00:00Z | -28800 | 2023-12-31T12:00:00 | 2024-01-02T12:00:00 | 2023-12-31T20:00:00.000Z |
| b | 2024-01-01T03:00:00Z | -28800 | 2023-12-30T12:00:00 | 2024-01-01T12:00:00 | 2023-12-30T20:00:00.000Z |
| c | 2024-01-01T20:00:00Z | 19800 | 2024-01-01T12:00:00 | 2024-01-03T12:00:00 | 2024-01-01T06:30:00.000Z |
| d | 2024-03-10T12:00:00Z | -18000 | 2024-03-09T12:00:00 | 2024-03-11T12:00:00 | 2024-03-09T17:00:00.000Z |
| e | 2024-01-01T08:00:00Z | -28800 | 2023-12-31T12:00:00 | 2024-01-02T12:00:00 | 2023-12-31T20:00:00.000Z |

- (b): the station day differs from the UTC day.
- (c): a half-hour offset (India).
- (d): the US DST-change date. The browser's DST must not shift the result.
- (e): exactly station-local midnight.

Also assert `endOfDay.getTime() - startOfDay.getTime() === 48 * 3600 * 1000` for each case.

Run the tests and confirm that the LA, Kolkata or Kiritimati cases **fail**
against the current code before you change it. Record which ones failed in the report.

**Step 2: implement.** Replace the body of `getStationDayBounds` with pure UTC
arithmetic. Keep its signature, the `DayBounds` type and its export:

```ts
const MS_PER_HOUR = 60 * 60 * 1000;

export const getStationDayBounds = (timestamp: number, offsetSeconds: number): DayBounds => {
    const offsetMs = offsetSeconds * 1000;
    // Station wall-clock time encoded as a UTC instant, so the UTC getters read
    // the station's calendar fields and the browser's own zone never applies.
    const stationWall = new Date(timestamp + offsetMs);
    const midnightWallMs = Date.UTC(
        stationWall.getUTCFullYear(),
        stationWall.getUTCMonth(),
        stationWall.getUTCDate()
    );
    const startWallMs = midnightWallMs - 12 * MS_PER_HOUR;
    const endWallMs = startWallMs + 48 * MS_PER_HOUR;

    // YYYY-MM-DDTHH:mm:ss, station-local, no zone (what the API expects).
    const formatForApi = (wallMs: number) => new Date(wallMs).toISOString().slice(0, 19);

    return {
        startOfDay: new Date(startWallMs - offsetMs),
        endOfDay: new Date(endWallMs - offsetMs),
        startDateTime: formatForApi(startWallMs),
        endDateTime: formatForApi(endWallMs),
    };
};
```

Update the JSDoc. Say that `startOfDay` / `endOfDay` are the real instants of
the window edges, and that the strings are station-local wall-clock time for
the API. No caller reads `startOfDay` / `endOfDay` (only `TideInfo.tsx` and
`TideChart.tsx` call this, and they read only the strings).

**Step 3:** `npm test` and `npm run lint` pass. Commit:
`web: derive the station-day window with UTC math so the browser timezone can't shift it (#5)`.

## Task 2: stable fetchStations and context value (#4)

**Files:** `web/src/context/StationContext.tsx`,
`web/src/context/__tests__/StationContext.test.tsx`, `web/src/app/page.tsx`

**Bug:** `fetchStations` is recreated on every `StationProvider` render, and so
is the context `value` object. PR #14 worked around this in `page.tsx` with
`fetchStationsRef`. Fix the root cause and remove the workaround.

**Step 1: test first.** In `StationContext.test.tsx`, add:
`"keeps fetchStations and the context value stable across provider re-renders"`.
Render with a mock for `{ lat: 47.6, lon: -122.3 }`, capture
`result.current.fetchStations`, then call `fetchStations` inside `act`. That
updates `stations` state and re-renders the provider. Assert that
`result.current.fetchStations` is the same reference (`toBe`) as the captured
one. Also call `rerender()` and assert identity again. Confirm the test fails
before the fix.

**Step 2: implement in `StationContext.tsx`:**
- Wrap `fetchStations` in `useCallback(..., [refetch])`. Apollo 3.12's
  `useQuery` returns a stable `refetch`.
- Wrap the provider value in `useMemo`, with deps
  `[stations, loading, error?.message, fetchStations]`. The value still exposes
  `error: error?.message || null`.
- Remove the stale inline comment `// Use the state variable instead of data?.stations`.

**Step 3: remove the workaround in `page.tsx`:**
- Delete the `fetchStationsRef` declaration, its sync `useEffect`, and the
  comment above them ("fetchStations gets a new identity on every
  StationProvider render…").
- In the first-load effect, call `fetchStations(loc.lat, loc.lon)` directly, and
  change the effect's dependency array from `[]` to `[fetchStations]`.
  `fetchStations` is now stable, so the effect still runs once on mount.
- If `useRef` is still used elsewhere (it is: `userPickedLocation`), keep the import.

**Step 4:** `npm test`, `npm run lint` and `npm run build` pass. The existing
`page.test.tsx` tests must pass unchanged. Commit:
`web: memoize fetchStations and the station context value; drop the page ref workaround (#4)`.
