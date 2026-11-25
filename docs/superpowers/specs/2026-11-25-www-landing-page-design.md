# www.flowebb.com landing page — design

**Date:** 2026-11-25
**Status:** Approved 2026-11-25
**Platforms:** new `www/`, `infra/`, `.github/`, `scripts/`, `docs/`

## Intent

The tide app lives at `app.flowebb.com`. Neither `www.flowebb.com` nor the bare
`flowebb.com` resolve today. We want a simple front door at `www.flowebb.com`
for people who search for "tide times near me" or similar: a clean, clear page
that explains what FlowEbb does and how it works, with an obvious link to
the app.

FlowEbb is free and has no ads. The page has no commercial purpose; its only
job is to get someone looking for their local tide to the app quickly, and to
be found by search engines while doing it.

## Goals

- `https://www.flowebb.com/` serves one static landing page.
- `https://flowebb.com/` (and any path on it) 301-redirects to
  `https://www.flowebb.com/` + same path, so search engines index one host.
- The page has search-friendly text describing what the app does and how to
  use it, and a prominent "See your local tide" link to `https://app.flowebb.com/`.
- The page is fast: plain HTML and CSS, no framework, no JavaScript required.
- Merges to `main` that touch `www/` deploy the page automatically, like the
  other platforms.

## Non-goals

- Anything about the LED device, firmware, or the project's internals.
- Changing the app at `app.flowebb.com`, or renaming `web/` (considered and
  deferred; a possible later `web/` → `apps/web/` move is a separate PR).
- Analytics, cookies, forms, sign-up, a blog, or multiple pages.

## Anti-goals (would be a failure even if it works)

- Ads, trackers, third-party scripts, or anything that feels like marketing
  for money.
- Claims about the app that aren't true. Every feature named on the page must
  match what `web/` actually does today (see "Content" below).
- A change that can break or slow down `app.flowebb.com` or `api.flowebb.com`.

## Constraints

- Terraform is the only way AWS resources change, and only via
  `deploy-infra.yml` on merge to `main` (`infra/CLAUDE.md`). Agents never apply.
- The existing ACM certificate already covers `flowebb.com` and
  `*.flowebb.com` (`infra/terraform/modules/base_networking/main.tf`), so no
  certificate change is needed. Use `module.base_networking.certificate_arn`.
- **The CI role can't yet create what this needs.** The role's policy in
  `infra/terraform/modules/github_actions/main.tf` scopes CloudFront actions to
  the one existing distribution ARN, has no CloudFront Function or Origin
  Access Control permissions, and scopes S3 to known bucket names. In the prod
  root, `module.github_actions` is applied *after* `module.edge_networking`, so
  widening the policy in the same apply that creates the new distribution
  would fail. Hence two PRs (see "Delivery order").
- Repo is public: no account IDs, distribution IDs, or ARNs in commits or
  public logs. New IDs go in GitHub secrets and are masked in workflow logs,
  as `deploy-infra.yml` already does for the existing distribution.
- Every code change needs tests; lint and tests run in CI (`verify.yml`), and
  `ci-gate` must include the new jobs.
- New top-level platform: follow `docs/adding-a-platform.md` (README,
  CLAUDE.md with the secrets heading, `.claude/agents/www.md`, routing table,
  CI filter + jobs, `PLATFORMS` in `scripts/check-repo-rules.sh` and its test,
  `docs/architecture.md`, `flowebb.code-workspace`, decision record). The
  checklist says `apps/<name>/`; `www/` lives at the top level like `web/`.

## Approaches considered

1. **Separate static site — chosen.** Hand-written HTML/CSS in `www/`, its own
   S3 bucket and CloudFront distribution for `www.flowebb.com` + `flowebb.com`,
   an apex→www redirect, and its own deploy workflow. Fastest page, plain HTML
   is ideal for search, and it is fully isolated from the app. Cost: a second
   distribution, workflow, and a bit more Terraform.
2. **A route inside the Next.js app.** Reuses the app's look and deploy, but
   ships the app's JS bundle on a text page and needs a host-rewrite on the
   shared distribution, coupling the two sites.
