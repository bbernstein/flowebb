# Decision records

Short records of non-obvious choices, so agents and humans do not undo past
nuances. Check the records for an area before changing its behavior. Add one
when you make a choice a future reader might second-guess.

Raw transcripts are never committed. Summarize the decision in your own words,
and leave out personal or sensitive details.

Records 0001 to 0034 were mined from the owner's claude.ai project history
(November 2024 to January 2025) and the 2026 monorepo migration, then checked
against the code at the time of writing. Later records are written as decisions
are made. A **Note:** line marks anything
uncertain: inferred alternatives, assistant estimates, or code that disagrees
with the original decision. Records are numbered by date.

## Index

Each record is listed once, under the platform it mainly constrains. Its
**Platforms** section lists every platform it touches.

### Backend

- [0002: Cache the full NOAA station list as one S3 object](0002-station-list-cached-as-one-s3-object.md). Accepted.
- [0008: Use NOAA's predictions, not our own harmonic tide calculation](0008-noaa-predictions-not-harmonic-calculation.md). Accepted.
- [0009: Cache predictions per station-day in DynamoDB behind an in-memory LRU](0009-prediction-cache-per-station-day.md). Accepted.
- [0010: Subordinate stations: interpolate smoothly between NOAA extremes](0010-subordinate-stations-interpolate-extremes.md). Accepted.
- [0013: Rewrite the backend from Kotlin/Ktor on the JVM to Go](0013-rewrite-backend-in-go.md). Accepted.
- [0014: Local versus production behavior is set by the SAM template](0014-local-vs-prod-config-from-sam-template.md). Accepted.
- [0015: The backend handles all tide times in the station's local zone](0015-backend-times-in-station-local-zone.md). Accepted.
- [0018: Cache sizes and TTLs come from CACHE_* environment variables](0018-cache-sizes-and-ttls-from-env.md). Accepted.
- [0020: NOAA failures become typed errors, and must not pass as empty data](0020-noaa-errors-typed-and-502.md). Accepted.
- [0025: Serve the API as one GraphQL Lambda built with gqlgen](0025-graphql-api-with-gqlgen.md). Accepted.
- [0028: Backend tests: 80% coverage without generated code, injected dependencies, no real NOAA or AWS](0028-backend-testing-and-coverage.md). Accepted.

### Web

- [0006: Use Material UI for web components, not Tailwind or shadcn/ui](0006-mui-component-library.md). Accepted.
- [0007: Map waits for the stored location and fits bounds to the click plus stations](0007-map-loading-and-bounds.md). Accepted.
- [0011: The web app shows every time in the station's local zone](0011-web-displays-station-local-time.md). Accepted.
- [0012: Highcharts for the tide chart; accessibility module currently disabled](0012-highcharts-tide-chart-accessibility.md). Accepted.
- [0017: Remember the last location and station in browser storage](0017-persist-last-location-and-station.md). Accepted.
- [0021: Logo is an animated SVG wordmark, "flow" rising and "ebb" receding](0021-animated-wordmark-logo.md). Accepted.
- [0023: Tide chart "now" marker ticks each minute; range shifts every six hours](0023-tide-chart-live-now-marker.md). Accepted.
- [0029: Picking a location always selects the nearest station](0029-auto-select-nearest-station.md). Accepted.

### WWW

- [0035: The www landing page is its own static site and CloudFront distribution](0035-www-landing-site.md). Accepted.

### Infra

- [0003: Terraform layout: per-concern modules, one prod root, no cycles](0003-terraform-modules-and-single-prod-root.md). Accepted.
- [0004: Infra and application deploys are separate; Terraform ignores Lambda code](0004-infra-and-app-deploys-are-separate.md). Accepted.
- [0005: GitHub Actions assumes an AWS role through OIDC, never static keys](0005-github-actions-uses-oidc-role.md). Accepted.
- [0016: Remote Terraform state in S3 with a DynamoDB lock table](0016-remote-terraform-state-with-lock.md). Accepted.
- [0034: Trust GitHub's ID-based OIDC subject for the monorepo, applied once locally with Terraform 1.5.0](0034-oidc-id-based-subject.md). Accepted.

### Firmware

- [0019: Build firmware with PlatformIO and the Arduino framework](0019-platformio-arduino-toolchain.md). Accepted.
- [0022: Firmware is split into config, models, services, storage, display and utils](0022-firmware-module-layout.md). Accepted.
- [0024: License the firmware under MIT](0024-firmware-mit-license.md). Accepted.
- [0026: The device is a thin GraphQL client and depends on fixed tide fields](0026-device-graphql-contract.md). Accepted.
- [0027: Deep sleep must keep a programming-mode escape (GPIO0)](0027-deep-sleep-programming-guard.md). Partly superseded (deep sleep is off in current code; the guard rule stands).
- [0031: The LED color tracks tide level, red at low water and green at high, with a faint blue wave](0031-led-color-semantics.md). Accepted.
- [0032: Battery power: 2xAA through a boost converter, sized by duty cycle](0032-battery-power-budget.md). Accepted (hardware recommendation).

### Cross-cutting

- [0001: Static site on S3 and CloudFront, API on API Gateway and Lambda](0001-static-site-and-api-on-separate-domains.md). Accepted.
- [0030: Split the original monorepo into backend, web and infra repos](0030-split-monorepo-2025.md). Superseded by [0033](0033-monorepo-reunified-2026.md).
- [0033: Return to a single monorepo now that Claude Code reads files on demand](0033-monorepo-reunified-2026.md). Accepted.

<!-- Add one bullet per record under its main platform: link text "NNNN: Title", target the record file, then its status. -->

## Template

Name files `NNNN-short-title.md` (e.g. `0001-dynamodb-prediction-cache.md`).
Use the next free number. To supersede a record, add a new one and change the
old one's status to `Superseded by NNNN`; do not delete it.

```markdown
# NNNN: Title

**Status:** Accepted | Superseded by NNNN | Partly superseded

**Date:** YYYY-MM-DD

## Context
What problem or constraint forced a choice.

## Decision
What was chosen, stated so it can be checked against the code.

## Alternatives rejected
Each option considered, and why it lost.

## Platforms
backend / web / infra / firmware / apps: which ones this constrains.

## Source
Where this came from (PR, issue, design doc, or a summarized prior discussion).
```
