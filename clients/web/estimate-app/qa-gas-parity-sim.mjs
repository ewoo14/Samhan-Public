/**
 * qa-gas-parity-sim.mjs — 종합견적서 GAS parity 실증 시뮬레이션 harness
 *
 * 목적: "GAS 와 기능 차이가 전혀 없어야 함" 최종 검증.
 *   - 우리: clients/web/estimate-app/lib/code.js  (classifyHome_ / classifySingleSetLM_ /
 *           classifyCommercial_ / classifyCommercialDisp_)  ← require() 로 실 함수 로드
 *   - GAS : tools/legacy-gas/종합견적서-live/Code.js        ← 실 소스 바이트를 brace-match
 *           슬라이스 후 vm 샌드박스에서 평가 (분류/disp 헬퍼는 pure)
 *
 * 입력(실 데이터): product_db (samhan-postgres) 의 estimate-catalog endpoint 동등 쿼리.
 *   EstimateCatalogInternalController.products() == findExposedCatalog(category, [ESTIMATE,BOTH])
 *   → product_estimate_exposure JOIN products, usage_scope IN (ESTIMATE,BOTH).
 *   각 row 의 (name, model_code) 를 양쪽 분류기에 동일 입력.
 *
 * 단가 함수(parseFixedDc/homeUnitPrice/commUnitPrice/singleUnitPrice/explodeSetParts)는
 *   index.html/index.ejs <script> 에서 정의되며 DOM(document.*) 의존 → headless 직접 실행
 *   비현실적. 대신 양쪽 본문을 바이트 단위 비교하여 동일성을 코드로 단언(assertPriceFnIdentical).
 *
 * 실행:
 *   node clients/web/estimate-app/qa-gas-parity-sim.mjs
 *   (samhan-postgres 가동 필요. docker exec 로 psql 호출.)
 */

import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import vm from 'node:vm';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(__dirname, '../../..');
const GAS_CODE = path.join(REPO, 'tools/legacy-gas/종합견적서-live/Code.js');
const GAS_HTML = path.join(REPO, 'tools/legacy-gas/종합견적서-live/index.html');
const APP_CODE = path.join(__dirname, 'lib/code.js');
const APP_EJS = path.join(__dirname, 'views/index.ejs');

/* ─────────────────────────────────────────────────────────────────────────
 * 1) 우리 estimate-app 분류기 로드 (실 함수)
 * ─────────────────────────────────────────────────────────────────────── */
const require = createRequire(import.meta.url);
const app = require(APP_CODE);
const APP = {
  classifyHome_: app.classifyHome_,
  classifySingleSetLM_: app.classifySingleSetLM_,
  classifyCommercial_: app.classifyCommercial_,
};

/* ─────────────────────────────────────────────────────────────────────────
 * 2) GAS Code.js 의 pure 분류/헬퍼 함수를 실 소스에서 슬라이스 → vm 평가
 * ─────────────────────────────────────────────────────────────────────── */