3. **Plain HTML in the app's bucket.** Still needs the rewrite, and the app's
   `aws s3 sync --delete` deploy would wipe the landing page. Rejected.

The owner chose approach 1, and chose the directory name `www/` (matching the
domain it serves), leaving `web/` as is.

## Design

### `www/` directory

```
www/
  site/                 # exactly what gets uploaded to S3
    index.html
    404.html
    styles.css
    logo.svg            # copy of web/public/logo-animated.svg
    favicon.ico         # copy of web/src/app/favicon.ico
    robots.txt          # allow all; Sitemap: https://www.flowebb.com/sitemap.xml
    sitemap.xml         # one URL: https://www.flowebb.com/
  test/                 # node:test checks of the files in site/
  package.json          # lint + test scripts, devDependencies only
  .gitignore            # node_modules/
  README.md
  CLAUDE.md
```

No build step: `site/` is deployed as is.

### The page

Layout (one column, centered, mobile-first; matches the app's blue wordmark):

```
            [ flow/ebb animated logo ]

     Free local tide times and tide charts
  See today's high and low tides for the nearest
     NOAA tide station — no ads, no sign-up.

          [  See your local tide  →  ]        ← links to https://app.flowebb.com/

  What you get
   • Today's tide chart with a live "now" marker
   • Times and heights of the next high and low tides
   • Times shown in the station's local time zone
   • Data from NOAA tide predictions for U.S. coastal stations

  How it works
   1. Open the app and tap "use my location" — or click the map.
   2. FlowEbb picks the nearest NOAA tide station.
   3. See the current tide and today's chart. It remembers
      your spot for next time.

  Common questions  (short FAQ: Is it free? Where does the data come
  from? Which places are covered? Does it work on my phone?)

  [ See your local tide → ]

  footer: Tide predictions from NOAA CO-OPS · Free, no ads
```

The words above are a sketch; the builder writes the final copy within these
rules:

- **Content must be true of the current app.** Before writing it, confirm
  each feature in `web/src/` (e.g. `components/LocationButton.tsx`,
  `components/Map.tsx`, `components/TideChart.tsx`, `components/TideInfo.tsx`,
  and decisions 0011, 0017, 0023, 0029). Drop any line that isn't true.
- Don't promise privacy specifics (e.g. "your location never leaves your
  device") unless the code proves it.
- Plain, friendly language; one `<h1>`; real `<h2>` sections; the call to
  action appears at least twice (top and bottom).

### SEO

- `<title>` and `<meta name="description">` built around "local tide times",
  "tide chart", "high and low tide" (builder writes the wording).
- `<link rel="canonical" href="https://www.flowebb.com/">`, `lang="en"`,
  viewport meta, Open Graph and Twitter card tags (no image required).
- JSON-LD: a `WebApplication` (name FlowEbb, url `https://app.flowebb.com/`,
  `applicationCategory` "WeatherApplication" or similar, free `offers` with
  price 0) and an `FAQPage` matching the visible FAQ.
- `robots.txt` and `sitemap.xml` as above. `404.html` links home and to the app.
- The app domain is not changed (no noindex etc.); out of scope.

### Tests and lint (`www/`)

- Lint: `html-validate` on `site/*.html` (pinned devDependency).
- Tests (`node --test`, no other deps): index.html has exactly one `<h1>`, a
  non-empty title and meta description, canonical = `https://www.flowebb.com/`,
  at least one link to `https://app.flowebb.com/`, valid JSON in every
  `application/ld+json` block, no `<script src=...>` pointing off-site, and
  every local file referenced (`styles.css`, `logo.svg`, favicon) exists in
  `site/`. robots.txt names the sitemap; sitemap.xml lists the canonical URL.

### Infra (`infra/terraform/`)

New module `modules/www_site/`, called from `environments/prod/main.tf`:

- S3 bucket named after the www domain (`www.flowebb.com`), versioning on,
  public access blocked.
- CloudFront distribution with aliases `www.flowebb.com` and `flowebb.com`,
  the existing certificate, `default_root_object = "index.html"`,
  `redirect-to-https`, compression on, custom error response 403/404 →
  `/404.html` with status 404, and logging to the existing CloudFront logs
  bucket under a `www/` prefix.
- Origin access: builder's call between Origin Access Control (current AWS
  recommendation) and an OAI (matches the existing distribution). The IAM
  change in PR 1 must grant whichever is chosen.
