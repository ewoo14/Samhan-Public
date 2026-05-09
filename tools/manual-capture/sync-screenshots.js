/**
 * SamhanLogis 운영자 매뉴얼 — 캡처 결과 일괄 sync + placeholder 폴백.
 *
 * 동작:
 *   1) docs/manual/**\/*.md 모든 image link 추출 (../screenshots/<섹션>/<file>.png)
 *   2) tools/manual-capture/output/<id>.png → docs/manual/screenshots/<섹션>/<file>.png 로 복사 (CAPTURE_MAP 참조)
 *      - id 매칭 우선, 매칭 없으면 placeholder 폴백.
 *   3) 모든 image link 의 실 PNG 존재 보장 — 누락 path 에 _placeholder-screenshot-pending.png copy.
 *   4) capture log 출력 (실 캡처 / placeholder / 미사용 capture).
 *
 * 사용:
 *   node tools/manual-capture/sync-screenshots.js
 *
 * 전제:
 *   - tools/manual-capture/output/ 에 capture-desktop.js / capture-mobile.js 산출물 존재
 *   - tools/manual-capture/output/_placeholder-screenshot-pending.png 존재
 *     (없으면 generate-placeholder.js 자동 호출)
 */
const path = require('node:path');
const fs = require('node:fs');
const { execSync } = require('node:child_process');

const ROOT = path.resolve(__dirname, '..', '..');
const MANUAL_DIR = path.join(ROOT, 'docs', 'manual');
const SCREENSHOTS_DIR = path.join(MANUAL_DIR, 'screenshots');
const OUTPUT_DIR = path.join(__dirname, 'output');
const PLACEHOLDER = path.join(OUTPUT_DIR, '_placeholder-screenshot-pending.png');

/**
 * capture id (capture.config.json) → 매뉴얼 image path 매핑.
 * 한 capture 가 여러 매뉴얼 image 를 채울 수 있다 (예: 00-login → login-full / login-id-box / ...).
 *
 * 매핑 없는 매뉴얼 image 는 placeholder 적용.
 */
const CAPTURE_MAP = {
  '00-login': [
    '00-시작/01-login-full.png',
    '00-시작/01-login-id-box.png',
    '00-시작/01-login-pw-box.png',
  ],
  '00-main-sidebar': [
    '00-시작/01-login-success.png',
    '00-시작/02-main-full.png',
    '00-시작/02-main-sidebar.png',
    '00-시작/02-main-header.png',
  ],
  '01-warehouses': [
    '02-창고/03-inventory-menu.png',
  ],
  '02-sales-list': [
    '01-영업/03-slip-list-entry.png',
    '01-영업/03-slip-new-button.png',
    '01-영업/05-slip-source-filter.png',
    '02-창고/02-warehouse-outbound-menu.png',
  ],
  '03-purchases-list': [
    '02-창고/01-warehouse-inbound-menu.png',
    '02-창고/01-inbound-slip-list.png',
  ],
  '04-transfers-list': [
    // 재고이동은 별도 매뉴얼 미작성 — 백로그
  ],
  '05-link-dispatch': [
    // 링크발송 매뉴얼 미작성 — 백로그
  ],
  '06-sales-estimates': [
    // 견적서 매뉴얼 미작성 — 백로그
  ],
  '07-sales-partner-orders': [
    '01-영업/05-partner-order-main.png',
  ],
  '08-sales-order-approvals': [
    // 주문서 승인 매뉴얼 미작성 — 백로그
  ],
  '09-sales-partner-dc-config': [
    // DC 설정 매뉴얼 미작성 — 백로그
  ],
  '10-accounting-accounts': [
    // 계정과목 매뉴얼 미작성 — 백로그
  ],
  '11-accounting-journals': [
    '03-회계/01-journal-menu.png',
    '03-회계/01-journal-new-button.png',
    '03-회계/01-journal-header.png',
    '03-회계/01-journal-lines.png',
    '03-회계/01-journal-balance-check.png',
    '03-회계/01-journal-save-button.png',
  ],
  '12-accounting-balances': [
    '03-회계/02-report-menu.png',
    '03-회계/02-trial-balance.png',
  ],
  '20-mobile-driver-dashboard': [
    '04-모바일/01-driver-dashboard-list.png',
    '04-모바일/01-driver-dashboard-refresh.png',
    '04-모바일/01-driver-app-mode-toggle.png',
  ],
  '21-mobile-driver-signature': [
    '04-모바일/01-driver-signature-canvas.png',
  ],
  '22-mobile-driver-location': [
    '04-모바일/01-driver-tracking-screen.png',
  ],
  '23-mobile-estimate-webview': [
    // 영업원 견적 webview — 매뉴얼 별도 미작성, 백로그
  ],
};

