/**
 * Playwright Edge 캡처 — order-legacy.
 */

import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const OUT = process.argv[2] || path.resolve('../../../docs/qa/migration-fe-legacy-v2-quote-order');
const BASE = process.env.QA_BASE_URL || 'http://localhost:5185';
fs.mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await ctx.newPage();

const shots = [
  { name: 'order-home', url: '/', wait: 2000 },
  { name: 'order-healthz', url: '/healthz', wait: 200 },
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