// GAS Code.js 전체를 vm 샌드박스에서 평가 — 분류/disp 함수는 pure 이고, 모듈 최상위는
// 리터럴 const/var 만(Google API 호출 없음)이라 깨끗이 로드됨. Google 전역은 stub 주입.
// 추출하려는 함수의 *실 소스 바이트 100%* 를 그대로 사용 (복사/슬라이스 아님).
const GAS_FN_NAMES = [
  'sanitizeKoreanParen_',
  'trimSymbols_',
  'sanitizeDisp_',
  'hpFromText_',
  'unifyCatL_',
  'classifyHome_',
  'classifySingleSetLM_',
  'classifyCommercial_',
];
const gasSrc = readFileSync(GAS_CODE, 'utf8');
const noop = () => {};
const stub = new Proxy(function () {}, {
  get: () => stub,
  apply: () => stub,
  construct: () => ({}),
});
const sandbox = {
  // Google Apps Script 전역 stub (정의 시점엔 미호출 — 안전망)
  SpreadsheetApp: stub, DriveApp: stub, UrlFetchApp: stub, HtmlService: stub,
  PropertiesService: stub, CacheService: stub, Session: stub, Utilities: stub,
  Logger: { log: noop }, console,
};
vm.createContext(sandbox);
vm.runInContext(
  gasSrc + '\n;this.__GAS__ = { ' + GAS_FN_NAMES.join(', ') + ' };',
  sandbox,
  { filename: 'GAS-Code.js' },
);
const GAS = sandbox.__GAS__;
for (const n of GAS_FN_NAMES) {
  if (typeof GAS[n] !== 'function') throw new Error(`GAS 함수 로드 실패: ${n}`);
}
// GAS 상업멀티 카탈로그 disp 어댑터(getCommercialMulti 의 disp 규칙) 재현:
//   classifyCommercial_ + disp = sanitizeDisp_(name)  ← estimate-app classifyCommercialDisp_ 와 동일 구조
GAS.classifyCommercialDisp_ = (name, model) => {
  const c = GAS.classifyCommercial_(name, model);
  return { catL: c.catL, catM: c.catM, catS: c.catS, disp: GAS.sanitizeDisp_(name) };
};
// code.js 의 classifyCommercialDisp_(line 607) 는 미export 이나 본문은
//   classifyCommercial_ + disp=sanitizeDisp_(name) (둘 다 export) — 동일 합성 재현.
APP.classifyCommercialDisp_ = (name, model) => {
  const c = app.classifyCommercial_(name, model);
  return { catL: c.catL, catM: c.catM, catS: c.catS, disp: app.sanitizeDisp_(name) };
};

/* ─────────────────────────────────────────────────────────────────────────
 * 3) 단가 함수 바이트 동일성 단언 (DOM 의존 → 실행 대신 소스 동일성 검증)
 * ─────────────────────────────────────────────────────────────────────── */
// 균형중괄호 추출기 — 문자열/템플릿/주석/정규식 리터럴 인지.
function balancedSlice(src, startIdx) {
  let i = src.indexOf('{', startIdx);
  if (i < 0) throw new Error('본문 { 미발견');
  let depth = 0;
  let mode = 'normal';
  let prevSig = '';
  for (; i < src.length; i++) {
    const c = src[i];
    const n = src[i + 1];
    switch (mode) {
      case 'sq': if (c === '\\') i++; else if (c === "'") mode = 'normal'; continue;
      case 'dq': if (c === '\\') i++; else if (c === '"') mode = 'normal'; continue;
      case 'tpl': if (c === '\\') i++; else if (c === '`') mode = 'normal'; continue;
      case 'line': if (c === '\n') mode = 'normal'; continue;
      case 'block': if (c === '*' && n === '/') { i++; mode = 'normal'; } continue;
      case 'regex': if (c === '\\') i++; else if (c === '[') mode = 'rclass'; else if (c === '/') mode = 'normal'; continue;
      case 'rclass': if (c === '\\') i++; else if (c === ']') mode = 'regex'; continue;
      default: break;
    }
    if (c === "'") { mode = 'sq'; continue; }
    if (c === '"') { mode = 'dq'; continue; }
    if (c === '`') { mode = 'tpl'; continue; }
    if (c === '/' && n === '/') { mode = 'line'; i++; continue; }
    if (c === '/' && n === '*') { mode = 'block'; i++; continue; }
    if (c === '/') {
      const re = new Set(['(', ',', '=', ':', '[', '!', '&', '|', '?', '{', '}', ';', '+', '-', '*', '%', '<', '>', '~', '^', 'return', 'typeof', 'case', '=>', '']);
      if (re.has(prevSig)) { mode = 'regex'; continue; }
    }
    if (c === '{') { depth++; }
    else if (c === '}') { depth--; if (depth === 0) return src.slice(startIdx, i + 1); }
    if (!/\s/.test(c)) {
      if (/[A-Za-z0-9_$]/.test(c)) {
        let j = i; while (j < src.length && /[A-Za-z0-9_$]/.test(src[j])) j++;
        prevSig = src.slice(i, j); i = j - 1;
      } else if (c === '=' && n === '>') { prevSig = '=>'; i++; }
      else { prevSig = c; }
    }
  }
  throw new Error('본문 } 미발견');
}
function extractFnBody(file, name) {
  const src = readFileSync(file, 'utf8');
  const m = new RegExp(`function\\s+${name}\\s*\\(`).exec(src);
  if (!m) throw new Error(`${name} 미발견 in ${file}`);
  return balancedSlice(src, m.index);
}
function normalizeWs(s) {
  // 사소한 공백/CRLF 차이를 무시하고 의미 동일성 비교
  return s.replace(/\r\n/g, '\n').replace(/[ \t]+/g, ' ').replace(/[ \t]*\n[ \t]*/g, '\n').trim();
}
function assertPriceFnIdentical(name) {
  const g = extractFnBody(GAS_HTML, name);
  const a = extractFnBody(APP_EJS, name);
  const gn = normalizeWs(g);
  const an = normalizeWs(a);
  const identical = gn === an;
  // 첫 불일치 위치 찾기
  let firstDiff = -1;
  if (!identical) {
    const len = Math.min(gn.length, an.length);
    for (let i = 0; i < len; i++) {
      if (gn[i] !== an[i]) {
        firstDiff = i;
        break;
      }
    }
    if (firstDiff < 0) firstDiff = len;
  }
  return {
    name,
    identical,
    gasLen: gn.length,
    appLen: an.length,
    firstDiff,
    gasSnippet: identical ? '' : gn.slice(Math.max(0, firstDiff - 40), firstDiff + 60),
    appSnippet: identical ? '' : an.slice(Math.max(0, firstDiff - 40), firstDiff + 60),
  };
}