function ensureDir(dir) {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

/**
 * docs/manual 의 모든 .md 에서 ../screenshots/<섹션>/<file>.png 형태의 image 경로 추출.
 * screenshots/README.md 자체는 example 이므로 제외.
 */
function collectManualImageRefs() {
  const refs = new Set();
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (entry.name === 'screenshots') continue;  // README.md 의 example link 무시
        walk(full);
      } else if (entry.isFile() && entry.name.endsWith('.md')) {
        const md = fs.readFileSync(full, 'utf8');
        const re = /!\[[^\]]*\]\(\.\.\/screenshots\/([^)]+\.(?:png|jpg|jpeg|gif|webp))\)/gi;
        let m;
        while ((m = re.exec(md)) !== null) {
          refs.add(m[1].replace(/\\/g, '/'));
        }
      }
    }
  }
  walk(MANUAL_DIR);
  return Array.from(refs).sort();
}

/**
 * placeholder 파일 보장 — generate-placeholder.js 가 산출.
 */
function ensurePlaceholder() {
  if (fs.existsSync(PLACEHOLDER)) return;
  console.log('  [info] placeholder 미존재 → generate-placeholder.js 실행');
  execSync('node ' + JSON.stringify(path.join(__dirname, 'generate-placeholder.js')), {
    cwd: __dirname,
    stdio: 'inherit',
  });
}

function copyFile(src, dest) {
  ensureDir(path.dirname(dest));
  fs.copyFileSync(src, dest);
}

(async () => {
  console.log('SamhanLogis 매뉴얼 screenshot sync\n');

  if (!fs.existsSync(OUTPUT_DIR)) {
    console.error(`[abort] output dir 미존재: ${OUTPUT_DIR}`);
    console.error('       먼저 capture-desktop.js / capture-mobile.js 실행 필요');
    process.exit(1);
  }
  ensurePlaceholder();
  ensureDir(SCREENSHOTS_DIR);

  // 1) 매뉴얼 image refs 수집
  const manualRefs = collectManualImageRefs();
  console.log(`매뉴얼 image refs:  ${manualRefs.length} 개`);

  // 2) capture map 적용
  const fromCapture = [];
  const fromPlaceholder = [];
  const captureUnused = [];

  // capture id → 매뉴얼 path 적용
  for (const [captureId, manualPaths] of Object.entries(CAPTURE_MAP)) {
    const captured = path.join(OUTPUT_DIR, `${captureId}.png`);
    if (!fs.existsSync(captured)) {
      // capture 실패 → 모든 매뉴얼 path 에 placeholder
      for (const mp of manualPaths) {
        const dest = path.join(SCREENSHOTS_DIR, mp);
        copyFile(PLACEHOLDER, dest);
        fromPlaceholder.push({ manual: mp, reason: `capture id "${captureId}" 산출 실패` });
      }
      continue;
    }
    if (manualPaths.length === 0) {
      captureUnused.push(captureId);
      continue;
    }
    for (const mp of manualPaths) {
      const dest = path.join(SCREENSHOTS_DIR, mp);
      copyFile(captured, dest);
      fromCapture.push({ manual: mp, captureId });
    }
  }

  // 3) 누락된 매뉴얼 image — placeholder 폴백
  const filled = new Set([...fromCapture.map((x) => x.manual), ...fromPlaceholder.map((x) => x.manual)]);
  for (const ref of manualRefs) {
    const dest = path.join(SCREENSHOTS_DIR, ref);
    if (!fs.existsSync(dest)) {
      copyFile(PLACEHOLDER, dest);
      fromPlaceholder.push({ manual: ref, reason: 'CAPTURE_MAP 매핑 없음' });
    } else if (!filled.has(ref)) {
      // 이미 다른 PR 에서 만든 PNG 존재 — skip (덮어쓰지 않음)
    }
  }

  // 4) 보고
  console.log('\n=== 캡처 sync 결과 ===');
  console.log(`\n[실 캡처 적용] ${fromCapture.length} 개`);
  for (const x of fromCapture) {
    console.log(`  ${x.captureId} → ${x.manual}`);
  }
  console.log(`\n[placeholder 적용] ${fromPlaceholder.length} 개`);
  for (const x of fromPlaceholder) {
    console.log(`  ${x.manual} (${x.reason})`);
  }
  console.log(`\n[미사용 capture (매뉴얼 매핑 X)] ${captureUnused.length} 개`);
  for (const id of captureUnused) {
    console.log(`  ${id}.png — 매뉴얼 .md 작성 시 CAPTURE_MAP 갱신 필요`);
  }

  // 5) 매뉴얼 ref 누락/존재 검증
  let missing = 0;
  for (const ref of manualRefs) {
    const full = path.join(SCREENSHOTS_DIR, ref);
    if (!fs.existsSync(full)) {
      console.log(`  [WARN] 매뉴얼 link 누락: ${ref}`);
      missing += 1;
    }
  }
  console.log(`\n매뉴얼 image link 검증: ${manualRefs.length - missing}/${manualRefs.length} 존재`);

  console.log('\n[done] sync 완료. PR 에 docs/manual/screenshots/ 변경 commit 필요.');
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