- A CloudFront Function (viewer-request) that 301s any request whose `Host` is
  `flowebb.com` to `https://www.flowebb.com` + URI + query string.
- Route53 alias A and AAAA records for `www.flowebb.com` and `flowebb.com` →
  the new distribution, in the existing zone.
- New variable `www_domain` (default `"www.flowebb.com"`); apex comes from
  `domain_name`. Add it to `terraform.tfvars.example`.
- Outputs: the bucket name and distribution ID (for the deploy secrets).

### Delivery order

1. **PR 1 — CI role permissions (infra only).** Extend the
   `github_actions` module policy so `deploy-infra` can create and manage:
   a CloudFront distribution other than the existing one, CloudFront
   Functions, the chosen origin-access resource, the `www.flowebb.com` S3
   bucket (create/configure, plus object put/delete/list for deploys), and
   invalidations on the new distribution. Scope as narrowly as AWS allows
   (CloudFront distribution IDs aren't known in advance, so distribution
   actions will likely need `distribution/*`). Owner reviews the plan and
   merges with admin bypass; `deploy-infra` applies.
2. **PR 2 — the site.** Everything else: `www/`, `modules/www_site/`, the
   deploy workflow, CI jobs, docs. Owner reviews the plan and merges with
   admin bypass; `deploy-infra` creates the resources.
3. **Owner, once:** add GitHub secrets `WWW_BUCKET_NAME` and
   `WWW_CLOUDFRONT_DISTRIBUTION_ID` from the Terraform outputs, then dispatch
   `deploy-www.yml` once from `main`. The apply log does not print outputs
   (by design, to keep IDs out of public logs), so read them locally with
   `terraform output` from `infra/terraform/environments/prod/`.

### Deploy workflow `.github/workflows/deploy-www.yml`

- Triggers: push to `main` touching `www/**` or itself; `workflow_dispatch`.
- OIDC role via `secrets.AWS_ROLE_ARN` (same as the others); mask the
  account ID and the new distribution ID.
- If either `WWW_*` secret is missing, print a notice and exit successfully
  (so merging PR 2 before step 3 doesn't produce a red run).
- `aws s3 sync www/site/ s3://$WWW_BUCKET_NAME --delete`, HTML/txt/xml with
  `max-age=0, must-revalidate`, other assets with a short cache (e.g. 1 day)
  since file names aren't hashed. Then invalidate `/*` and wait.

### CI (`verify.yml`) and repo rules

- `changes` job: add `www` output with filter `www/**`.
- `www-test` job (npm ci, lint, test in `www/`), gated like `web-test`, and
  added to `ci-gate` needs.
- `scripts/check-repo-rules.sh` and its test: add `www` to `PLATFORMS`.

### Docs

- Root `CLAUDE.md`: routing-table row and commands row for `www/`; note the
  deploy workflow.
- `www/CLAUDE.md`, `www/README.md`, `.claude/agents/www.md`.
- `docs/architecture.md`: components table and data flow.
- `flowebb.code-workspace`: add the folder.
- New decision record `docs/decisions/0035-www-landing-site.md`: separate
  static site and distribution, `www/` naming, apex→www redirect, two-PR
  rollout.

## Left to the builder

- Final copy, within the content rules above.
- Visual styling details (colors taken from the app's logo gradient, font
  stack, spacing), keeping it plain and fast.
- OAC vs OAI, and exact IAM action lists.
- Exact cache-control values.

## Verification after rollout

- `curl -sI https://www.flowebb.com/` → 200; `curl -sI https://flowebb.com/x`
  → 301 to `https://www.flowebb.com/x`; the CTA reaches `app.flowebb.com`.
- `app.flowebb.com` and `api.flowebb.com/graphql` still work.
- Lighthouse SEO and performance scores on the live page (target 95+).