/* ─────────────────────────────────────────────────────────────────────────
 * 4) 실 데이터 로드 — product_db (endpoint 동등 쿼리)
 * ─────────────────────────────────────────────────────────────────────── */
function loadCategory(estimateCategory) {
  // endpoint: findExposedCatalog(category, [ESTIMATE,BOTH]) → name, model_code
  const sql = `
    SELECT p.name, COALESCE(p.model_code, p.model_name) AS model
      FROM product_estimate_exposure e
      JOIN products p ON p.id = e.product_id
     WHERE e.is_deleted = false
       AND p.is_deleted = false
       AND e.estimate_category = '${estimateCategory}'
       AND p.usage_scope IN ('ESTIMATE','BOTH')
     ORDER BY e.display_order ASC NULLS LAST, p.model_code ASC`;
  const out = execFileSync(
    'docker',
    ['exec', 'samhan-postgres', 'psql', '-U', 'samhan', '-d', 'product_db', '-At', '-F', '\t', '-c', sql],
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 },
  );
  return out
    .split('\n')
    .map((l) => l.replace(/\r$/, ''))
    .filter((l) => l.length > 0)
    .map((l) => {
      const tab = l.indexOf('\t');
      const name = tab < 0 ? l : l.slice(0, tab);
      const model = tab < 0 ? '' : l.slice(tab + 1);
      return { name, model };
    });
}

/* ─────────────────────────────────────────────────────────────────────────
 * 5) 카테고리별 분류 parity 실행
 * ─────────────────────────────────────────────────────────────────────── */
const eqCls = (a, b) =>
  String(a.catL ?? '') === String(b.catL ?? '') &&
  String(a.catM ?? '') === String(b.catM ?? '') &&
  String(a.catS ?? '') === String(b.catS ?? '');
const eqDisp = (a, b) => String(a.disp ?? '') === String(b.disp ?? '');
const fmt = (c) => `[L=${c.catL ?? ''}|M=${c.catM ?? ''}|S=${c.catS ?? ''}${c.disp !== undefined ? '|disp=' + (c.disp ?? '') : ''}]`;

function runCategory(label, estimateCategory, gasFn, appFn, withDisp) {
  const rows = loadCategory(estimateCategory);
  let clsMismatch = 0;
  let dispMismatch = 0;
  const examples = [];
  for (const { name, model } of rows) {
    const g = gasFn(name, model);
    const a = appFn(name, model);
    const clsBad = !eqCls(g, a);
    const dispBad = withDisp && !eqDisp(g, a);
    if (clsBad) clsMismatch++;
    if (dispBad) dispMismatch++;
    if ((clsBad || dispBad) && examples.length < 25) {
      examples.push({ name, model, gas: fmt(g), app: fmt(a), clsBad, dispBad });
    }
  }
  return { label, estimateCategory, total: rows.length, clsMismatch, dispMismatch, examples };
}

