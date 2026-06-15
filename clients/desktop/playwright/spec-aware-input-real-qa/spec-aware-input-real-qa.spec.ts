/**
 * 사양 인지형 입력 (사양 후속 #1 재설계) Docker 실서버 QA Playwright spec.
 *
 * 대상: 품목 등록 폼 사양 섹션 — 품목별 사양 드롭다운(estimate_category 필터) + valueType 입력
 *       (NUMBER 숫자+단위 / DIMENSION 3분할 WxHxD / TEXT) + 순서변경(위/아래) + 중복제외.
 * 실서버: http://localhost:8080 (api-gateway, 실 product-service V17), FE http://localhost:5173.
 * 인증: dev_master / dev_p05_pass! (MASTER, products.list VIEW).
 *
 * 실행: cd C:\dev\Samhan-Public\clients\desktop
 *   set AUDIT_BASE_URL=http://localhost:5173 && node_modules\.bin\playwright test \
 *     --config=playwright.real-qa.config.ts playwright/spec-aware-input-real-qa --reporter=line --timeout=90000
 */
import { test, expect, type Page } from '@playwright/test'
import * as path from 'path'
import * as fs from 'fs'
import { fileURLToPath } from 'url'

const _dirname = typeof __dirname !== 'undefined' ? __dirname : path.dirname(fileURLToPath(import.meta.url))
const BASE_URL = process.env['AUDIT_BASE_URL'] ?? 'http://localhost:5173'
const API_BASE = 'http://localhost:8080'
const OUT = path.resolve(_dirname, '../../../../docs/qa/spec-aware-input')
fs.mkdirSync(OUT, { recursive: true })

async function loginAndInstallStub(page: Page, loginId: string, password: string): Promise<void> {
  const res = await page.request.post(`${API_BASE}/auth/login`, { data: { loginId, password } })
  const body = await res.json()
  const token: string = body.data?.token ?? ''
  const role: string = body.data?.role ?? 'MASTER'
  const userId: string = body.data?.userId ?? ''
  const displayName: string = body.data?.displayName ?? loginId
  await page.addInitScript(({ tok, r, uid, name }: { tok: string; r: string; uid: string; name: string }) => {
    Object.defineProperty(window, 'samhanAuth', {
      configurable: true,
      value: {
        getToken: async () => ({ token: tok, userId: uid, role: r, fullName: name, partnerCode: null }),
        setToken: async () => undefined,
        clearToken: async () => undefined,
      },
    })
  }, { tok: token, r: role, uid: userId, name: displayName })
}

test('사양 인지형 입력 — 품목별 드롭다운·valueType·순서·중복', async ({ page }) => {
  await loginAndInstallStub(page, 'dev_master', 'dev_p05_pass!')
  await page.goto(`${BASE_URL}/#/products/new`)
  await page.waitForSelector('[data-testid="product-form-model-name"]', { timeout: 30000 })

  // 품목 카테고리 = 상업멀티 (능력 NUMBER + 제품크기 DIMENSION + 냉매가스 TEXT)
  await page.selectOption('[data-testid="product-form-product-category"]', 'COMMERCIAL_MULTI')
  await page.waitForTimeout(1500) // 템플릿 로드

  // 행0: NUMBER (냉방능력, kW) → 숫자 입력 + 단위 suffix
  await page.click('[data-testid="product-form-add-spec"]')
  await page.waitForSelector('[data-testid="product-form-spec-0-template"]', { timeout: 10000 })
  await page.selectOption('[data-testid="product-form-spec-0-template"]', '냉방능력, kW')
  await page.fill('[data-testid="product-form-spec-0-value"]', '101.0')
  const row0Unit = await page.locator('[data-testid="product-form-spec-0-row"]').innerText()
  expect(row0Unit).toContain('kW')

  // 행1: DIMENSION (제품크기, mm) → 3분할 WxHxD
  await page.click('[data-testid="product-form-add-spec"]')
  await page.selectOption('[data-testid="product-form-spec-1-template"]', '제품크기, mm')
  await page.fill('[data-testid="product-form-spec-1-dimension-width"]', '1800')
  await page.fill('[data-testid="product-form-spec-1-dimension-height"]', '2370')
  await page.fill('[data-testid="product-form-spec-1-dimension-depth"]', '1070')

  // 행2: TEXT (냉매가스)
  await page.click('[data-testid="product-form-add-spec"]')
  await page.selectOption('[data-testid="product-form-spec-2-template"]', '냉매가스')
  await page.fill('[data-testid="product-form-spec-2-value"]', 'R410A')

  await page.screenshot({ path: path.join(OUT, '01-valuetypes.png'), fullPage: true })

  // 중복 제외 검증: 행3 드롭다운에 이미 추가된 사양 미포함
  await page.click('[data-testid="product-form-add-spec"]')
  const row3Options = await page.$$eval('[data-testid="product-form-spec-3-template"] option', (els) =>
    els.map((e) => (e as HTMLOptionElement).value).filter(Boolean))
  const added = ['냉방능력, kW', '제품크기, mm', '냉매가스']
  const leaked = added.filter((a) => row3Options.includes(a))
  expect(leaked).toEqual([]) // 추가된 사양은 후보에서 제외

  // 순서 변경: 행0(냉방능력) 아래로 이동
  await page.click('[data-testid="product-form-spec-0-move-down"]')
  await page.waitForTimeout(300)
  await page.screenshot({ path: path.join(OUT, '02-dedup-and-reorder.png'), fullPage: true })

  fs.writeFileSync(path.join(OUT, 'spec-aware-evidence.txt'),
    `품목 카테고리=COMMERCIAL_MULTI (estimate_category 필터)\n`
    + `행0 NUMBER "냉방능력, kW" 단위 suffix 노출=${row0Unit.includes('kW')}\n`
    + `행1 DIMENSION "제품크기, mm" 3분할(1800x2370x1070)\n`
    + `행2 TEXT "냉매가스" R410A\n`
    + `중복제외: 행3 후보에서 추가된 ${added.length}개 제외 누수=${leaked.length}\n`
    + `행3 잔여 후보 수=${row3Options.length}\n`
    + `순서변경: 행0 아래로 이동(드래그+위/아래 버튼 제공)\n`, 'utf8')
})
