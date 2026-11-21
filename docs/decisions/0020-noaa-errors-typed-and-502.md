# 0020: NOAA failures become typed errors, and must not pass as empty data

**Status:** Accepted

**Date:** 2025-01-11

## Context

Every error surfaced as a generic 500, and the web app showed "Failed to fetch
tide data". Separately, extremes once came back null because the NOAA response
was not decoded properly, and NOAA sometimes answers HTTP 200 with an error
object in the body.

## Decision

- `backend/internal/tide/errors.go` defines `NoaaAPIError` (wrapping the cause)
  and an invalid-range error.
- The fetch functions in `backend/internal/tide/service.go` return
  `NoaaAPIError` for HTTP failures, decode failures, and a 200 response whose
  body carries an error, for both predictions and extremes. Never treat a 200
  as success without checking the body.
- If the prediction fetch fails, the error is logged and the request
  interpolates from extremes where it can, rather than failing outright.
- `backend/cmd/tides/main.go` maps `NoaaAPIError` (via `errors.As`) to 502 Bad
  Gateway and everything else to 500. The web app showed a specific message on
  502.
- Tests cover the 200-with-error case with a mock NOAA server.

**Note:** the deployed API is the GraphQL Lambda (`cmd/graphql`), and its
resolvers return plain errors. GraphQL clients cannot currently tell an
upstream NOAA failure from an internal one. `cmd/tides` is not deployed by CI.

## Alternatives rejected

- A generic 500 for everything: hides that NOAA is the failing party.
- Trusting a 200 response without checking the body: yields empty or null data
  silently.

## Platforms

backend, web

## Source

Summarized claude.ai project history: "Troubleshooting Missing Tide Extremes
Data" (2025-01-09), "Test NOAA Extremes API Error Response" (2025-01-10),
"Handling NOAA API Errors in Go" and "Handling 502 Errors in Tide API"
(2025-01-11).
