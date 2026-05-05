/**
 * legacy index.html → views/index.ejs 변환 스크립트.
 *
 * 입력: migration/source/scripts/estimate/index.html (Apps Script HtmlService template)
 * 출력: views/index.ejs (Express EJS template)
 *
 * 변환 규칙 (DOM/CSS/JS 0% 변경, template tag 만 치환):
 *  1. `<?!= include('NAME') ?>` → public/assets/NAME.html 의 raw inline (Apps Script include)
 *  2. `<?!= var ?>` → `<%- var %>` (raw, JSON 주입용)
 *  3. `<?= var ?>` → `<%= var %>` (escaped, 단일 값)
 *  4. EJS 안의 `<%` 와 `%>` 가 본문 내 JS string/regex 와 충돌 가능 → legacy index.html 은
 *     template tag 외에는 `<?` 패턴이 없음 (검증 완료) → 안전.
 *  5. 끝에 google.script.run RPC shim 주입 — fetch('/rpc/<fnName>') 로 우회.
 *
 * 본 스크립트는 idempotent — 재실행 시 동일 결과.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..', '..', '..', '..');
// LEGACY_SRC_ROOT 환경변수로 외부 경로 override 가능 (기본: 프로젝트 root 의 migration/source/scripts).
const LEGACY_ROOT = process.env.LEGACY_SRC_ROOT
  || path.join(ROOT, 'migration', 'source', 'scripts');
const SRC = path.join(LEGACY_ROOT, 'estimate', 'index.html');
const DST = path.join(__dirname, '..', 'views', 'index.ejs');
const ASSETS_DIR = path.join(__dirname, '..', 'public', 'assets');
const LEGACY_ASSETS_DIR = path.join(LEGACY_ROOT, 'estimate');

let html = fs.readFileSync(SRC, 'utf8');

// 1) include() — Apps Script `<?!= include('X') ?>` → EJS native `<%- include('partials/X.html') %>`
//    legacy 자산 (.html 파일들 — fonts / logo / stamp / samhan) 은 views/partials/ 로 복사.
//    EJS include 는 compile 단계에서 inline 처리 → 런타임 비용은 inline 과 동일.
const INCLUDE_FILES = ['NanumGothic', 'NanumGothicBold', 'logo', 'stamp', 'samhan'];
const PARTIALS_DIR = path.join(__dirname, '..', 'views', 'partials');
fs.mkdirSync(PARTIALS_DIR, { recursive: true });
for (const name of INCLUDE_FILES) {
  const legacyPath = path.join(LEGACY_ASSETS_DIR, `${name}.html`);
  const dstPath = path.join(PARTIALS_DIR, `${name}.html`);
  if (fs.existsSync(legacyPath)) {
    fs.copyFileSync(legacyPath, dstPath);
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

// 2) `<?!= var ?>` → `<%- var %>` (raw JSON)
html = html.replace(/<\?!=\s*([^?]+?)\s*\?>/g, '<%- $1 %>');

// 3) `<?= var ?>` → `<%= var %>` (escaped)
html = html.replace(/<\?=\s*([^?]+?)\s*\?>/g, '<%= $1 %>');

// 4) google.script.run shim 주입 — </body> 직전에 한 번만.
const SHIM = `
<script>
/**
 * google.script.run 호환 shim — Apps Script HtmlService → Express RPC.
 *
 * legacy index.html 의 모든 RPC 호출은 다음 패턴:
 *   google.script.run.withSuccessHandler(cb).withFailureHandler(cb).fnName(args)
 *
 * 본 shim 은 위 chain 을 가로채 \`POST /rpc/fnName\` 으로 fetch 한다.
 */
(function () {
  if (window.google && window.google.script && window.google.script.run && window.google.script.run.__samhan) return;
  function makeRpc() {
    let onSuccess = function () {};
    let onFailure = function (e) { console.error('[rpc] failure', e); };
    const handler = {
      get: function (target, prop) {
        if (prop === 'withSuccessHandler') {
          return function (cb) { onSuccess = cb || onSuccess; return new Proxy({}, handler); };
        }
        if (prop === 'withFailureHandler') {
          return function (cb) { onFailure = cb || onFailure; return new Proxy({}, handler); };
        }
        if (prop === 'withUserObject') {
          return function () { return new Proxy({}, handler); };
        }
        // 함수 호출
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
