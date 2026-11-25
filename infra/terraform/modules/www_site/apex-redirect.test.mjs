import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const src = readFileSync(fileURLToPath(new URL('./apex-redirect.js', import.meta.url)), 'utf8');
const handler = new Function(src + '; return handler;')();

function event(host, uri, querystring) {
  return {
    request: {
      method: 'GET',
      headers: { host: { value: host } },
      uri,
      querystring: querystring || {},
    },
  };
}

test('apex root redirects', () => {
  const res = handler(event('flowebb.com', '/'));
  assert.equal(res.statusCode, 301);
  assert.equal(res.headers.location.value, 'https://www.flowebb.com/');
});

test('apex keeps path and query', () => {
  const res = handler(event('flowebb.com', '/faq', { x: { value: '1' }, y: { value: 'a b' } }));
  assert.equal(res.statusCode, 301);
  assert.equal(res.headers.location.value, 'https://www.flowebb.com/faq?x=1&y=a%20b');
});

test('apex emits every multiValue entry', () => {
  const res = handler(
    event('flowebb.com', '/faq', {
      t: { value: 'a', multiValue: [{ value: 'a' }, { value: 'b&c' }] },
    }),
  );
  assert.equal(res.headers.location.value, 'https://www.flowebb.com/faq?t=a&t=b%26c');
});

test('apex host match ignores case', () => {
  const res = handler(event('FlowEbb.com', '/x'));
  assert.equal(res.statusCode, 301);
  assert.equal(res.headers.location.value, 'https://www.flowebb.com/x');
});

test('www passes through', () => {
  const ev = event('www.flowebb.com', '/faq', { x: { value: '1' } });
  assert.equal(handler(ev), ev.request);
});
