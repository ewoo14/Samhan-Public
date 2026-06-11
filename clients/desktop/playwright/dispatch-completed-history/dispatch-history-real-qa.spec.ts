/**
 * #463 완료배차 내역 뷰 — 실서버(mock OFF) QA 캡처.
 *
 * 실 게이트웨이(:8080) + 실 DISPATCHED 데이터(dispatch flow + arologis confirm 시뮬로 생성)로
 * 완료배차 목록/상세가 실 화면에 렌더됨을 실증. mock 없음(no-fake-data). FE real-mode dev :5178.
 *
 * 산출: docs/qa/dispatch-completed-history/history-list.png, history-detail.png
 */
import { expect, test, type Page } from '@playwright/test'
import * as path from 'path'
import * as fs from 'fs'
import { fileURLToPath } from 'url'

const _dirname =
  typeof __dirname !== 'undefined' ? __dirname : path.dirname(fileURLToPath(import.meta.url))
const BASE_URL = process.env['AUDIT_BASE_URL'] ?? 'http://localhost:5178'
const API_BASE = process.env['API_BASE'] ?? 'http://localhost:8080'
const PASSWORD = process.env['DEV_PASSWORD'] ?? 'dev_p05_pass!'
const SHOTS = path.resolve(_dirname, '../../../../docs/qa/dispatch-completed-history')
fs.mkdirSync(SHOTS, { recursive: true })

interface LoginResult { token: string; role: string; userId: string; displayName: string }

async function realLogin(page: Page, loginId: string): Promise<LoginResult> {
  const res = await page.request.post(`${API_BASE}/auth/login`, { data: { loginId, password: PASSWORD } })
  expect(res.ok(), `로그인 실패(${loginId}): HTTP ${res.status()}`).toBeTruthy()
  const body = await res.json()
  const d = body.data ?? {}
  return { token: d.token ?? '', role: d.role ?? '', userId: d.userId ?? '', displayName: d.displayName ?? loginId }
}

async function installAuthStub(page: Page, login: LoginResult): Promise<void> {
  await page.addInitScript(
    ({ tok, r, uid, name }: { tok: string; r: string; uid: string; name: string }) => {
      Object.defineProperty(window, 'samhanAuth', {
        configurable: true,
        value: {
          getToken: async () => ({ token: tok, userId: uid, role: r, fullName: name, partnerCode: null }),
          setToken: async () => undefined,
          clearToken: async () => undefined,
        },
      })
    },
    { tok: login.token, r: login.role, uid: login.userId, name: login.displayName },
  )
}

test('완료배차 내역 목록 + 상세 실 게이트웨이 캡처 (dev_master)', async ({ page }) => {
  const pageErrors: string[] = []
  page.on('pageerror', (e) => pageErrors.push(e.message))

  const login = await realLogin(page, 'dev_master')
  await installAuthStub(page, login)

  await page.goto(`${BASE_URL}/#/dispatch-board/history`)
  await page.waitForSelector('[data-testid="dispatch-history-table"]', { timeout: 30000 })
  // 실 데이터(dispatchDate=2026-06-11) 포함 위해 날짜 범위 명시(브라우저 today 기준 기본 30일 범위 무관).
  await page.getByTestId('dispatch-history-from').fill('2025-01-01')
  await page.getByTestId('dispatch-history-to').fill('2026-12-31')
  await page.getByTestId('dispatch-history-filter-submit').click()
  // 실 DISPATCHED 1건(2026/06/11-1) 행 출현 대기.
  await page.waitForSelector('[data-testid^="dispatch-history-row-"]', { timeout: 15000 })
  await page.waitForTimeout(1000)
  await page.screenshot({ path: path.join(SHOTS, 'history-list.png'), fullPage: true })

  // 행 클릭 → 상세(차량그룹·전표·기사). arologisDispatchId drill-in.
  await page.locator('[data-testid^="dispatch-history-row-"]').first().click()
  await page.waitForTimeout(2000)
  await page.screenshot({ path: path.join(SHOTS, 'history-detail.png'), fullPage: true })

  // 조회 전용 — 변경(수정/취소 요청) 버튼 부재 단언(read-only 실증).
  const mutationBtns = await page.getByRole('button', { name: /수정 요청|취소 요청|배차 완료|재배차/ }).count()
  expect(mutationBtns, '완료배차 상세는 조회 전용(변경 버튼 0)').toBe(0)
  expect(pageErrors, `pageerror: ${pageErrors.join('; ')}`).toHaveLength(0)
})
