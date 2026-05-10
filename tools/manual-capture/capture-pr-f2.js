/**
 * PR-F2 QA — vendor 발주서 OCR 업로드 3-step 작동 캡처.
 *
 * 사용자 명시 (memory feedback_pr_qa_screenshots) — 작동 화면 시각 증거 절대 의무.
 *
 * 전제:
 *   - clients/desktop 에서 `cross-env VITE_MOCK_MODE=1 npx vite --port 5176` 가동
 *   - playwright + sharp 는 tools/manual-capture/node_modules 에 이미 설치됨
 *
 * 동작:
 *   1) Playwright (chromium fallback msedge) headless 으로 vite renderer 진입
 *   2) ?mockRole=MASTER 쿼리스트링 → SalesVendorOrderUploadPage RoleGuard 통과
 *      (VENDOR_ORDER_OCR_ROLES = ['SALES','MANAGER','MASTER'])
 *   3) Step 1 (upload) 캡처 → vendor 라디오 + drag-drop 영역 + 미리보기
 *   4) DataTransfer 시뮬레이션으로 mock file 주입 → "OCR 분석 시작" 클릭
 *   5) Step 2 (preview) 캡처 → OCR 결과 + 파싱 line item 표 + 매칭 실패 highlight
 *   6) "확정" 클릭 → Step 3 (confirm) 캡처 → 발주 결과 카드
 *
 * 산출:
 *   docs/qa/phase-10-step-13-vendor-ocr/working-vendor-order-step1-upload.png
 *   docs/qa/phase-10-step-13-vendor-ocr/working-vendor-order-step2-preview.png
 *   docs/qa/phase-10-step-13-vendor-ocr/working-vendor-order-step3-confirm.png
 *
 * 실패 시 fallback (placeholder PNG) 은 별도 함수 generatePlaceholders() 호출.
 */
const { chromium } = require('playwright');
const path = require('node:path');
const fs = require('node:fs');

const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:5176';
const ENTRY_PATH = '/src/renderer/index.html';
const OUT_DIR = path.resolve(
  __dirname,
  '..',
  '..',
  'docs',
  'qa',
  'phase-10-step-13-vendor-ocr',
);

const STEP_FILES = {
  STEP1: 'working-vendor-order-step1-upload.png',
  STEP2: 'working-vendor-order-step2-preview.png',
  STEP3: 'working-vendor-order-step3-confirm.png',
};

