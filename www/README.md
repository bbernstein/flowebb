# www

Static landing page for https://www.flowebb.com/. Plain HTML and CSS, no build
step, no JavaScript. It explains FlowEbb and links to the app at
https://app.flowebb.com/.

```bash
npm ci
npm run lint   # html-validate on site/*.html
npm test       # node:test checks of site/ (SEO tags, links, JSON-LD, robots, sitemap), then scripts/deploy.test.sh
```

`site/` is exactly what gets uploaded to S3. See `CLAUDE.md` for rules.
