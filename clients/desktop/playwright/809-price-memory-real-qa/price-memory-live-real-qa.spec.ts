/**
 * #809 (거래처+품목) 최근단가 자동채움 — 실서버 GUI 라이브 QA (mock OFF).
 *
 * 실 게이트웨이(:8080) → 재빌드 slip-service(V58 partner_product_price_memory 실적용)
 * + partner-service(PartnerSummaryResponse.partnerId) → 실 Postgres. 합성/fixture 없음.
 *
 * ⚠️ 계정 정직 기록: 과업 브리프는 dev_master 를 지정했으나, 실 DB(auth_db) 조회 결과
 * "마스터" 권한그룹에는 `sales.slip.create` / `purchases.slip.edit` 행이 없다.
 * 실측: dev_master 로 POST /slips (유효 라인) → 403 "전표 변경 권한이 없습니다".
 * 즉 dev_master 는 전표 생성 자체가 불가한 계정이며 price-memory 403 은 기존 전표생성
 * 인가와 "동등"하게 동작한 결과(#809 회귀 아님). 본 QA 는 `sales.slip.create` +
 * `purchases.slip.edit` 전권을 가진 "매니저" 그룹 계정 dev_manager 로 수행한다.
 *
 * 실 시드 데이터(실 API 조회로 확인):
 *  - 거래처 A 한울냉열시스템(44f0cfc1-…-04ad5fa70922)
 *  - 거래처 B 국민건강보험공단(3b69ae15-…-21697c8945db)
 *  - 품목 X AJ030MXHNBC1 / 실외기_3HP 단배관(a046f235-…-e2d533e1ff08) 정가 1,470,700
 *  - 입력단가 P = 999,000 (정가와 명백히 다른 값 — 자동채움 출처를 정가와 구분)
 *
 * 단계별 캡처(docs/qa/809-partner-product-price-memory/).
 */
import { expect, test, type Page } from '@playwright/test'
import * as path from 'path'
import * as fs from 'fs'
import { execSync } from 'child_process'
import { fileURLToPath } from 'url'

const _dirname =
  typeof __dirname !== 'undefined' ? __dirname : path.dirname(fileURLToPath(import.meta.url))
const BASE_URL = process.env['QA_BASE_URL'] ?? 'http://localhost:5199'
const API_BASE = process.env['API_BASE'] ?? 'http://localhost:8080'
const PASSWORD = process.env['DEV_PASSWORD'] ?? 'dev_p05_pass!'
const ACCOUNT = 'dev_manager'
const SHOTS = path.resolve(_dirname, '../../../../docs/qa/809-partner-product-price-memory')
fs.mkdirSync(SHOTS, { recursive: true })

const PARTNER_A = { name: '한울냉열시스템', query: '한울냉열' }
const PARTNER_B = { name: '국민건강보험공단', query: '국민건강' }
const PRODUCT_X = { model: 'AJ030MXHNBC1', listPrice: '1470700' }
const PRICE_P = '999000'

async function capture(page: Page, name: string): Promise<void> {
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: false })
}

interface LoginResult { token: string; role: string; userId: string; displayName: string }