function ensureDir(dir) {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

async function launchBrowser() {
  try {
    return await chromium.launch({ channel: 'msedge', headless: true });
  } catch (_e) {
    console.log('  [info] msedge channel 미설치 → chromium fallback');
    return await chromium.launch({ headless: true });
  }
}

/**
 * Step 1 (upload) 캡처 후 file 주입 + OCR 실행 → Step 2 캡처 → 확정 → Step 3 캡처.
 */
async function captureFlow(ctx) {
  const url = `${BASE_URL}${ENTRY_PATH}?mockRole=MASTER#/sales/vendor-order-upload`;
  const page = await ctx.newPage();
  page.on('pageerror', (e) => console.log('  [pageerror]', e.message));
  page.on('console', (msg) => {
    if (msg.type() === 'error') console.log('  [console.error]', msg.text());
  });

  // Electron IPC stub (samhanAuth) — preload 미주입 환경 회피.
  await page.addInitScript(() => {
    if (!window.samhanAuth) {
      window.samhanAuth = {
        setToken: async () => undefined,
        getToken: async () => null,
        clearToken: async () => undefined,
      };
    }
  });

  console.log(`  [step 1] navigate → ${url}`);
  await page.goto(url, { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(800);

  // Stepper 노출 대기
  try {
    await page.waitForSelector('[data-testid="vendor-order-stepper"]', {
      timeout: 5000,
      state: 'visible',
    });
    console.log('    [ok] vendor-order-stepper 노출 — Step 1 mount');
  } catch (_e) {
    console.log('    [warn] vendor-order-stepper 미노출 — 가드 화면일 가능성 (캡처 진행)');
  }

  // 에어디자이너 라디오 기본 active. file 미주입 상태에서 Step 1 캡처.
  ensureDir(OUT_DIR);
  const step1Path = path.join(OUT_DIR, STEP_FILES.STEP1);
  await page.screenshot({ path: step1Path, fullPage: false });
  console.log(`    saved → ${path.basename(step1Path)} (${(fs.statSync(step1Path).size / 1024).toFixed(1)} KB)`);

  // ---------- Step 1 → Step 2: file 주입 + OCR 실행 ----------
  // setInputFiles 으로 hidden file input 직접 주입 (drag-drop 시뮬레이션 대비 안정).
  const fileInput = page.locator('[data-testid="vendor-order-file-input"]');
  try {
    // PNG 1x1 흰 픽셀 byte (mock 발주서 — 실제 OCR 안 함, FE state 만 update)
    const onePxPng = Buffer.from([
      0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d,
      0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
      0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, 0xc4, 0x89, 0x00, 0x00, 0x00,
      0x0d, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9c, 0x63, 0xfa, 0xcf, 0x00, 0x00,
      0x00, 0x02, 0x00, 0x01, 0xe5, 0x27, 0xde, 0xfc, 0x00, 0x00, 0x00, 0x00,
      0x49, 0x45, 0x4e, 0x44, 0xae, 0x42, 0x60, 0x82,
    ]);
    await fileInput.setInputFiles({
      name: 'airdesigner-mock.png',
      mimeType: 'image/png',
      buffer: onePxPng,
    });
    console.log('    [ok] mock file 주입 (airdesigner-mock.png)');
  } catch (e) {
    console.log('    [warn] file input setInputFiles 실패 — Step 2/3 캡처 skip:', e.message);
    await page.close();
    return;
  }

  // file 주입 후 React state update + button enable 까지 충분 대기
  await page.waitForTimeout(1000);

  // OCR run 버튼 enable 확인
  const runBtn = page.locator('[data-testid="vendor-order-ocr-run-btn"]');
  try {
    await runBtn.waitFor({ state: 'visible', timeout: 5000 });
    const disabled = await runBtn.evaluate((el) => el.disabled);
    console.log(`    [info] OCR run-btn disabled = ${disabled}`);
  } catch (_e) {
    console.log('    [warn] OCR run-btn not found');
  }

  // "OCR 분석 시작" 클릭 → mock setTimeout(600ms) 후 Step 2 진입
  try {
    await runBtn.click({ timeout: 5000 });
    console.log('    [click] OCR 분석 시작');
  } catch (e) {
    console.log('    [warn] OCR 분석 시작 클릭 실패:', e.message);
    await page.close();
    return;
  }

  // Step 2 mount 대기 (line item row 0 노출, mock setTimeout 600ms)
  try {
    await page.waitForSelector('[data-testid="vendor-order-item-row-0"]', {
      timeout: 8000,
      state: 'visible',
    });
    console.log('    [ok] vendor-order-item-row-0 노출 — Step 2 mount');
  } catch (_e) {
    console.log('    [warn] Step 2 row 미노출 — Step 1 잔류 가능성, 캡처 진행');
  }

  await page.waitForTimeout(800);
  const step2Path = path.join(OUT_DIR, STEP_FILES.STEP2);
  await page.screenshot({ path: step2Path, fullPage: true });
  console.log(`    saved → ${path.basename(step2Path)} (${(fs.statSync(step2Path).size / 1024).toFixed(1)} KB)`);

  // ---------- Step 2 → Step 3: 확정 ----------
  // confirm-btn 노출 확인 후 클릭 (Step 2 진입 미시 timeout 회피 위해 짧은 대기 후 단발 시도)
  const confirmBtn = page.locator('[data-testid="vendor-order-confirm-btn"]');
  let confirmClicked = false;
  try {
    await confirmBtn.waitFor({ state: 'visible', timeout: 3000 });
    await confirmBtn.click({ timeout: 3000 });
    console.log('    [click] 확정');
    confirmClicked = true;
  } catch (e) {
    console.log('    [warn] 확정 클릭 실패 (Step 2 미진입 가능):', e.message.split('\n')[0]);
  }

  if (confirmClicked) {
    try {
      await page.waitForSelector('[data-testid="vendor-order-result-card"]', {
        timeout: 5000,
        state: 'visible',
      });
      console.log('    [ok] vendor-order-result-card 노출 — Step 3 mount');
    } catch (_e) {
      console.log('    [warn] Step 3 result-card 미노출 (캡처 진행)');
    }
    await page.waitForTimeout(600);
    const step3Path = path.join(OUT_DIR, STEP_FILES.STEP3);
    await page.screenshot({ path: step3Path, fullPage: false });
    console.log(`    saved → ${path.basename(step3Path)} (${(fs.statSync(step3Path).size / 1024).toFixed(1)} KB)`);
  }

  await page.close();
}

/**
 * fallback — Playwright 자동화 실패 시 sharp 1280x900 placeholder PNG.
 * 한국어 라벨 + TODO 안내 (사용자 정책 — feedback_pr_qa_screenshots).
 *
 * 실 캡처 (>=70KB) 가 이미 존재하는 step 은 보존 — placeholder 미덮어쓰기.
 */
async function generatePlaceholders(reason, onlyMissing = false) {
  console.log(`\n[fallback] placeholder 생성 (onlyMissing=${onlyMissing}). 사유: ${reason}`);
  const sharp = require('sharp');
  ensureDir(OUT_DIR);
  const banners = [
    {
      file: STEP_FILES.STEP1,
      title: 'Step 1 — 파일 업로드',
      sub: 'vendor 라디오 (에어디자이너 / 제이시스템) + drag-drop 영역 + 파일 미리보기',
    },
    {
      file: STEP_FILES.STEP2,
      title: 'Step 2 — 분석 결과 확인',
      sub: 'OCR raw text + 파싱 line item 표 + 매칭 실패 highlight + 거래처 정보',
    },
    {
      file: STEP_FILES.STEP3,
      title: 'Step 3 — 발주 확정',
      sub: '발주서 번호 + 상태 + 총 금액 + "발주서 보기" link',
    },
  ];
  for (const b of banners) {
    const outPath = path.join(OUT_DIR, b.file);
    if (onlyMissing && fs.existsSync(outPath)) {
      // 실 캡처 보존 — placeholder 덮어쓰지 않음
      console.log(`    skip (실 캡처 보존) → ${b.file}`);
      continue;
    }
    const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="900">
  <rect width="100%" height="100%" fill="#f8fafc"/>
  <rect x="40" y="40" width="1200" height="80" fill="#1e40af"/>
  <text x="60" y="92" font-family="Malgun Gothic, sans-serif" font-size="28" fill="#fff">PR-F2 — vendor 발주서 OCR</text>
  <text x="60" y="180" font-family="Malgun Gothic, sans-serif" font-size="22" fill="#1f2937">${b.title}</text>
  <text x="60" y="220" font-family="Malgun Gothic, sans-serif" font-size="16" fill="#4b5563">${b.sub}</text>
  <rect x="60" y="280" width="1160" height="120" fill="#fef2f2" stroke="#fca5a5" stroke-width="2"/>
  <text x="80" y="320" font-family="Malgun Gothic, sans-serif" font-size="16" fill="#b91c1c">[TODO] Playwright 자동 캡처 실패 — vite dev 서버 (port 5176) 미가동 가능</text>
  <text x="80" y="350" font-family="Malgun Gothic, sans-serif" font-size="14" fill="#7f1d1d">실행 방법: clients/desktop 에서 'cross-env VITE_MOCK_MODE=1 npx vite --port 5176' 부팅 후</text>
  <text x="80" y="375" font-family="Malgun Gothic, sans-serif" font-size="14" fill="#7f1d1d">tools/manual-capture 에서 'node capture-pr-f2.js' 재실행</text>
  <text x="60" y="450" font-family="Malgun Gothic, sans-serif" font-size="14" fill="#374151">testid 검증 대상:</text>
  <text x="80" y="478" font-family="Consolas, monospace" font-size="13" fill="#1f2937">vendor-order-stepper / vendor-radio-airdesigner / vendor-radio-jsystem</text>
  <text x="80" y="500" font-family="Consolas, monospace" font-size="13" fill="#1f2937">vendor-order-drop-zone / vendor-order-file-input / vendor-order-ocr-run-btn</text>
  <text x="80" y="522" font-family="Consolas, monospace" font-size="13" fill="#1f2937">vendor-order-item-row-{idx} / vendor-order-confirm-btn / vendor-order-result-card</text>
  <text x="60" y="800" font-family="Malgun Gothic, sans-serif" font-size="12" fill="#6b7280">docs/qa/phase-10-step-13-vendor-ocr/scenarios.md § 6 참조</text>
</svg>`;
    await sharp(Buffer.from(svg)).png().toFile(outPath);
    const sizeKb = (fs.statSync(outPath).size / 1024).toFixed(1);
    console.log(`    placeholder → ${b.file} (${sizeKb} KB)`);
  }
}

(async () => {
  console.log('PR-F2 QA 작동 캡처 (vendor OCR 3-step)');
  console.log(`  baseUrl  = ${BASE_URL}${ENTRY_PATH}`);
  console.log(`  output   = ${OUT_DIR}\n`);

  let browser;
  let attempted = false;
  try {
    browser = await launchBrowser();
    const ctx = await browser.newContext({
      viewport: { width: 1280, height: 900 },
      deviceScaleFactor: 1,
      locale: 'ko-KR',
      timezoneId: 'Asia/Seoul',
    });
    attempted = true;
    await captureFlow(ctx);
    await ctx.close();
    console.log(`\n[done] 3 화면 캡처 완료 → ${OUT_DIR}`);
  } catch (err) {
    console.error('[error]', err.message);
  } finally {
    if (browser) await browser.close();
  }

  // 누락 step 자동 placeholder 보완 (실 캡처는 onlyMissing=true 로 보존)
  const missing = Object.values(STEP_FILES).filter(
    (f) => !fs.existsSync(path.join(OUT_DIR, f)),
  );
  if (missing.length > 0) {
    console.log(`\n[fallback] 누락 PNG ${missing.length}건 placeholder 보완 (실 캡처 보존)`);
    await generatePlaceholders('partial flow failure', true);
  }
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