// 분류기 어댑터 (name, model) 시그니처 통일
const adaptHome = (fn) => (name) => fn(name); // classifyHome_(name)
const adaptSingle = (fn) => (name, model) => fn({ name, model }); // classifySingleSetLM_({name,model}) → {L,M}
// 싱글세트는 {L,M} shape → catL/catM 매핑 비교
function singleWrap(fn) {
  return (name, model) => {
    const r = fn({ name, model });
    return { catL: r.L, catM: r.M, catS: '' };
  };
}

const results = [];
results.push(
  runCategory('홈멀티(HOME_MULTI)', 'HOME_MULTI', (n) => GAS.classifyHome_(n), (n) => APP.classifyHome_(n), true),
);
results.push(
  runCategory(
    '상업멀티(COMMERCIAL_MULTI)',
    'COMMERCIAL_MULTI',
    (n, m) => GAS.classifyCommercialDisp_(n, m),
    (n, m) => APP.classifyCommercialDisp_(n, m),
    true,
  ),
);
results.push(
  runCategory(
    '싱글세트(SINGLE_SET)',
    'SINGLE_SET',
    (n, m) => singleWrap(GAS.classifySingleSetLM_)(n, m),
    (n, m) => singleWrap(APP.classifySingleSetLM_)(n, m),
    false,
  ),
);
// 구형(LEGACY): GAS getOldProducts_ 는 분류기 미적용(품명/모델 그대로) → 분류 parity 대상 아님.
// 단가만 50% DC 고정 — 단가 함수 동일성으로 커버. 참고용 row 수만 집계.
let oldCount = 0;
try {
  oldCount = loadCategory('LEGACY').length;
} catch (e) {
  oldCount = -1;
}

/* ─────────────────────────────────────────────────────────────────────────
 * 6) 단가 함수 동일성
 * ─────────────────────────────────────────────────────────────────────── */
const PRICE_FNS = ['parseFixedDc', 'homeUnitPrice', 'singleUnitPrice', 'commUnitPrice', 'explodeSetParts'];
const priceResults = PRICE_FNS.map((n) => assertPriceFnIdentical(n));

/* ─────────────────────────────────────────────────────────────────────────
 * 7) 추가: classifySingleSetLM_ 입력결합 divergence 직접 probe
 *    (name 비어있고 model 에만 키워드 / name 과 model 에 다른 키워드)
 * ─────────────────────────────────────────────────────────────────────── */
function probeSingleInputCombine() {
  // 실 데이터에서 'name 에는 분류키워드 없음 + model 에 있음' 케이스를 자동 탐지
  const rows = loadCategory('SINGLE_SET');
  const KW = /(360\s*cst|360cst|360|4\s*way|4way|1\s*way|1way|덕트|실링|스탠드|벽걸이|가정용|하우스|집)/i;
  const found = [];
  for (const { name, model } of rows) {
    const g = singleWrap(GAS.classifySingleSetLM_)(name, model);
    const a = singleWrap(APP.classifySingleSetLM_)(name, model);
    if (!eqCls(g, a)) {
      const nameHas = KW.test(String(name).toLowerCase());
      const modelHas = KW.test(String(model).toLowerCase());
      found.push({ name, model, nameHas, modelHas, gas: fmt(g), app: fmt(a) });
    }
  }
  return found;
}
const singleProbe = probeSingleInputCombine();

/* ─────────────────────────────────────────────────────────────────────────
 * 출력
 * ─────────────────────────────────────────────────────────────────────── */
console.log('═'.repeat(78));
console.log('종합견적서 GAS parity 실증 — 실 데이터(product_db) 동일 입력 분류/단가 비교');
console.log('═'.repeat(78));
console.log('\n■ 분류(catL/catM/catS, 해당 시 disp) parity\n');
console.log(
  ['카테고리', '검사품목수', '분류불일치', 'disp불일치'].map((s) => s.padEnd(22)).join(''),
);
console.log('-'.repeat(78));
for (const r of results) {
  console.log(
    [r.label, String(r.total), String(r.clsMismatch), String(r.dispMismatch)]
      .map((s, i) => String(s).padEnd(i === 0 ? 28 : 14))
      .join(''),
  );
}
console.log(
  ['구형(LEGACY)*분류미적용', String(oldCount), 'N/A', 'N/A']
    .map((s, i) => String(s).padEnd(i === 0 ? 28 : 14))
    .join(''),
);
console.log('  * 구형은 GAS/우리 모두 분류기 미적용(품명/모델 그대로, 단가만 50% DC) → 단가 동일성으로 커버');

