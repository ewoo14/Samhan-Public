/**
 * 슬4c — 그룹웨어 생성 기본 결재라인 프리필 UI 캡처 (VITE_MOCK_MODE, mockRole=MASTER+mockPerms).
 *
 * 생성 페이지(/groupware/approvals/new)에서 문서종류(템플릿) "지출결의서" 선택 시
 * default-approvers(GROUPWARE_EXPENSE_REPORT)가 결재자 칩에 자동 프리필됨을 캡처.
 * config admin-게이트와 무관(생성은 일반 사용자 페이지)하나, QA-env canAccess 보장 위해 mockPerms grant.
 * 실 백엔드 아님(mock 모드, 명시) — 실 게이트웨이 default-approvers는 슬4a에서 라이브 검증 완료.
 */
import * as path from 'path'
import * as fs from 'fs'
import { fileURLToPath } from 'url'
import { test, expect, type Page } from '@playwright/test'

const BASE = process.env['AUDIT_BASE_URL'] ?? 'http://127.0.0.1:5175'
const _dirname = path.dirname(fileURLToPath(import.meta.url))
const DIR = path.resolve(_dirname, '../../../../docs/qa/groupware-approval-line-config-s4c')
fs.mkdirSync(DIR, { recursive: true })
let seq = 0
async function cap(page: Page, name: string): Promise<void> {
  seq++
  await page.screenshot({ path: path.join(DIR, `${String(seq).padStart(2, '0')}-${name}.png`), fullPage: true })
}

test('S4c: 그룹웨어 생성 — 문서종류 선택 시 기본 결재라인 프리필', async ({ page }) => {
  const perms = Buffer.from(JSON.stringify([
    { pageCode: 'groupware.approvals', view: true, edit: true },
  ]), 'utf-8').toString('base64')
  await page.goto(`${BASE}/?mockRole=MASTER&mockPerms=${perms}#/groupware/approvals/new`, { waitUntil: 'networkidle', timeout: 30_000 })
  await page.waitForTimeout(1500)
  await page.evaluate(() => { window.location.hash = '#/groupware/approvals/new' })
  await page.waitForTimeout(2500)
  console.log('[URL]', page.url())
  await cap(page, 'create-initial')

  const select = page.getByTestId('groupware-approval-create-template')
  await expect(select).toBeVisible({ timeout: 10_000 })
  // 문서종류 "지출결의서"(EXPENSE_REPORT) 선택 → default-approvers 프리필
  await select.selectOption({ label: '지출결의서' })
  await page.waitForTimeout(2000)
  await cap(page, 'create-expense-report-prefilled')
  const body = (await page.locator('body').textContent()) ?? ''
  console.log('[BODY]', body.replace(/\s+/g, ' ').slice(0, 400))
})
