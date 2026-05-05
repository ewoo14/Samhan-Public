/* eslint-disable */
/**
 * 빌드 시 legacy estimate index.html 을 Electron webview 에 로드 가능한 정적 HTML 로 변환.
 *
 * 처리 단계:
 *   1) `migration/source/scripts/estimate/index.html` (18614 라인) 읽기
 *   2) Google Apps Script 템플릿 디렉티브 `<?!= include('X') ?>` 를 동일 디렉토리의 `X.html`
 *      파일 내용으로 inline 치환 (NanumGothic / NanumGothicBold / logo / stamp / samhan)
 *   3) `</head>` 직전에 shim bootstrap `<script>` 삽입 — webview preload 가 `window.google`
 *      을 주입하지 않은 경우 (예: 일반 브라우저 미리보기) fallback noop 으로 동작
 *   4) 결과를 `clients/desktop/legacy-assets/estimate/index.built.html` 로 출력
 *
 * 부재 시 graceful fallback:
 *   - source HTML 이 없으면 `index.fallback.html` 만 출력 (PR #38 main 미반영 환경)
 *   - electron-vite build 시 legacy-assets 통째로 renderer dist 에 복사
 *
 * 호출:
 *   - npm run build:legacy (manual)
 *   - npm run build (자동 prebuild step)
 *
 * 함수 매핑 표는 `docs/dev-reports/legacy-rpc-mapping-estimate.md` 참고.
 */
const { existsSync, mkdirSync, readFileSync, writeFileSync, copyFileSync, readdirSync } = require('node:fs')
const { resolve, basename, dirname } = require('node:path')

const ROOT = resolve(__dirname, '..', '..', '..')
const SRC_DIR = resolve(ROOT, 'migration', 'source', 'scripts', 'estimate')
const OUT_DIR = resolve(__dirname, '..', 'legacy-assets', 'estimate')
const SRC_INDEX = resolve(SRC_DIR, 'index.html')
const OUT_INDEX = resolve(OUT_DIR, 'index.built.html')
const FALLBACK_INDEX = resolve(OUT_DIR, 'index.fallback.html')

/**
 * Apps Script `<?!= include('NAME') ?>` 디렉티브를 동일 디렉토리의 `NAME.html` 내용으로 치환.
 * include 파일 내부의 추가 디렉티브는 (현재 legacy 코드 기준) 발생하지 않으므로 1단계 치환.
 */
function resolveIncludes(html, srcDir) {
  return html.replace(/<\?!=\s*include\('([^']+)'\)\s*\?>/g, (match, name) => {
    const includePath = resolve(srcDir, `${name}.html`)
    if (!existsSync(includePath)) {
      console.warn(`[build-legacy-estimate] include 파일 없음: ${name}.html — 빈 placeholder`)
      return `<!-- include('${name}') 없음 -->`
    }
    const content = readFileSync(includePath, 'utf8')
    console.log(`[build-legacy-estimate] include('${name}') 치환 (${content.length} bytes)`)
    return content
  })
}

/**
 * `</head>` 직전에 shim bootstrap 삽입.
 *
 * webview preload 스크립트 (clients/desktop/src/preload/legacyShim.ts) 가 먼저 실행되어
 * `window.google.script.run` 을 주입한 후 본 inline script 가 실행되며, preload 미적용
 * 환경 (일반 브라우저 디버깅 등) 에서는 fallback noop shim 을 주입한다.
 */
function injectShimBootstrap(html) {
  const bootstrap = `<script>
/* SamhanLogis legacy webview shim bootstrap (auto-generated) */
(function(){
  if (window.google && window.google.script && window.google.script.run) {
    console.log('[legacyShim] preload 가 주입한 google.script.run 사용');
    return;
  }
  console.warn('[legacyShim] preload 미적용 — fallback noop shim 활성');
  function makeChain(fnName){
    var onSuccess=null, onFailure=null;
    var chain={};
    chain.withSuccessHandler=function(cb){ onSuccess=cb; return chain; };
    chain.withFailureHandler=function(cb){ onFailure=cb; return chain; };
    chain[fnName]=function(){
      console.warn('[legacyShim:noop] '+fnName+' 호출 — backend 미연결');
      try{ if(onSuccess) onSuccess(null); }catch(e){ if(onFailure) onFailure(e); }
    };
    return chain;
  }
  window.google = window.google || {};
  window.google.script = window.google.script || {};
  window.google.script.run = new Proxy({}, {
    get: function(_, prop){
      if (prop === 'withSuccessHandler' || prop === 'withFailureHandler') {
        return function(cb){ return makeChain('__noop__'); };
      }
      return makeChain(String(prop));
    }
  });
})();
</script>
`
  if (html.includes('</head>')) {
    return html.replace('</head>', `${bootstrap}</head>`)
  }
  return bootstrap + html
}

function ensureDir(dir) {
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true })
}

/** legacy assets 부재 시 fallback HTML — webview src 가 항상 유효하도록. */
function writeFallback() {
  ensureDir(OUT_DIR)
  const fallback = `<!doctype html>
<html lang="ko">
<head><meta charset="utf-8"><title>종합견적서 (legacy 미반영)</title>
<style>
body{font-family:system-ui,sans-serif;background:#f8fafc;color:#0f172a;margin:0;padding:0;}
.empty{max-width:640px;margin:80px auto;padding:32px;border:1px solid #e2e8f0;border-radius:12px;background:#fff;}
h1{margin-top:0;font-size:20px;}
code{background:#f1f5f9;padding:2px 6px;border-radius:4px;font-size:12px;}
.hint{color:#475569;font-size:14px;line-height:1.6;}
</style>
</head>
<body>
<div class="empty">
<h1>legacy estimate 자료 미반영</h1>
<p class="hint">
\`migration/source/scripts/estimate/\` 가 비어 있어 본 webview 는 placeholder 로 표시됩니다.
<br/>해당 자료는 <code>feature/legacy-migration-discovery</code> 브랜치 머지 후 자동 반영됩니다.
</p>
<p class="hint">
빌드 단계에서 <code>clients/desktop/scripts/build-legacy-estimate.cjs</code> 가 자료 존재 여부를
확인 후 실 HTML 또는 본 placeholder 를 생성합니다.
</p>
</div>
</body>
</html>
`
  writeFileSync(FALLBACK_INDEX, fallback, 'utf8')
  // index.built.html 이 없으면 fallback 을 표준 entry 로 사용
  if (!existsSync(OUT_INDEX)) {
    writeFileSync(OUT_INDEX, fallback, 'utf8')
  }
  console.log(`[build-legacy-estimate] fallback 출력: ${FALLBACK_INDEX}`)
}

/** 메인 빌드 루틴. */
function main() {
  ensureDir(OUT_DIR)

  if (!existsSync(SRC_INDEX)) {
    console.warn(`[build-legacy-estimate] source 없음: ${SRC_INDEX}`)
    writeFallback()
    return
  }

  console.log(`[build-legacy-estimate] source 읽기: ${SRC_INDEX}`)
  let html = readFileSync(SRC_INDEX, 'utf8')

  console.log(`[build-legacy-estimate] include 치환 중`)
  html = resolveIncludes(html, SRC_DIR)

  console.log(`[build-legacy-estimate] shim bootstrap 삽입`)
  html = injectShimBootstrap(html)

  writeFileSync(OUT_INDEX, html, 'utf8')
  writeFallback()
  console.log(`[build-legacy-estimate] 출력 완료: ${OUT_INDEX} (${html.length} bytes)`)
}

if (require.main === module) main()

module.exports = { resolveIncludes, injectShimBootstrap }