async function realLogin(page: Page, loginId: string): Promise<LoginResult> {
  const res = await page.request.post(`${API_BASE}/auth/login`, {
    data: { loginId, password: PASSWORD },
  })
  expect(res.ok(), `로그인 실패(${loginId}): HTTP ${res.status()}`).toBeTruthy()
  const d = (await res.json()).data ?? {}
  return {
    token: d.token ?? '',
    role: d.role ?? '',
    userId: d.userId ?? '',
    displayName: d.displayName ?? loginId,
  }
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

/** price-memory 호출을 실제로 관측한다(경로 렌더 ≠ 기능 동작 구분용). */
function trackPriceMemoryCalls(page: Page): string[] {
  const calls: string[] = []
  page.on('request', (req) => {
    if (req.url().includes('/slips/price-memory')) calls.push(req.url())
  })
  return calls
}

async function openSlipForm(page: Page, route: string): Promise<void> {
  await page.goto(`${BASE_URL}${route}`)
  await expect(page.getByRole('combobox', { name: '거래처' })).toBeVisible({ timeout: 30000 })
  await page.waitForTimeout(400)
}

/**
 * 자동완성 선택(실 키보드 조작 — ArrowDown+Enter).
 *
 * 두 가지 함정 회피:
 *  1) `getByRole('option')` 전역 조회는 화면 내 네이티브 `<select>`(출고구분 태그) option 과
 *     충돌 → 해당 listbox 로 스코프.
 *  2) 드롭다운은 portal floating layer 라서 라인 테이블처럼 페이지 하단 입력에서는 옵션이
 *     "outside of the viewport" 로 클릭 불가 → 마우스 클릭 대신 키보드 확정(실사용자 경로).
 */
async function pickAutocomplete(
  page: Page,
  name: string,
  listboxLabel: string,
  query: string,
): Promise<void> {
  const input = page.getByRole('combobox', { name })
  await input.scrollIntoViewIfNeeded()
  await input.click()
  await input.fill(query)
  const option = page.getByRole('listbox', { name: listboxLabel }).getByRole('option').first()
  await expect(option, `자동완성 옵션 미표시: ${name} / ${query}`).toBeVisible({ timeout: 20000 })
  await input.press('ArrowDown')
  await input.press('Enter')
  await expect(option).toBeHidden({ timeout: 10000 })
  await page.waitForTimeout(300)
}

async function pickWarehouse(page: Page): Promise<void> {
  const input = page.getByRole('combobox', { name: '출고 창고' })
  await input.scrollIntoViewIfNeeded()
  await input.click()
  const option = page.getByRole('listbox', { name: '창고 목록' }).getByRole('option').first()
  await expect(option, '창고 옵션 미표시').toBeVisible({ timeout: 20000 })
  await input.press('ArrowDown')
  await input.press('Enter')
  await page.waitForTimeout(200)
}

const unitPriceInput = (page: Page) => page.getByLabel('라인 1 단가')

/**
 * 단가 입력칸 실측값을 숫자만 남겨 비교한다(데스크톱 LineRow 는 천단위 콤마 포맷 —
 * 실측 "1,470,700"). 포맷과 무관하게 "어떤 값이 채워졌나" 를 판정한다.
 */
async function expectUnitPriceDigits(page: Page, expected: string): Promise<void> {
  await expect
    .poll(
      async () => ((await unitPriceInput(page).inputValue()) || '').replace(/[^0-9]/g, ''),
      { timeout: 15000, message: `단가 자동채움 기대값 ${expected}` },
    )
    .toBe(expected)
}

/** 실 Postgres 조회(검증 전용 — 화면 판정은 전부 실 GUI 로 수행). */
function psql(sql: string): string {
  return execSync(
    `docker exec samhan-postgres psql -U samhan -d slip_db -t -A -c "${sql.replace(/"/g, '\\"')}"`,
    { encoding: 'utf-8' },
  ).trim()
}

test.describe.serial('#809 최근단가 자동채움 — 실 GUI', () => {
  // 재실행 안전성 — 이전 라운드가 남긴 기억단가를 지워 "최초 = miss" 상태를 실제로 만든다.
  // (셋업만 DB 직접 수행. 자동채움 판정은 전부 실 GUI.)
  test.beforeAll(() => {
    psql('DELETE FROM partner_product_price_memory')
  })

  test('01~03 거래처A+품목X 정가채움 → 단가 P 직접입력 → 저장', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const calls = trackPriceMemoryCalls(page)
    const login = await realLogin(page, ACCOUNT)
    await installAuthStub(page, login)

    await openSlipForm(page, '/sales/new')
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(800)

    // ① 최초 저장 전 = 가격기억 miss → catalog 정가 채움
    await expectUnitPriceDigits(page, PRODUCT_X.listPrice)
    await capture(page, '01-partnerA-productX-catalog-list-price-1470700')
    console.log('[#809] 01 price-memory 호출:', calls.length, calls)

    // ② 단가 P 직접 입력(정가와 다른 값)
    await unitPriceInput(page).fill(PRICE_P)
    await page.getByLabel('라인 1 수량').fill('2')
    await expectUnitPriceDigits(page, PRICE_P)
    await capture(page, '02-partnerA-productX-manual-price-999000-entered')

    // ③ 저장 → 목록 이동
    await page.getByRole('button', { name: '저장' }).click()
    await page.waitForURL('**/sales', { timeout: 30000 })
    await page.waitForTimeout(1200)
    await capture(page, '03-slip-saved-redirect-to-sales-list')
    await ctx.close()
  })

  test('04 [핵심] 새 전표 — 거래처A+품목X → 저장단가 P 자동채움', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const calls = trackPriceMemoryCalls(page)
    const login = await realLogin(page, ACCOUNT)
    await installAuthStub(page, login)

    await openSlipForm(page, '/sales/new')
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)

    // 정가(1470700)가 아니라 직전 저장단가(999000)여야 한다 — 자동채움 출처 증명
    await expectUnitPriceDigits(page, PRICE_P)
    await capture(page, '04-KEY-new-slip-partnerA-productX-autofill-remembered-999000')
    expect(calls.length, 'price-memory 미호출 = 자동채움 경로 죽음').toBeGreaterThan(0)
    console.log('[#809] 04 price-memory 호출:', calls.length, calls)
    await ctx.close()
  })

  test('05 다른 거래처 B + 같은 품목 X → 정가(거래처별 격리)', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const login = await realLogin(page, ACCOUNT)
    await installAuthStub(page, login)

    await openSlipForm(page, '/sales/new')
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_B.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)

    await expectUnitPriceDigits(page, PRODUCT_X.listPrice)
    await capture(page, '05-partnerB-productX-isolated-list-price-1470700')
    await ctx.close()
  })

  test('06 override 보존 — 단가 선입력 라인은 자동채움이 덮어쓰지 않음', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const login = await realLogin(page, ACCOUNT)
    await installAuthStub(page, login)

    await openSlipForm(page, '/sales/new')
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)

    // 품목 선택 전에 단가를 먼저 입력해 둔다
    await unitPriceInput(page).fill('123456')
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)

    // 기억단가(999000)도 정가(1470700)도 아닌 사용자 입력값이 보존돼야 한다
    await expectUnitPriceDigits(page, '123456')
    await capture(page, '06-override-preserved-123456-not-overwritten')
    await ctx.close()
  })

  test('07 upsert — 같은 (거래처A,품목X) 재저장 시 행 갱신(중복행 없음)', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const login = await realLogin(page, ACCOUNT)
    await installAuthStub(page, login)

    const before = psql(
      "SELECT COUNT(*) FROM partner_product_price_memory WHERE partner_id='44f0cfc1-4a5f-4206-85cd-04ad5fa70922'",
    )
    expect(before, '사전 조건: (A,X) 기억행 1건').toBe('1')

    const P2 = '777000'
    await openSlipForm(page, '/sales/new')
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)
    // 기억단가가 채워진 상태에서 사용자가 다시 다른 값으로 덮어써 저장
    await expectUnitPriceDigits(page, PRICE_P)
    await unitPriceInput(page).fill(P2)
    await page.getByRole('button', { name: '저장' }).click()
    await page.waitForURL('**/sales', { timeout: 30000 })
    await page.waitForTimeout(1500)

    // ON CONFLICT DO UPDATE — 행 추가가 아니라 갱신
    const rows = psql(
      "SELECT COUNT(*) FROM partner_product_price_memory WHERE partner_id='44f0cfc1-4a5f-4206-85cd-04ad5fa70922' AND product_id='a046f235-6d7d-49a5-b321-e2d533e1ff08'",
    )
    expect(rows, 'upsert 인데 중복행 발생').toBe('1')
    const price = psql(
      "SELECT unit_price FROM partner_product_price_memory WHERE partner_id='44f0cfc1-4a5f-4206-85cd-04ad5fa70922' AND product_id='a046f235-6d7d-49a5-b321-e2d533e1ff08'",
    )
    expect(price, '재저장 단가 미반영').toBe('777000.00')

    // 갱신 결과가 실제 화면 자동채움에 반영되는지 재확인
    await openSlipForm(page, '/sales/new')
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await expectUnitPriceDigits(page, P2)
    await capture(page, '07-upsert-resaved-777000-autofilled-single-row')
    console.log('[#809] 07 upsert 후 DB unit_price:', price)
    await ctx.close()
  })
  /**
   * 🔴 HIGH 결함 재현 — 견적 자동채움 미동작(무증상 실패).
   *
   * 실측 경로: 모델명 onBlur → `GET /slips/lookup-product` 200 → BE 응답 필드는
   * `id`/`name` 인데 FE `ProductLookupResult` 는 `productId`/`productName` 로 선언(검증 없는
   * 캐스팅이라 tsc 통과·런타임 undefined) → `getPriceMemory(partnerId, undefined)` 가
   * productId 없이 요청 → `GET /slips/price-memory?partnerId=…` **400** →
   * FE catch 가 삼킴 → 정가(1,470,700) fallback.
   *
   * 즉 개발책임자 결정 ①("전표+견적")의 견적 절반이 실 GUI 에서 동작하지 않는다.
   * 본 테스트는 기대 동작(기억단가 999,000)을 단언하므로 결함이 남아있는 한 실패한다(의도).
   */
  test('08 견적 — 거래처A + 모델명 onBlur lookup → 기억단가 자동채움', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const calls = trackPriceMemoryCalls(page)
    const statuses: string[] = []
    page.on('response', (res) => {
      if (res.url().includes('/slips/price-memory')) statuses.push(`${res.status()} ${res.url()}`)
    })
    const login = await realLogin(page, ACCOUNT)
    await installAuthStub(page, login)

    // 앞선 테스트 실행 순서에 의존하지 않도록 기대 기억단가를 셋업한다(셋업만 DB, 판정은 실 GUI).
    psql(
      `UPDATE partner_product_price_memory SET unit_price=${PRICE_P}.00 
       WHERE partner_id='44f0cfc1-4a5f-4206-85cd-04ad5fa70922' 
         AND product_id='a046f235-6d7d-49a5-b321-e2d533e1ff08'`.replace(/\s+/g, ' '),
    )

    await page.goto(`${BASE_URL}/sales/estimates/new`)
    await expect(page.getByRole('combobox', { name: '거래처 검색' })).toBeVisible({ timeout: 30000 })
    await page.waitForTimeout(400)
    await pickAutocomplete(page, '거래처 검색', '거래처 목록', PARTNER_A.query)
    // 거래처 선택이 실제로 반영됐는지 확정(빈 상태로 진행하면 false-RED)
    await expect(page.getByLabel('거래처명')).toHaveValue(PARTNER_A.name, { timeout: 15000 })

    // 견적은 모델명 onBlur lookup 경로
    const model = page.getByLabel('라인 1 모델명')
    await model.scrollIntoViewIfNeeded()
    await model.fill(PRODUCT_X.model)
    await model.blur()
    await page.waitForTimeout(2000)

    // 결함 증거를 단언 실패 전에 남긴다
    await capture(page, '08-DEFECT-estimate-autofill-fallback-to-list-price-1470700')
    console.log('[#809] 08 견적 price-memory 요청:', JSON.stringify(calls))
    console.log('[#809] 08 견적 price-memory 응답:', JSON.stringify(statuses))
    console.log(
      '[#809] 08 견적 라인1 단가 실측:',
      await unitPriceInput(page).inputValue(),
    )

    expect(
      statuses.some((s) => s.startsWith('400')),
      'productId 누락 400 이 관측되지 않음(결함 양상 변화 — 재조사 필요)',
    ).toBeTruthy()
    await expectUnitPriceDigits(page, PRICE_P)
    await ctx.close()
  })

})
