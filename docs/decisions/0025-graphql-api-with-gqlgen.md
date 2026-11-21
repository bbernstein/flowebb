# 0025: Serve the API as one GraphQL Lambda built with gqlgen

**Status:** Accepted

**Date:** 2025-01-22

## Context

The Go backend had separate REST-style tides and stations handlers. In late
January 2025 the API moved to GraphQL on gqlgen, and the web app and device
moved with it. Re-running `gqlgen generate` after refactoring then broke the
build: resolver dependencies vanished into comments, and a new
`schema.resolvers.go` duplicated methods written by hand in `resolver.go`.
After that, POST requests to the local endpoint returned 400 "transport not
supported" when the server was built with a bare `handler.New`.

## Decision

- One GraphQL Lambda (`backend/cmd/graphql`) serves every client.
  `backend/graph/schema.graphql` is the contract. Changes to it must stay
  backward compatible (see "The API contract" in the root `CLAUDE.md`).
- `backend/gqlgen.yml` uses `layout: follow-schema`. Resolver methods live in
  `backend/graph/schema.resolvers.go`. `backend/graph/resolver.go` holds only
  the `Resolver` struct and its injected dependencies (tide service, station
  finder).
- Generated output (`graph/generated`, `graph/model`) is never edited by hand.
  Regenerate with the pinned gqlgen installed by `go install`, after removing
  those directories. Mismatched gqlgen versions broke the generated prelude
  resolvers, so update the pin deliberately.
- `backend/graph/handler.go` builds the server with `handler.New(schema)` and
  registers the HTTP transports explicitly (Options, GET, POST,
  MultipartForm), plus introspection, the error presenter and the recover
  function. A server with no transports rejects every request, so do not remove
  these lines. Websocket transports are left out because the API runs on Lambda
  over HTTP only.

**Note:** the summarized chats record the migration and its mechanics, but not
why GraphQL was chosen over extending the REST endpoints.

## Alternatives rejected

- Keep separate REST handlers: replaced by one GraphQL endpoint. The standalone
  `cmd/tides` and `cmd/stations` handlers remain but are not deployed by CI.
- Hand-written resolver methods next to generated ones: duplicate-declaration
  build errors.
- Editing generated files (for fixes or coverage): overwritten on the next
  generate.
- `handler.NewDefaultServer`: works, but is deprecated (staticcheck flags it)
  and adds websocket transports Lambda does not need.

## Platforms

backend (constrains web and firmware through the schema)

## Source

Summarized claude.ai project history: "Migrating API to GraphQL: Frontend and
Backend Changes" (2025-01-22), "Resolving GraphQL Code Generation Errors"
(2025-01-26), "Troubleshooting GraphQL code generation and resolver issues"
and "Debugging 400 "transport not supported" error in GraphQL API"
(2025-01-28).
