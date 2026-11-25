// CloudFront Function (runtime cloudfront-js-2.0), viewer-request.
// Redirects the apex domain to www, keeping path and query string.
// Terraform loads this file with file(); the function cannot take variables,
// so the domains are constants.
var APEX_HOST = 'flowebb.com';
var WWW_HOST = 'www.flowebb.com';

// Query keys and values arrive as received from the client, so they may be
// raw or already percent-encoded. Decode first (treating "+" as a space),
// then encode, so "a b" and "a%20b" both become "a%20b" and nothing is
// double-encoded. Malformed escapes are kept as received.
function normalize(s) {
  try {
    return encodeURIComponent(decodeURIComponent(s.replace(/\+/g, ' ')));
  } catch (e) {
    return s;
  }
}

function buildQuery(querystring) {
  var parts = [];
  Object.keys(querystring || {}).forEach(function (key) {
    var entry = querystring[key];
    var values = entry.multiValue
      ? entry.multiValue.map(function (v) { return v.value; })
      : [entry.value];
    values.forEach(function (value) {
      var k = normalize(key);
      parts.push(value === undefined || value === '' ? k : k + '=' + normalize(value));
    });
  });
  return parts.length ? '?' + parts.join('&') : '';
}

function handler(event) {
  var request = event.request;
  var host = request.headers.host && request.headers.host.value;
  if (host && host.toLowerCase() === APEX_HOST) {
    return {
      statusCode: 301,
      statusDescription: 'Moved Permanently',
      headers: {
        location: { value: 'https://' + WWW_HOST + request.uri + buildQuery(request.querystring) },
      },
    };
  }
  return request;
}
