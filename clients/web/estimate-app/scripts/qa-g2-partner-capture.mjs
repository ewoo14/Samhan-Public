// G2 QA — estimate-app 거래처 검색이 partner-service DB에서 채워지는 실 화면 캡처.
import { chromium } from 'playwright';
import fs from 'fs';

const OUT = 'docs/qa/estimate-partner-manager-db/screenshots';
fs.mkdirSync(OUT, { recursive: true });
const URL = 'http://localhost:5183/?email=dev_master@samhan-air.com';
const report = {};

const browser = await chromium.launch();
const page = await browser.newPage();
await page.setViewportSize({ width: 1440, height: 900 });
try {
  await page.goto(URL, { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  // 거래처 글로벌 데이터 로드 대기 (RPC getCustomerDataAsync)
  await page
    .waitForFunction(() => Array.isArray(window.CUSTOMERS) && window.CUSTOMERS.length > 0, { timeout: 60000 })
    .catch(() => {});
  report.custCount = await page.evaluate(() => (Array.isArray(window.CUSTOMERS) ? window.CUSTOMERS.length : 'n/a'));

  const search = await page.$('#custSearch');
  if (!search) {
    report.error = '#custSearch not found';
  } else {
    // 1) "동양" 검색 → 드롭다운
    await search.click();
    await search.type('동양', { delay: 90 });
    await page.waitForTimeout(1500);
    report.dropdown_dongyang = await page
      .$eval('#custSuggestions', (el) => el.innerText.split('\n').map((s) => s.trim()).filter(Boolean).slice(0, 10))
      .catch(() => []);
    await page.screenshot({ path: `${OUT}/01-partner-search-dropdown.png` });

    // 2) "한울" 검색 → 드롭다운
    await search.fill('');
    await search.type('한울', { delay: 90 });
    await page.waitForTimeout(1500);
    report.dropdown_hanwool = await page
      .$eval('#custSuggestions', (el) => el.innerText.split('\n').map((s) => s.trim()).filter(Boolean).slice(0, 10))
      .catch(() => []);
    await page.screenshot({ path: `${OUT}/02-partner-search-hanwool.png` });

    // 3) 첫 후보 선택 → 거래처 정보 채움
    const clicked = await page.evaluate(() => {
      const box = document.querySelector('#custSuggestions');
      if (!box) return false;
      const item = box.querySelector('div, li, .autocomplete-item');
      if (!item) return false;
      item.click();
      return true;
    });
    report.selectedItem = clicked;
    await page.waitForTimeout(1000);
    await page.screenshot({ path: `${OUT}/03-partner-selected.png` });
    report.selectedFields = await page.evaluate(() => {
      const v = (id) => {
        const el = document.getElementById(id);
        return el ? (el.value || el.textContent || '').trim() : null;
      };
      return { search: v('custSearch'), rep: v('custRep'), tel: v('custTel'), group: v('custGroup'), note: v('custNote') };
    });
  }
} catch (e) {
  report.exception = String(e && e.message);
} finally {
  await browser.close();
}
console.log(JSON.stringify(report, null, 2));
