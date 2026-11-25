# www.flowebb.com Landing Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve a fast, search-friendly static landing page at `https://www.flowebb.com/` (with `flowebb.com` 301-redirecting to it) that explains the tide app and links to `https://app.flowebb.com/`.

**Architecture:** Hand-written HTML/CSS in a new top-level `www/` platform, deployed by `aws s3 sync` to its own S3 bucket behind its own CloudFront distribution (OAC origin, CloudFront Function for the apex redirect). Terraform module `infra/terraform/modules/www_site/`. Shipped as two PRs because the CI deploy role must be widened before it can create a second distribution.

**Tech Stack:** HTML5/CSS, Node 22 (`node:test`, `html-validate`), bash, Terraform 1.5 + AWS provider ~> 5.0, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-25-www-landing-page-design.md`

## Global Constraints

- Canonical URL: `https://www.flowebb.com/`. App URL: `https://app.flowebb.com/`. Apex: `flowebb.com`.
- No JavaScript on the page other than `application/ld+json` blocks; no third-party scripts, trackers, cookies, ads, fonts, or CDNs.
- No mention of the LED device, firmware, or project internals on the page.
- Every feature claimed on the page must be true of `web/src/` today. No privacy promises the code doesn't prove.
- Never `terraform apply` locally; `plan`, `fmt`, `validate`, `tflint` only.
- No account IDs, distribution IDs, or ARNs in commits or public logs; mask new IDs in workflows.
- Never commit to `main`. Commit messages end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01HTgPMKMqEqg6N61hshM2vF
  ```
- Origin access uses **Origin Access Control (OAC)**, not OAI (decided here to keep Task 1 and Task 4 consistent).
- Bucket name = `var.www_domain` = `"www.flowebb.com"`. CloudFront Function name = `"${var.project_name}-apex-redirect-${var.environment}"`.

## Review Focus

1. Apex request with a path and query string (`https://flowebb.com/faq?x=1`) → 301 to `https://www.flowebb.com/faq?x=1`, not to `/` and not dropping the query. Test: Task 4 function unit test.
2. A missing page on www (`/nope`) → the 404 page with HTTP status 404 (S3 with OAC returns 403 for missing keys, so both 403 and 404 must map). Test: Task 4 `terraform validate` + post-deploy curl in Task 7.
3. `deploy-www` running before the `WWW_*` secrets exist (merge of PR 2) → green run with a notice, no AWS calls. Test: Task 5 deploy script test.
4. A page edit that references a file not in `site/` (e.g. renamed CSS) → test failure, not a broken live page. Test: Task 2 local-references test.
5. FAQ text edited on the page but not in the JSON-LD (or vice versa) → test failure, so structured data never disagrees with visible content. Test: Task 3 FAQ consistency test.

---

## PR 1 — CI role permissions (branch `feat/www-landing-page`, already holds the spec and this plan)

### Task 1: Widen the CI deploy role for the www site

**Files:**
- Modify: `infra/terraform/modules/github_actions/main.tf` (S3 resource list; CloudFront statement; new statements)
- Modify: `infra/terraform/modules/github_actions/variables.tf` (add `www_bucket_name`)
- Modify: `infra/terraform/environments/prod/variables.tf` (add `www_domain`)
- Modify: `infra/terraform/environments/prod/main.tf` (pass `www_bucket_name = var.www_domain` to `module.github_actions`)
- Modify: `infra/terraform/environments/prod/terraform.tfvars.example` (add `www_domain = "www.example.com"`)

**Interfaces:**
- Produces: prod variable `www_domain` (string, default `"www.flowebb.com"`), used by Task 4.

