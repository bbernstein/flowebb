import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, existsSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const SITE_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "site");
const WWW = "https://www.flowebb.com/";
const APP = "https://app.flowebb.com/";

// --- shared helpers (Task 3 and later tests reuse these) ---
const readSite = (name) => readFileSync(path.join(SITE_DIR, name), "utf8");
const htmlPages = () => readdirSync(SITE_DIR).filter((f) => f.endsWith(".html"));
const count = (re, s) => (s.match(re) || []).length;
const jsonLdBlocks = (html) =>
  [...html.matchAll(/<script\b[^>]*type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/g)].map(
    (m) => m[1],
  );
const stripTags = (html) =>
  html
    .replace(/<script[\s\S]*?<\/script>/g, " ")
    .replace(/<style[\s\S]*?<\/style>/g, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/\s+/g, " ")
    .trim();
const metaContent = (html, attr, name) => {
  const m = html.match(new RegExp(`<meta\\s+${attr}="${name}"\\s+content="([^"]*)"`));
  return m ? m[1] : null;
};

test("index has exactly one h1", () => {
  assert.equal(count(/<h1[\s>]/g, readSite("index.html")), 1);
});

test("index has a title and meta description", () => {
  const html = readSite("index.html");
  const title = html.match(/<title>([\s\S]*?)<\/title>/)?.[1].trim() ?? "";
  assert.ok(title.length >= 10 && title.length <= 70, `title length ${title.length}`);
  const desc = metaContent(html, "name", "description") ?? "";
  assert.ok(desc.length >= 50 && desc.length <= 160, `description length ${desc.length}`);
});

test("index canonical is www", () => {
  assert.ok(readSite("index.html").includes(`<link rel="canonical" href="${WWW}">`));
});

test("index links to the app", () => {
  assert.ok(count(new RegExp(`href="${APP}"`, "g"), readSite("index.html")) >= 2);
});

test("index has lang and viewport", () => {
  const html = readSite("index.html");
  assert.match(html, /<html lang="en"/);
  assert.match(html, /<meta name="viewport"/);
});

test("index has Open Graph tags", () => {
  const html = readSite("index.html");
  assert.ok(metaContent(html, "property", "og:title"));
  assert.ok(metaContent(html, "property", "og:description"));
  assert.equal(metaContent(html, "property", "og:url"), WWW);
  assert.ok(metaContent(html, "property", "og:type"));
});

test("json-ld blocks parse", () => {
  const blocks = jsonLdBlocks(readSite("index.html")).map((b) => JSON.parse(b));
  assert.ok(blocks.length >= 1);
  const app = blocks.find((b) => b["@type"] === "WebApplication");
  assert.ok(app, "WebApplication block present");
  assert.equal(app.url, APP);
  assert.ok(["0", 0].includes(app.offers?.price));
});

test("no external or inline scripts", () => {
  for (const page of htmlPages()) {
    const tags = readSite(page).match(/<script\b[^>]*>/g) || [];
    for (const tag of tags) {
      assert.match(tag, /type="application\/ld\+json"/, `${page}: ${tag}`);
    }
  }
});

test("local references exist", () => {
  for (const page of htmlPages()) {
    for (const m of readSite(page).matchAll(/\b(?:href|src)=(["'])(.*?)\1/g)) {
      const ref = m[2];
      if (/^(https?:|mailto:|#)/.test(ref)) continue;
      const file = ref.replace(/^\//, "").split(/[?#]/)[0] || "index.html";
      assert.ok(existsSync(path.join(SITE_DIR, file)), `${page}: missing ${ref}`);
    }
  }
});

test("404 links home and to app", () => {
  const html = readSite("404.html");
  assert.ok(html.includes(`href="${WWW}"`) || html.includes('href="/"'));
  assert.ok(html.includes(`href="${APP}"`));
  assert.match(html, /<meta name="robots" content="noindex"/);
});

test("robots names sitemap", () => {
  const robots = readSite("robots.txt");
  assert.ok(robots.includes("Sitemap: https://www.flowebb.com/sitemap.xml"));
  assert.doesNotMatch(robots, /^\s*Disallow:\s*\/\s*$/m);
});

test("sitemap lists canonical", () => {
  assert.ok(readSite("sitemap.xml").includes(`<loc>${WWW}</loc>`));
});

test("index has the expected sections", () => {
  const h2s = [...readSite("index.html").matchAll(/<h2[^>]*>([\s\S]*?)<\/h2>/g)].map((m) => stripTags(m[1]));
  for (const re of [/what you get/i, /how it works/i, /questions/i]) {
    assert.ok(h2s.some((t) => re.test(t)), `missing h2 matching ${re}: ${h2s.join(" | ")}`);
  }
});

test("faq json-ld matches visible faq", () => {
  const html = readSite("index.html");
  const faq = jsonLdBlocks(html)
    .map((b) => JSON.parse(b))
    .find((b) => b["@type"] === "FAQPage");
  assert.ok(faq, "FAQPage block present");
  const section = html.match(/<(\w+)\b[^>]*\bid="faq"[^>]*>([\s\S]*?)<\/\1>/)?.[2] ?? "";
  const items = [...section.matchAll(/<h3[^>]*>([\s\S]*?)<\/h3>\s*<p[^>]*>([\s\S]*?)<\/p>/g)].map((m) => ({
    q: stripTags(m[1]),
    a: stripTags(m[2]),
  }));
  assert.ok(items.length >= 4, `visible faq items: ${items.length}`);
  assert.deepEqual(
    faq.mainEntity.map((e) => e.name),
    items.map((i) => i.q),
  );
  assert.deepEqual(
    faq.mainEntity.map((e) => e.acceptedAnswer.text),
    items.map((i) => i.a),
  );
});

test("page does not mention the device", () => {
  assert.doesNotMatch(stripTags(readSite("index.html")), /\b(led|esp32|firmware|neopixel)\b/i);
});

export { readSite, jsonLdBlocks, stripTags, metaContent };
