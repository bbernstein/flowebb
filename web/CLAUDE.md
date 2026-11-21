# web/: Next.js static site

Read the root `CLAUDE.md` first. Run every command below from `web/`.

## Stack

- Next.js 15 (`output: 'export'`, so it is a fully static site), React 18, TypeScript.
- Apollo Client for GraphQL, MUI for components, Leaflet for maps, Highcharts for charts.
- Jest + React Testing Library.
- Node >= 20.11 (CI uses Node 22).
- Hosting: S3 + CloudFront. There is no server, so no API routes, SSR, or server actions.

## Directory map

| Path | Contents |
|---|---|
| `src/app/` | App Router entry (`layout.tsx`, `page.tsx`, `providers.tsx`) |
| `src/components/` | `TideInfo`, `TideChart`, `Map`, `LocationButton`, `BuildInfo` |
| `src/context/` | `TideContext`, `StationContext`: GraphQL queries and state |
| `src/apollo/client.ts` | Apollo client setup |
| `src/config/environment.ts` | reads `NEXT_PUBLIC_API_URL` (defaults to `http://localhost:8080`) |
| `src/types/`, `src/utils/` | types; utilities with tests in `src/utils/__tests__/` |
| `scripts/deploy-frontend.sh` | build + S3 sync + CloudFront invalidation |

## Commands

```bash
npm ci              # install
npm run dev         # dev server on http://localhost:3000
npm run lint        # ESLint (next lint --dir src)
npm test            # Jest
npm run test:coverage
npm run build       # static export to out/
```

API URL: set `NEXT_PUBLIC_API_URL` in `.env.local` (gitignored). Use
`http://localhost:8080` for a local backend or `https://api.flowebb.com`
for production (the client appends `/graphql`).

Deploy: `.github/workflows/deploy-web.yml` (runs `scripts/deploy-frontend.sh prod`),
manually dispatched. Only dispatch it with the owner's go-ahead.

## Rules

- Coverage: CI does **not** enforce coverage yet. `jest.config.js` sets a
  70% threshold, which only applies when you run `npm run test:coverage`.
  New code still needs unit tests that cover it.
- Everything must work as a static export. `NEXT_PUBLIC_*` values are baked in at
  build time and visible to every visitor, so never put a secret in one.
- GraphQL queries live in `src/context/`. Only request fields that exist in
  `backend/graph/schema.graphql`. There is no codegen, so check the schema by hand.
  If you need a new field, that is a `backend/` change first (see the root protocol).
- Run `npm run lint` and `npm test` before reporting. Run `npm run build` when
  you change config, routing, or imports.

## Never commit secrets or private data

The root rule applies in full. Here: never commit `.env.local` or any other
`.env.*` file (only `*.example`), and never commit deploy bucket names or
distribution IDs. Those come from GitHub secrets.

## Decisions

Check [`docs/decisions/`](../docs/decisions/README.md) before changing behavior here.

Key decisions:

- [0001: Static site on S3 and CloudFront, API on API Gateway and Lambda](../docs/decisions/0001-static-site-and-api-on-separate-domains.md)
- [0011: The web app shows every time in the station's local zone](../docs/decisions/0011-web-displays-station-local-time.md)
- [0017: Remember the last location and station in browser storage](../docs/decisions/0017-persist-last-location-and-station.md)
- [0029: Picking a location always selects the nearest station](../docs/decisions/0029-auto-select-nearest-station.md)
- [0007: Map waits for the stored location and fits bounds to the click plus stations](../docs/decisions/0007-map-loading-and-bounds.md)
- [0012: Highcharts for the tide chart; accessibility module currently disabled](../docs/decisions/0012-highcharts-tide-chart-accessibility.md)
- [0006: Use Material UI for web components, not Tailwind or shadcn/ui](../docs/decisions/0006-mui-component-library.md)
