/**
 * legacy partner-order/index.html → views/index.ejs 변환 스크립트.
 *
 * 입력: migration/source/scripts/partner-order/index.html
 * 출력: views/index.ejs
 *
 * 변환 규칙: estimate-legacy 의 convert-template.mjs 와 동일.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..', '..', '..', '..');
const LEGACY_ROOT = process.env.LEGACY_SRC_ROOT
  || path.join(ROOT, 'migration', 'source', 'scripts');
const SRC = path.join(LEGACY_ROOT, 'partner-order', 'index.html');
const DST = path.join(__dirname, '..', 'views', 'index.ejs');
const ASSETS_DIR = path.join(__dirname, '..', 'public', 'assets');
const LEGACY_ASSETS_DIR = path.join(LEGACY_ROOT, 'partner-order');

let html = fs.readFileSync(SRC, 'utf8');

// partner-order 의 include 자산은 legacy estimate 디렉토리에서 fallback 가능.
const INCLUDE_FILES = ['NanumGothic', 'NanumGothicBold', 'logo', 'stamp', 'samhan'];
const PARTIALS_DIR = path.join(__dirname, '..', 'views', 'partials');
fs.mkdirSync(PARTIALS_DIR, { recursive: true });
for (const name of INCLUDE_FILES) {
  const candidates = [
    path.join(LEGACY_ASSETS_DIR, `${name}.html`),
    path.join(LEGACY_ROOT, 'estimate', `${name}.html`),
  ];
  const dstPath = path.join(PARTIALS_DIR, `${name}.html`);
  for (const c of candidates) {
    if (fs.existsSync(c)) {
      fs.copyFileSync(c, dstPath);
      break;
    }
  }
}
html = html.replace(/<\?!= *include\(['"]([^'"]+)['"]\) *\?>/g, (_m, name) => {
  const p = path.join(PARTIALS_DIR, `${name}.html`);
  if (!fs.existsSync(p)) {
    console.warn(`[convert-template] include 자산 누락: ${name}`);
    return '';
  }
  return `<%- include('partials/${name}.html') %>`;
});

html = html.replace(/<\?!=\s*([^?]+?)\s*\?>/g, '<%- $1 %>');
html = html.replace(/<\?=\s*([^?]+?)\s*\?>/g, '<%= $1 %>');

// 클라이언트 측 inline Notion 토큰 (REDACTED placeholder) 제거 — 시크릿 스캔 false-positive 회피.
// legacy partner-order/index.html 8207 의 logActionToNotion 은 사실상 서버측 RPC 와 중복 (UrlFetchApp
// 은 브라우저에서 동작 X). 서버측 lib/code.js 의 동명 async 함수가 실제 동작 — 클라이언트 stub 만 비워둠.
html = html.replace(
  /'REDACTED_NOTION_TOKEN_[A-Z_0-9]+_\d+'/g,
  "'REDACTED_BY_BUILD_USE_SERVER_RPC'",
);
html = html.replace(
  /"REDACTED_NOTION_TOKEN_[A-Z_0-9]+_\d+"/g,
  '"REDACTED_BY_BUILD_USE_SERVER_RPC"',
);

const SHIM = `
<script>
(function () {
  if (window.google && window.google.script && window.google.script.run && window.google.script.run.__samhan) return;
  function makeRpc() {
    let onSuccess = function () {};
    let onFailure = function (e) { console.error('[rpc] failure', e); };
    const handler = {
      get: function (target, prop) {
        if (prop === 'withSuccessHandler') return function (cb) { onSuccess = cb || onSuccess; return new Proxy({}, handler); };
        if (prop === 'withFailureHandler') return function (cb) { onFailure = cb || onFailure; return new Proxy({}, handler); };
        if (prop === 'withUserObject') return function () { return new Proxy({}, handler); };
        return function () {
          const args = Array.prototype.slice.call(arguments);
          fetch('/rpc/' + prop, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ args: args }),
          })
            .then(function (r) { return r.json(); })
            .then(function (d) {
              if (d && d.ok) onSuccess(d.result);
              else onFailure(new Error((d && d.error) || 'RPC failed'));
            })
            .catch(onFailure);
        };
      },
    };
    return new Proxy({}, handler);
  }
  window.google = window.google || {};
  window.google.script = window.google.script || {};
  window.google.script.run = makeRpc();
  window.google.script.run.__samhan = true;
})();
</script>
`;

if (/<\/body>/i.test(html)) {
  html = html.replace(/<\/body>/i, SHIM + '</body>');
} else {
  html += SHIM;
}

fs.mkdirSync(path.dirname(DST), { recursive: true });
fs.writeFileSync(DST, html, 'utf8');
console.log(`[convert-template] ${path.relative(ROOT, DST)} written (${html.length} bytes, ${html.split('\\n').length} lines)`);