let totalCls = 0;
let totalDisp = 0;
for (const r of results) {
  totalCls += r.clsMismatch;
  totalDisp += r.dispMismatch;
}

for (const r of results) {
  if (r.examples.length) {
    console.log(`\n  ── ${r.label} 불일치 예 (최대 25) ──`);
    for (const e of r.examples) {
      console.log(`   품명: ${e.name}  |  model: ${e.model}`);
      console.log(`      GAS : ${e.gas}`);
      console.log(`      우리: ${e.app}`);
    }
  }
}

console.log('\n■ classifySingleSetLM_ 입력결합 divergence probe (실 데이터 자동탐지)');
if (singleProbe.length === 0) {
  console.log('   → 실 SINGLE_SET 데이터에서 분류 불일치 0건 (name/model 결합 차이로 인한 실 divergence 없음)');
} else {
  console.log(`   → 불일치 ${singleProbe.length}건:`);
  for (const p of singleProbe.slice(0, 25)) {
    console.log(`   품명: ${p.name} | model: ${p.model} | nameHasKW=${p.nameHas} modelHasKW=${p.modelHas}`);
    console.log(`      GAS : ${p.gas}`);
    console.log(`      우리: ${p.app}`);
  }
}

console.log('\n■ 단가 함수 소스 동일성 (DOM 의존 → 바이트/공백정규화 비교)\n');
console.log(['함수', '동일여부', 'GAS길이', '우리길이', '첫불일치'].map((s) => s.padEnd(16)).join(''));
console.log('-'.repeat(78));
let priceAllSame = true;
for (const p of priceResults) {
  if (!p.identical) priceAllSame = false;
  console.log(
    [p.name, p.identical ? '동일' : '★다름', String(p.gasLen), String(p.appLen), p.identical ? '-' : String(p.firstDiff)]
      .map((s) => String(s).padEnd(16))
      .join(''),
  );
}
for (const p of priceResults) {
  if (!p.identical) {
    console.log(`\n  ── ${p.name} 첫 불일치 컨텍스트 ──`);
    console.log(`     GAS : …${p.gasSnippet}…`);
    console.log(`     우리: …${p.appSnippet}…`);
  }
}

console.log('\n' + '═'.repeat(78));
console.log('최종 요약');
console.log('═'.repeat(78));
const totalItems = results.reduce((t, r) => t + r.total, 0);
console.log(`분류 검사 총 품목수: ${totalItems} (홈 ${results[0].total} / 상업 ${results[1].total} / 싱글 ${results[2].total})`);
console.log(`분류 불일치 합계: ${totalCls}  |  disp 불일치 합계: ${totalDisp}`);
console.log(`단가 함수 전체 동일: ${priceAllSame ? 'YES (5/5)' : 'NO'}`);
const verdict = totalCls === 0 && totalDisp === 0 && priceAllSame;
console.log(`\n>>> PARITY 판정: ${verdict ? '✅ 차이 0 (GAS == 우리)' : '❌ 차이 존재 — 상기 표 참조'}`);

// 머신리더블 결과
const summary = {
  generatedAt: new Date().toISOString(),
  classification: results.map((r) => ({
    category: r.estimateCategory,
    label: r.label,
    inspected: r.total,
    classMismatch: r.clsMismatch,
    dispMismatch: r.dispMismatch,
    examples: r.examples,
  })),
  legacyOldCount: oldCount,
  singleInputCombineProbe: singleProbe,
  priceFunctions: priceResults.map((p) => ({ name: p.name, identical: p.identical, gasLen: p.gasLen, appLen: p.appLen, firstDiff: p.firstDiff })),
  totals: { inspected: totalItems, classMismatch: totalCls, dispMismatch: totalDisp, priceAllSame },
  verdict,
};
console.log('\n--- JSON ---');
console.log(JSON.stringify(summary, null, 2));
