/**
 * Playwright Edge 캡처 스크립트 — PR QA 가드용.
 *
 * 사전 조건:
 *  - npm install (devDependencies 포함)
 *  - .env 작성 (실 시크릿 또는 dummy 값)
 *  - npm start (백그라운드, port 5184)
 *
 * 실행:
 *  node scripts/qa-capture.mjs [outDir]
 *
 * 출력:
 *  docs/qa/migration-fe-legacy-v2-quote-order/estimate-{home,gate,healthz}.png
 */

import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const OUT = process.argv[2] || path.resolve('../../../docs/qa/migration-fe-legacy-v2-quote-order');
const BASE = process.env.QA_BASE_URL || 'http://localhost:5184';
fs.mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await ctx.newPage();

const shots = [
  { name: 'estimate-home', url: '/', wait: 2000 },
  { name: 'estimate-healthz', url: '/healthz', wait: 200 },
];

for (const shot of shots) {
  try {
    await page.goto(BASE + shot.url, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await page.waitForTimeout(shot.wait);
    await page.screenshot({ path: path.join(OUT, `${shot.name}.png`), fullPage: true });
    console.log(`[qa] ${shot.name}.png`);
  } catch (e) {
    console.warn(`[qa] ${shot.name} 실패: ${e.message}`);
  }
}

await browser.close();
