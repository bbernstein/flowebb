# 0035: The www landing page is its own static site and CloudFront distribution

**Status:** Accepted

**Date:** 2026-11-25

## Context

The product needed a public marketing front door at `www.flowebb.com` for
search and sharing, separate from the tide app. The Next.js app
([0001](0001-static-site-and-api-on-separate-domains.md)) is a client of the
API and ships with a heavy map and chart bundle that a landing page does not
need. The CI deploy role ([0005](0005-github-actions-uses-oidc-role.md)) could
only manage the existing resources.

## Decision

- The landing page lives in `www/` as hand-written HTML and CSS with no
  framework, build step, or client JavaScript. It makes no API calls and only
  links to the app. The directory is named `www` to match the hostname it
  serves.
- It is hosted in its own private S3 bucket behind its own CloudFront
  distribution (Terraform module `infra/terraform/modules/www_site/`), reached
  only through origin access control (OAC). It does not share the web app's
  bucket or distribution, so a landing-page change cannot affect the app.
- The apex domain redirects to `www` with a 301, done by a small CloudFront
  Function (`apex-redirect.js`, unit-tested with `node --test`).
- Deploys run through `.github/workflows/deploy-www.yml` on push to `main`
  under `www/**`. CI runs `www-test` in `verify.yml` (lint, tests, and the
  apex function test).
- Rollout was two PRs because the CI role's scope had to widen before CI could
  manage the new resources. PR 1 widened the role (infra only). PR 2 added the
  site, the module, the workflow, and the CI and docs registration. As with
  any infra change, the owner reviews the plan and merges PR 2 with admin
  bypass.

## Alternatives rejected

- A route inside the Next.js app: couples marketing copy to the app's build
  and deploy, and adds weight to a page meant to load fast for SEO.
- Reusing the app's bucket and distribution: one mistake could take the app
  down, and cache and redirect rules would have to be shared.
- A static-site framework: more tooling for a single page.

## Platforms

www, infra

## Source

Design spec: `docs/superpowers/specs/2026-11-25-www-landing-page-design.md`.