- [x] **Step 1:** Add variable `www_domain` (description "Marketing landing page domain", default `"www.flowebb.com"`) to prod `variables.tf`, and `www_bucket_name` (string, no default) to the github_actions module.
- [x] **Step 2:** In the S3 statement's `Resource`, add `"arn:aws:s3:::${var.www_bucket_name}"` and `"arn:aws:s3:::${var.www_bucket_name}/*"`.
- [x] **Step 3:** In the CloudFront statement, change the distribution resource to `"arn:aws:cloudfront::${account}:distribution/*"` (new distribution IDs can't be known in advance), and add `cloudfront:GetDistributionConfig` to its actions. Keep the OAI resource line.
- [x] **Step 4:** Add a statement for OAC: actions `cloudfront:CreateOriginAccessControl`, `GetOriginAccessControl`, `GetOriginAccessControlConfig`, `UpdateOriginAccessControl`, `DeleteOriginAccessControl`, `ListOriginAccessControls`; resource `"arn:aws:cloudfront::${account}:origin-access-control/*"`.
- [x] **Step 5:** Add a statement for CloudFront Functions: actions `cloudfront:CreateFunction`, `DescribeFunction`, `GetFunction`, `UpdateFunction`, `PublishFunction`, `DeleteFunction`, `TestFunction`; resource `"arn:aws:cloudfront::${account}:function/${var.project_name}-*"`.
- [x] **Step 6:** Pass `www_bucket_name = var.www_domain` in the prod `module "github_actions"` block; add `www_domain` to `terraform.tfvars.example`.
- Note (implementation): three additions beyond the original steps. (1) Removed the unused module variable `cloudfront_distribution_id` (tflint unused-declaration after widening to `distribution/*`). (2) Added `cloudfront:ListCachePolicies`/`GetCachePolicy` (for Task 4's `Managed-CachingOptimized` data source) and moved `ListOriginAccessControls` into the same `"*"`-scoped statement, since it has no resource-level scoping. (3) Added `route53:GetChange` to the Route53 statement, which `aws_route53_record` polls for INSYNC.
- [x] **Step 7: Verify** — from `infra/terraform`: `terraform fmt -check -recursive` (no output), `tflint --init && tflint --recursive` (no issues); from `environments/prod`: `terraform init && terraform validate` (Success). If AWS credentials are available, `terraform plan` shows only `module.github_actions.aws_iam_role_policy.github_actions` changing (`Plan: 0 to add, 1 to change, 0 to destroy`). Never print the plan's ARNs into chat or files.
- [ ] **Step 8: Commit** `infra: let CI deploy role manage the www landing site` (body explains why distribution scope widened and why this must apply before PR 2). Push and open PR 1. `infra-plan` will fail by design; stop and ask the owner to review and merge with admin bypass.

---

## PR 2 — the site (new branch `feat/www-site` from `main` after PR 1 is merged and applied)

### Task 2: `www/` platform scaffold, test suite, and SEO skeleton page

**Files:**
- Create: `www/package.json`, `www/package-lock.json`, `www/.gitignore` (`node_modules/`), `www/.htmlvalidate.json` (extends `html-validate:recommended`)
- Create: `www/README.md`, `www/CLAUDE.md` (stack, directory map, commands, rules, the exact heading `## Never commit secrets or private data`, "API usage: none — links to the app only")
- Create: `www/site/index.html`, `www/site/404.html`, `www/site/styles.css`, `www/site/robots.txt`, `www/site/sitemap.xml`
- Create: `www/site/logo.svg` (copy of `web/public/logo-animated.svg`), `www/site/favicon.ico` (copy of `web/src/app/favicon.ico`)
- Test: `www/test/site.test.mjs`

**Interfaces:**
- Produces: `npm run lint` (`html-validate site/*.html`), `npm test` (`node --test test/`), run from `www/`. Task 3 extends `index.html` and the test file; Task 6 calls these scripts from CI.

- [ ] **Step 1: Write the failing tests** in `www/test/site.test.mjs` using `node:test` + `node:assert/strict`, reading files from `../site/` (resolve via `import.meta.url`). Parse with regexes; no extra deps. Tests:
  - `index has exactly one h1` — count of `<h1` === 1.
  - `index has a title and meta description` — `<title>` text length 10–70; `meta name="description"` content length 50–160.
  - `index canonical is www` — `<link rel="canonical" href="https://www.flowebb.com/">`.
  - `index links to the app` — at least 2 `href="https://app.flowebb.com/"`.
  - `index has lang and viewport` — `<html lang="en"`, `meta name="viewport"`.
  - `index has Open Graph tags` — `og:title`, `og:description`, `og:url` = `https://www.flowebb.com/`, `og:type`.
  - `json-ld blocks parse` — every `<script type="application/ld+json">` body `JSON.parse`s; one has `@type` `WebApplication` with `url` `https://app.flowebb.com/` and `offers.price` `"0"` or `0`.
  - `no external or inline scripts` — every `<script` in every `site/*.html` has `type="application/ld+json"`.
  - `local references exist` — every `href`/`src` in `site/*.html` that is not `http(s):`, `mailto:` or `#…` resolves to a file in `site/`.
  - `404 links home and to app` — `404.html` contains `href="https://www.flowebb.com/"` (or `href="/"`) and `href="https://app.flowebb.com/"`, and `meta name="robots" content="noindex"`.
  - `robots names sitemap` — `robots.txt` contains `Sitemap: https://www.flowebb.com/sitemap.xml` and no `Disallow: /` line.
  - `sitemap lists canonical` — `sitemap.xml` contains `<loc>https://www.flowebb.com/</loc>`.
- [ ] **Step 2:** Create `package.json` (`"private": true`, `"type": "module"`, scripts `lint` and `test` above, devDependency `html-validate` pinned exact latest), run `npm install`, then `npm test`. Expected: FAIL (site files missing).
- [ ] **Step 3:** Create the `site/` files: `index.html` with the full `<head>` (title, description, canonical, viewport, OG + Twitter card, favicon, `styles.css`, WebApplication JSON-LD), logo, `<h1>`, one-line lead, and the CTA linking to the app at top and bottom; `404.html`; `robots.txt`; `sitemap.xml`; copied `logo.svg` and `favicon.ico`; minimal `styles.css`. Copy for sections comes in Task 3.
- [ ] **Step 4: Verify** — `npm run lint` (0 errors) and `npm test` (all pass).
- [ ] **Step 5: Commit** `www: add landing page platform with SEO checks`.

### Task 3: Page copy, FAQ, and styling

**Files:**
- Modify: `www/site/index.html`, `www/site/styles.css`
- Test: `www/test/site.test.mjs`

**Interfaces:**
- Consumes: Task 2's page skeleton and test helpers.

- [ ] **Step 1: Confirm the facts.** Read `web/src/app/page.tsx`, `web/src/components/{LocationButton,Map,TideChart,TideInfo}.tsx`, and decisions 0011, 0017, 0023, 0029. List the features to claim (expected: use-my-location button, click map to choose a spot, nearest NOAA station auto-selected, today's chart with live "now" marker, next high/low times, station local time, remembers last location/station). Drop anything not confirmed. Record the confirmed list in the commit message body.
- [ ] **Step 2: Write the failing tests:**
  - `index has the expected sections` — `<h2>` headings include text matching `/what you get/i`, `/how it works/i`, `/questions/i`.
  - `faq json-ld matches visible faq` — a `FAQPage` JSON-LD block exists; its `mainEntity[].name` list equals, in order, the text of the FAQ `<h3>` elements inside the element with `id="faq"`, and each `acceptedAnswer.text` equals the following answer paragraph's text (tags stripped, whitespace collapsed).
  - `page does not mention the device` — `index.html` text contains none of `/led|esp32|firmware|neopixel/i`.
- [ ] **Step 3: Run** `npm test` → the three new tests FAIL.
- [ ] **Step 4:** Write the sections per the spec sketch ("What you get", "How it works" as an `<ol>`, FAQ with questions: Is FlowEbb free? / Where does the tide data come from? / Which places are covered? / Does it work on my phone?), footer "Tide predictions from NOAA CO-OPS · Free, no ads", and the FAQPage JSON-LD. Style: single centered column, max-width ~42rem, system font stack, blue accents from the logo gradient, a large high-contrast CTA button, readable on mobile and in dark mode (`prefers-color-scheme`). Title/description built around "local tide times", "tide chart", "high and low tide".
- [ ] **Step 5: Verify** — `npm run lint` and `npm test` all pass; open `site/index.html` in a browser at 375px and 1280px widths and confirm layout and both CTAs.
- [ ] **Step 6: Commit** `www: write landing page copy, FAQ, and styles`.

### Task 4: Terraform module `www_site`

**Files:**
- Create: `infra/terraform/modules/www_site/{main.tf,variables.tf,outputs.tf,versions.tf}`
- Create: `infra/terraform/modules/www_site/apex-redirect.js` (CloudFront Functions JS 2.0 runtime)
- Create: `infra/terraform/modules/www_site/apex-redirect.test.mjs` (`node --test`)
- Modify: `infra/terraform/environments/prod/main.tf` (module call + outputs)

**Interfaces:**
- Consumes: `var.www_domain` (Task 1), `module.base_networking.certificate_arn`, `module.base_networking.route53_zone_id`, `module.storage.cloudfront_logs_bucket`.
- Produces: module inputs `www_domain`, `apex_domain`, `project_name`, `environment`, `certificate_arn`, `route53_zone_id`, `cloudfront_logs_bucket`; outputs `bucket_name`, `distribution_id`; prod root output `www_deployment_details = { bucket_name, cloudfront_distribution_id }`.

- [ ] **Step 1: Write the failing function test** `apex-redirect.test.mjs`. Load `apex-redirect.js` source, evaluate it with `new Function(src + '; return handler;')()`, and call `handler(event)` with CloudFront event shapes (`request.headers.host.value`, `request.uri`, `request.querystring` as `{ key: { value } }`, including multi-value `multiValue` arrays). Tests:
  - `apex root redirects` — host `flowebb.com`, uri `/` → `statusCode` 301, `headers.location.value` `https://www.flowebb.com/`.
  - `apex keeps path and query` — uri `/faq`, querystring `{ x: { value: '1' }, y: { value: 'a b' } }` → location `https://www.flowebb.com/faq?x=1&y=a%20b`.
  - `www passes through` — host `www.flowebb.com` → returns the request object unchanged.
- [ ] **Step 2: Run** `node --test infra/terraform/modules/www_site/` → FAIL (file missing).
- [ ] **Step 3:** Implement `function handler(event)` in `apex-redirect.js`: if host equals the apex, return a 301 with `location` = `https://www.flowebb.com` + uri + encoded query string (handle `multiValue`); otherwise return `event.request`. Hard-code `www.flowebb.com`/`flowebb.com` as string constants in the file (the function has no variables); Terraform reads it with `file()`.
- [ ] **Step 4: Run** the test → PASS.
- [ ] **Step 5:** Write the module (`versions.tf` like `edge_networking/versions.tf` without the alias, since the certificate ARN is passed in):
  - `aws_s3_bucket` (name `var.www_domain`), versioning Enabled, public access block all true.
  - `aws_cloudfront_origin_access_control` (`s3`, `always`, `sigv4`).
  - `aws_cloudfront_function` (`cloudfront-js-2.0`, `publish = true`, code `file("${path.module}/apex-redirect.js")`, name `"${var.project_name}-apex-redirect-${var.environment}"`).
  - `aws_cloudfront_distribution`: aliases `[var.www_domain, var.apex_domain]`, IPv6, `default_root_object = "index.html"`, S3 origin via OAC, default behavior GET/HEAD, `redirect-to-https`, `compress = true`, managed `CachingOptimized` cache policy (data source `aws_cloudfront_cache_policy` name `Managed-CachingOptimized`), function association `viewer-request`; `custom_error_response` for 403 and 404 → `/404.html`, `response_code = 404`; logging to `"${var.cloudfront_logs_bucket}.s3.amazonaws.com"` prefix `www/`; cert `sni-only`, `minimum_protocol_version = "TLSv1.2_2021"`; no geo restriction.
  - `aws_s3_bucket_policy`: allow `s3:GetObject` on `bucket/*` to principal service `cloudfront.amazonaws.com` with condition `AWS:SourceArn` = the distribution ARN.
  - `aws_route53_record` A and AAAA aliases for both `var.www_domain` and `var.apex_domain` (use `for_each` over the two names × two types).
  - Caution: any additional CloudFront data source (response-headers or origin-request policy lookups) needs new CI role permissions. Avoid them or hard-code managed IDs.
- [ ] **Step 6:** Call the module from prod `main.tf` (`apex_domain = var.domain_name`, `depends_on = [module.storage, module.base_networking]`) and add the `www_deployment_details` output.
- [ ] **Step 7: Verify** — `terraform fmt -check -recursive`, `tflint --recursive`, `terraform validate` from prod all clean. If credentials are available, `terraform plan` adds only `module.www_site.*` resources (bucket + config, OAC, function, distribution, bucket policy, 4 records) and changes nothing else.
- [ ] **Step 8: Commit** `infra: add www_site module for www.flowebb.com and apex redirect`.

### Task 5: Deploy script and workflow

**Files:**
- Create: `www/scripts/deploy.sh`
- Create: `www/scripts/deploy.test.sh`
- Create: `.github/workflows/deploy-www.yml`
- Modify: `www/package.json` (`test` script also runs `bash scripts/deploy.test.sh`)

**Interfaces:**
- Consumes: env `WWW_BUCKET_NAME`, `WWW_CLOUDFRONT_DISTRIBUTION_ID`; `aws` CLI on PATH.
- Produces: `bash www/scripts/deploy.sh` (run from `www/`), exit 0 on success or skip.

- [ ] **Step 1: Write the failing test** `deploy.test.sh` (style of `scripts/check-repo-rules.test.sh`): put a stub `aws` first on `PATH` that appends its argv to a log file and prints `I123` for `create-invalidation`. Cases:
  - `skips when bucket secret missing` — unset `WWW_BUCKET_NAME` → exit 0, output contains `skipping`, log empty.
  - `skips when distribution secret missing` — same for `WWW_CLOUDFRONT_DISTRIBUTION_ID`.
  - `syncs html/txt/xml with no-cache` — log has an `s3 sync site/ s3://B --delete` call with `--cache-control public, max-age=0, must-revalidate` including `*.html`, `*.txt`, `*.xml`.
  - `syncs assets with one-day cache` — log has an `s3 sync` with `public, max-age=86400` excluding `*.html`, `*.txt`, `*.xml`.
  - `invalidates and waits` — log has `cloudfront create-invalidation --distribution-id D --paths /*` then `cloudfront wait invalidation-completed --distribution-id D --id I123`.
  - `fails when aws fails` — stub exits 1 on `s3` → script exits non-zero.
- [ ] **Step 2: Run** `bash scripts/deploy.test.sh` from `www/` → FAIL.
- [ ] **Step 3:** Implement `deploy.sh` (`set -euo pipefail`; skip with `echo "WWW_* secrets not set; skipping deploy"` and exit 0; the two syncs, assets first so new HTML never references a missing file; invalidate; wait). Never echo the bucket name or distribution ID.
- [ ] **Step 4: Run** the test → PASS.
- [ ] **Step 5:** Write `deploy-www.yml`, modeled on `deploy-web.yml`: push to `main` on `www/**` and its own path, plus `workflow_dispatch`; `if: github.ref == 'refs/heads/main'`; `concurrency: deploy-www`; OIDC with `secrets.AWS_ROLE_ARN`; a mask step for the account ID and `WWW_CLOUDFRONT_DISTRIBUTION_ID` (and `WWW_BUCKET_NAME`); then run `bash scripts/deploy.sh` in `www/` with the two secrets as env. Skip the AWS credentials step when the secrets are empty (`if: env.WWW_BUCKET_NAME != ''` with secrets mapped to job env).
- [ ] **Step 6: Verify** — `npm test` in `www/` passes; `actionlint .github/workflows/deploy-www.yml` clean if installed (otherwise note it wasn't run).
- [ ] **Step 7: Commit** `ci: add deploy-www workflow and tested deploy script`.

### Task 6: CI, repo rules, and platform registration

**Files:**
- Modify: `.github/workflows/verify.yml` (`www` output + filter `www/**`; `www-test` job; also run the apex function test; add `www-test` to `ci-gate.needs`)
- Modify: `scripts/check-repo-rules.sh` (`PLATFORMS=(backend web www infra firmware)`)
- Modify: `scripts/check-repo-rules.test.sh` (`PLATFORMS` list and routing echo line include `www/`; fixture dirs include `www`)
- Modify: `CLAUDE.md` (routing table row; commands table row; deploy-workflows sentence mentions `deploy-www.yml`)
- Create: `.claude/agents/www.md` (copy of `web.md` adapted: scope `www/`, commands `npm run lint`, `npm test`)
- Modify: `flowebb.code-workspace` (add `www` folder)
- Modify: `docs/architecture.md` (components row "Landing page | `www/` | Static HTML in S3, own CloudFront distribution, `www.flowebb.com` (apex redirects)"; data-flow note; deploy list entry)
- Create: `docs/decisions/0035-www-landing-site.md` (separate static site and distribution; `www/` name; OAC; apex→www 301; two-PR rollout because CI role scope; link to spec); add it to `docs/decisions/README.md` if that file indexes records, and to the root `CLAUDE.md` key decisions list.

- [ ] **Step 1: Write the failing test change:** update `scripts/check-repo-rules.test.sh` fixtures to include `www`, then add `www` to `PLATFORMS` in `check-repo-rules.sh` only after running `bash scripts/check-repo-rules.test.sh` to see the fixture-driven expectation fail. (If the existing test passes unchanged with the new platform, ask the owner before editing any existing assertion.)
- [ ] **Step 2:** Make the `check-repo-rules.sh` change and the root `CLAUDE.md` routing row; run `bash scripts/check-repo-rules.test.sh` and `bash scripts/check-repo-rules.sh` → both pass (`Repo rules OK.`).
- [ ] **Step 3:** Add the `www-test` job to `verify.yml`: gated on `workflow_dispatch || www == 'true' || workflows == 'true'`, plus `infra == 'true'` (so the apex function test runs on infra changes); Node 22 with npm cache on `www/package-lock.json`; `npm ci`, `npm run lint`, `npm test` in `www/`; then `node --test infra/terraform/modules/www_site/` from the repo root. Add it to `ci-gate.needs`. Run `bash .github/scripts/ci-gate.test.sh` → passes.
- [ ] **Step 4:** Remaining docs: agent file, workspace, architecture, decision record, CLAUDE.md commands/decisions entries.
- [ ] **Step 5: Verify** — `bash scripts/check-repo-rules.sh`, `bash scripts/check-repo-rules.test.sh`, `bash .github/scripts/ci-gate.test.sh`, `npm run lint && npm test` in `www/`, Terraform fmt/tflint/validate all clean.
- [ ] **Step 6: Commit** `ci: register www platform in CI, repo rules, and docs`. Push `feat/www-site`, open PR 2. `infra-plan` fails by design: stop and ask the owner to review the plan summary and merge with admin bypass.

### Task 7: Owner rollout and live verification (after PR 2 is merged and `deploy-infra` succeeds)

- [ ] **Step 1 (owner):** From `infra/terraform/environments/prod/`, run `terraform output www_deployment_details` locally and add GitHub secrets `WWW_BUCKET_NAME` and `WWW_CLOUDFRONT_DISTRIBUTION_ID`. (Agents must not print these values.)
- [ ] **Step 2 (owner go-ahead required):** dispatch `deploy-www.yml` from `main`.
- [ ] **Step 3: Verify live:**
  - `curl -sI https://www.flowebb.com/` → `HTTP/2 200`
  - `curl -sI 'https://flowebb.com/faq?x=1'` → `301`, `location: https://www.flowebb.com/faq?x=1`
  - `curl -sI https://www.flowebb.com/nope` → `404`
  - `curl -sI https://app.flowebb.com/` → `200`; a GraphQL POST to `https://api.flowebb.com/graphql` still answers.
  - Lighthouse (SEO and Performance) on `https://www.flowebb.com/` ≥ 95; record the scores in the PR or a follow-up comment.
