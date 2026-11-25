# www/: static landing page

Read the root `CLAUDE.md` first. Run every command below from `www/`.

Serves https://www.flowebb.com/ (a marketing/SEO front door that links to the
app at https://app.flowebb.com/). API usage: none, it links to the app only.

## Stack

- Hand-written HTML and CSS. No framework, no build step, no JavaScript.
- `html-validate` (pinned) for lint, `node:test` for tests.
- Node >= 20 (CI uses Node 22).
- Hosting: S3 + CloudFront (Terraform and deploy workflow come in later tasks).

## Directory map

| Path | Contents |
|---|---|
| `site/` | Exactly what is deployed: `index.html`, `404.html`, `styles.css`, `logo.svg`, `favicon.ico`, `robots.txt`, `sitemap.xml` |
| `test/site.test.mjs` | Checks of the files in `site/` |
| `.htmlvalidate.json` | html-validate config (`html-validate:recommended`) |

## Commands

```bash
npm ci
npm run lint    # html-validate site/*.html
npm test        # node --test test/
```

## Rules

- No JavaScript except `application/ld+json` blocks. No third-party scripts,
  trackers, cookies, ads, fonts, or CDNs. Everything is served from `site/`.
- Canonical URL is `https://www.flowebb.com/`; the app is `https://app.flowebb.com/`.
- Every feature claimed on the page must be true of `web/src/` today. Do not
  make privacy promises the code does not prove.
- Do not mention the LED device, firmware, or project internals.
- Keep JSON-LD (`FAQPage`) in sync with the visible FAQ.
- Run `npm run lint` and `npm test` before reporting.

## Never commit secrets or private data

The root rule applies in full. Here: never commit the S3 bucket name or
CloudFront distribution ID (they come from GitHub secrets), and no personal
info (names, emails) in page content.

## Decisions

Check [`docs/decisions/`](../docs/decisions/README.md) before changing behavior here.
