import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const tf = readFileSync(fileURLToPath(new URL('./main.tf', import.meta.url)), 'utf8');
const blocks = [...tf.matchAll(/custom_error_response\s*\{([^}]*)\}/g)].map((m) => m[1]);

for (const code of [403, 404]) {
  test(`missing pages (${code}) are served as 404.html with a 404 status`, () => {
    const block = blocks.find((b) => new RegExp(`error_code\\s*=\\s*${code}\\b`).test(b));
    assert.ok(block, `custom_error_response for ${code} exists`);
    assert.match(block, /response_page_path\s*=\s*"\/404\.html"/);
    assert.match(block, /response_code\s*=\s*404\b/);
  });
}
