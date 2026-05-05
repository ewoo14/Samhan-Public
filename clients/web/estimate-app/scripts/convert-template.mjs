/**
 * Apps Script HTML template → EJS 변환 스크립트.
 *
 * 입력: migration/source/scripts/estimate/index.html (18614 라인)
 * 출력: views/index.ejs
 *
 * 변환 규칙:
 *   <?!= include('logo') ?>      → public/assets/logo.html 의 raw content 직접 inline
 *   <?!= var ?>                  → <%- var %>           (HTML escape 안함)
 *   <?= var ?>                   → <%= var %>           (HTML escape)
 *   <? code ?>                   → <% code %>           (control flow)
 *
 * 추가 작업:
 *   - </head> 직전에 google.script.run RPC shim inline script 삽입
 *
 * 사용:
 *   node scripts/convert-template.mjs
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const REPO_ROOT = path.resolve(ROOT, '..', '..', '..');

const candidates = [
  path.join(REPO_ROOT, 'migration', 'source', 'scripts', 'estimate', 'index.html'),
  'C:/dev/SamhanLogis/migration/source/scripts/estimate/index.html',
];
let SRC = null;
for (const c of candidates) {
  if (fs.existsSync(c)) { SRC = c; break; }
}
if (!SRC) {
  console.error('[convert] 입력 파일 못찾음:', candidates);
  process.exit(1);
}
console.log(`[convert] reading ${SRC}`);

const out = path.join(ROOT, 'views', 'index.ejs');
let html = fs.readFileSync(SRC, 'utf8');
const before = html.length;

const cnt = { unescape: 0, escape: 0, scriptlet: 0, include: 0 };
const PARTIAL_DIR = path.join(ROOT, 'public', 'assets');

// 1. include — raw inline (public/assets/<name>.html)
html = html.replace(/<\?!=\s*include\(['"]([^'"]+)['"]\)\s*\?>/g, (_, name) => {
  cnt.include++;
  const p = path.join(PARTIAL_DIR, `${name}.html`);
  if (fs.existsSync(p)) {
    const content = fs.readFileSync(p, 'utf8');
    return `<!-- include: ${name}.html (inlined) -->\n${content}\n<!-- /include: ${name}.html -->`;
  }
  console.warn(`[convert] include 누락: ${p}`);
  return `<!-- include missing: ${name}.html -->`;
});

// 2. <?!= var ?>  → <%- var %>
html = html.replace(/<\?!=\s*([\s\S]*?)\s*\?>/g, (_, expr) => {
  cnt.unescape++;
  return `<%- ${expr.trim()} %>`;
});

// 3. <?= var ?>   → <%= var %>  (단, JS 문자열 안에 박힌 경우 <%- %> 사용 — Apps Script
//    HtmlService 의 contextual escaping 흉내. EJS `<%=` 는 무조건 HTML escape 하므로
//    `JSON.parse('...')` 안의 `"` 가 `&#34;` 로 깨진다. JS 문자열 컨텍스트 식별 휴리스틱:
//    `'<?= ?>'` 또는 `"<?= ?>"` 처럼 JS 따옴표 안에 박힌 형태이면 raw 출력.
html = html.replace(/(['"])\s*<\?=\s*([\s\S]*?)\s*\?>\s*\1/g, (_, q, expr) => {
  cnt.escape++;
  return `${q}<%- ${expr.trim()} %>${q}`;
});
html = html.replace(/<\?=\s*([\s\S]*?)\s*\?>/g, (_, expr) => {
  cnt.escape++;
  return `<%= ${expr.trim()} %>`;
});

// 4. <? code ?>   → <% code %>
html = html.replace(/<\?\s*([\s\S]*?)\s*\?>/g, (_, expr) => {
  cnt.scriptlet++;
  return `<% ${expr.trim()} %>`;
});

// 5. </head> 직전에 google.script.run shim 삽입
const shim = `<!-- estimate-app v2: google.script.run RPC shim (B2 마이그) -->
<script>
(function(){
  if (typeof window === 'undefined') return;
  window.google = window.google || {};
  window.google.script = window.google.script || {};
  function makeRunner(){
    let onSuccess = null, onFailure = null;
    const target = function(){};
    const proxy = new Proxy(target, {
      get(_, prop){
        if (prop === 'withSuccessHandler') return function(cb){ onSuccess = cb; return proxy; };
        if (prop === 'withFailureHandler') return function(cb){ onFailure = cb; return proxy; };
        if (prop === 'withUserObject') return function(){ return proxy; };
        if (prop === Symbol.toPrimitive || prop === 'then' || typeof prop !== 'string') return undefined;
        return function(){
          const args = Array.from(arguments);
          fetch('/rpc/' + prop, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ args }),
          })
            .then(function(r){ return r.json(); })
            .then(function(d){
              if (d && d.ok) { if (onSuccess) onSuccess(d.result); }
              else { if (onFailure) onFailure(new Error(d && d.error || 'RPC 실패')); }
            })
            .catch(function(e){ if (onFailure) onFailure(e); });
        };
      }
    });
    return proxy;
  }
  Object.defineProperty(window.google.script, 'run', { get: function(){ return makeRunner(); } });
  window.google.script.host = { close: function(){}, setHeight: function(){}, setWidth: function(){} };
  window.google.script.url = { getLocation: function(cb){ cb({ hash: location.hash, parameter: {}, parameters: {} }); } };
})();
</script>
`;
html = html.replace('</head>', `${shim}\n</head>`);

fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, html, 'utf8');

const after = html.length;
console.log(`[convert] wrote ${out} (${before} → ${after} bytes)`);
console.log(`[convert] tags converted:`, cnt);
