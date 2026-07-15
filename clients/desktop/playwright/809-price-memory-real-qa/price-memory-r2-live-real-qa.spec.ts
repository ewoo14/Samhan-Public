/**
 * #809 (거래처+품목) 최근단가 자동채움 — R1 fix 후 라이브 재검증 (R2 라운드, mock OFF).
 *
 * 대상 HEAD: ca3eee819 (R1 리뷰 fix — BLOCKING 4 · HIGH 6 · MEDIUM 11 · LOW 8 + 개발책임자 결정 4건)
 * 실 게이트웨이(:8080) → 재빌드 slip-service/partner-service(이미지 --build) → 실 Postgres.
 * 합성/fixture 없음. 판정은 전부 실 GUI, DB 는 뒷받침 실측용.
 *
 * ⚠️ 계정: `dev_master` 는 auth_db 상 "마스터" 권한그룹에 `sales.slip.create` 행이 없어
 * 전표 생성 자체가 403 이다(R1 INFO-1, #809 회귀 아님). 본 QA 는 `sales.slip.create` +
 * `purchases.slip.edit` 전권인 "매니저" 그룹 계정 `dev_manager` 로 수행한다.
 *
 * 실 시드(실 DB 조회로 확인):
 *  - 거래처A 한울냉열시스템 (44f0cfc1-…-04ad5fa70922)
 *  - 거래처B 국민건강보험공단종로지사 (dba5051b-…-2a162e0f4367) — 검색어 유일매치용
 *  - 품목X AJ030MXHNBC1 / 실외기_3HP 단배관 (a046f235-…-e2d533e1ff08) 정가 1,470,700
 *  - 품목Y AJ040MXHNBC1 / 실외기_4HP 단배관 (3612c28e-…-26367a8d3e3c) 정가 1,731,400
 *  - 세트 AC023CS1DBC1SY / 무풍 1way 냉방전용 (b63f676c-…-68d3d2c8d293) 정가 1,204,500
 *    · 기본 구성품 4종(INDOOR 21dec2cc / OUTDOOR 8015b3da / REMOTE 8f0becf3 / PANEL 3325f787)
 *
 * 시나리오: A 견적 자동채움 · B BUNDLE_SET · C 거래처 변경 재조회 · D 최근가 마커 ·
 *          E 수정경로 ×1.1 정규화 · F 전표 회귀
 * 단계별 캡처 → docs/qa/809-partner-product-price-memory/r2/
 */
import { expect, test, type Page } from '@playwright/test'
import * as path from 'path'
import * as fs from 'fs'
import { execSync } from 'child_process'
import { fileURLToPath } from 'url'

const _dirname =
  typeof __dirname !== 'undefined' ? __dirname : path.dirname(fileURLToPath(import.meta.url))
const BASE_URL = process.env['QA_BASE_URL'] ?? 'http://localhost:5211'
const API_BASE = process.env['API_BASE'] ?? 'http://localhost:8080'
const PASSWORD = process.env['DEV_PASSWORD'] ?? 'dev_p05_pass!'
const ACCOUNT = 'dev_manager'
const SHOTS = path.resolve(_dirname, '../../../../docs/qa/809-partner-product-price-memory/r2')
fs.mkdirSync(SHOTS, { recursive: true })

const PARTNER_A = { name: '한울냉열시스템', query: '한울냉열', id: '44f0cfc1-4a5f-4206-85cd-04ad5fa70922' }
const PARTNER_B = { name: '국민건강보험공단종로지사', query: '국민건강보험공단종로', id: 'dba5051b-6f22-4ae0-8277-2a162e0f4367' }
const PRODUCT_X = { model: 'AJ030MXHNBC1', name: '실외기_3HP 단배관', listPrice: '1470700', id: 'a046f235-6d7d-49a5-b321-e2d533e1ff08' }
const PRODUCT_Y = { model: 'AJ040MXHNBC1', listPrice: '1731400', id: '3612c28e-be0d-4b50-b774-26367a8d3e3c' }
const BUNDLE = { model: 'AC023CS1DBC1SY', listPrice: '1204500', id: 'b63f676c-cf19-4b56-926f-68d3d2c8d293' }
const BUNDLE_COMPONENT_IDS = [
  '21dec2cc-6b6f-49d7-9c13-fc56f3b7177c',
  '8015b3da-e89e-4af1-9cd2-9bc8cc71ef93',
  '8f0becf3-82d9-4a6b-9c86-30ce497e0f3d',
  '3325f787-e84b-4ce0-9adf-e64284a7ef5d',
]

/** 라운드 고유값 — 정가/직전 라운드 값과 명백히 구분되는 단가. */
const PRICE_P = '888000' // A: 거래처A+품목X 기억단가
const PRICE_B = '555000' // C: 거래처B+품목X 기억단가(재조회 대상)
const PRICE_BUNDLE = '1100000' // B: 세트 저장단가
const PRICE_USER_LINE = '111111' // C: 사용자 직접입력(보존 대상)
const EDIT_Q_EXCL_VAT = '500000' // E: 수정화면 VAT 제외 입력
const EDIT_Q_INCL_VAT = '550000' // E: 기대 정규화값(×1.1)

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

interface NetLog { calls: string[]; responses: string[] }

/** price-memory 호출/응답을 실제로 관측한다(경로 렌더 ≠ 기능 동작 구분용). */
function trackPriceMemory(page: Page): NetLog {
  const log: NetLog = { calls: [], responses: [] }
  page.on('request', (req) => {
    if (req.url().includes('/slips/price-memory')) log.calls.push(req.url())
  })
  page.on('response', (res) => {
    if (res.url().includes('/slips/price-memory')) log.responses.push(`${res.status()} ${res.url()}`)
  })
  return log
}

/** POST /estimates 요청이 실제로 나갔는지 관측(R1 은 요청조차 안 나갔음). */
function trackEstimatePost(page: Page): string[] {
  const posts: string[] = []
  page.on('request', (req) => {
    if (req.method() === 'POST' && /\/estimates(\?|$)/.test(req.url())) posts.push(req.url())
  })
  return posts
}

async function login(page: Page): Promise<void> {
  const l = await realLogin(page, ACCOUNT)
  await installAuthStub(page, l)
}

async function openSlipForm(page: Page): Promise<void> {
  await page.goto(`${BASE_URL}/sales/new`)
  await expect(page.getByRole('combobox', { name: '거래처' })).toBeVisible({ timeout: 30000 })
  await page.waitForTimeout(400)
}

/**
 * 자동완성 실 후보만 매칭한다.
 *
 * ⚠️ AsyncAutocomplete 의 "검색 중…" 로딩행도 `role="option"` 이라(`statusRow`, id 없음)
 * `getByRole('option').first()` 로 기다리면 로딩행에 걸려 결과 도착 전에 키를 눌러
 * 선택이 무효화된다(드롭다운 열린 채 잔류). 실 후보는 `id="${listId}-${key}"` 를 가지므로
 * id 접두사로 좁혀 "결과 도착" 을 실제로 기다린다.
 */
const realOptions = (page: Page, listboxLabel: string, idPrefix = 'ds-aac-list-') =>
  page.getByRole('listbox', { name: listboxLabel }).first().locator(`li[id^="${idPrefix}"]`)

/**
 * 자동완성 선택(실 키보드 조작 — ArrowDown+Enter).
 * portal floating layer 라 마우스 클릭이 viewport 밖으로 나가는 함정을 키보드 확정으로 회피.
 */
async function pickAutocomplete(page: Page, name: string, listboxLabel: string, query: string): Promise<void> {
  const input = page.getByRole('combobox', { name })
  await input.scrollIntoViewIfNeeded()
  await input.click()
  await input.fill(query)
  const options = realOptions(page, listboxLabel)
  await expect(options.first(), `자동완성 후보 미표시: ${name} / ${query}`).toBeVisible({ timeout: 20000 })
  await input.press('ArrowDown')
  await input.press('Enter')
  await expect(options.first(), `자동완성 확정 실패(드롭다운 잔류): ${name} / ${query}`).toBeHidden({ timeout: 10000 })
  await page.waitForTimeout(300)
}

async function pickWarehouse(page: Page): Promise<void> {
  const input = page.getByRole('combobox', { name: '출고 창고' })
  await input.scrollIntoViewIfNeeded()
  await input.click()
  // 창고는 정적 목록(AsyncAutocomplete 아님) — listId 접두사가 `ds-wh-list-` 이고 로딩행이 없다.
  const options = realOptions(page, '창고 목록', 'ds-wh-list-')
  await expect(options.first(), '창고 후보 미표시').toBeVisible({ timeout: 20000 })
  await input.press('ArrowDown')
  await input.press('Enter')
  await expect(options.first(), '창고 확정 실패(드롭다운 잔류)').toBeHidden({ timeout: 10000 })
  await page.waitForTimeout(200)
}

const unitPriceInput = (page: Page, line = 1) => page.getByLabel(`라인 ${line} 단가`)

/** 단가 실측값 — 천단위 콤마 포맷과 무관하게 "어떤 값이 채워졌나" 판정. */
async function expectUnitPriceDigits(page: Page, expected: string, line = 1, msg = ''): Promise<void> {
  await expect
    .poll(async () => ((await unitPriceInput(page, line).inputValue()) || '').replace(/[^0-9]/g, ''), {
      timeout: 15000,
      message: `${msg || '단가 자동채움'} 기대값 ${expected}`,
    })
    .toBe(expected)
}

/** 실 Postgres 조회(검증 전용). */
function psql(sql: string, db = 'slip_db'): string {
  return execSync(`docker exec samhan-postgres psql -U samhan -d ${db} -t -A -c "${sql.replace(/"/g, '\\"')}"`, {
    encoding: 'utf-8',
  }).trim()
}

function memoryRow(partnerId: string, productId: string): string {
  return psql(
    `SELECT unit_price || '|' || source FROM partner_product_price_memory
     WHERE partner_id='${partnerId}' AND product_id='${productId}' AND is_deleted=false`.replace(/\s+/g, ' '),
  )
}

/** '최근가' 마커 개수 — hit 라인에만 떠야 한다. */
const recentMarkers = (page: Page) => page.getByText('최근가', { exact: true })

async function saveSlipAndWait(page: Page): Promise<void> {
  await page.getByRole('button', { name: '저장' }).click()
  await page.waitForURL('**/sales', { timeout: 30000 })
  await page.waitForTimeout(1500)
}

test.describe.serial('#809 R2 — R1 fix 후 라이브 재검증', () => {
  test.beforeAll(() => {
    // 재실행 안전성 — "최초 = miss" 를 실제로 만들기 위해 본 스펙이 쓰는 (거래처,품목) 쌍만
    // 좁혀서 정리한다. 무조건부 전체 삭제는 하지 않는다(무관 데이터 보존).
    const partners = [PARTNER_A.id, PARTNER_B.id].map((i) => `'${i}'`).join(',')
    const products = [PRODUCT_X.id, PRODUCT_Y.id, BUNDLE.id, ...BUNDLE_COMPONENT_IDS]
      .map((i) => `'${i}'`)
      .join(',')
    psql(
      `DELETE FROM partner_product_price_memory
       WHERE partner_id IN (${partners}) AND product_id IN (${products})`.replace(/\s+/g, ' '),
    )
    const left = psql(
      `SELECT COUNT(*) FROM partner_product_price_memory
       WHERE partner_id IN (${partners}) AND product_id IN (${products})`.replace(/\s+/g, ' '),
    )
    console.log('[#809 R2] 테스트 대상 쌍 초기화 — 잔여행:', left)
    expect(left, '테스트 대상 기억행 초기화 실패').toBe('0')
  })

  test('01 [F/D-miss] 전표 miss → 정가 채움 · 최근가 마커 없음 → 단가 P 입력 → 저장 → DB 기억행 생성', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const net = trackPriceMemory(page)
    await login(page)

    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1000)

    // miss → 정가 fallback + 마커 없음(D: miss 라인엔 최근가 마커가 뜨면 안 된다)
    await expectUnitPriceDigits(page, PRODUCT_X.listPrice, 1, 'miss 정가 fallback')
    await expect(recentMarkers(page), 'miss 라인에 최근가 마커가 뜨면 안 됨').toHaveCount(0)
    await capture(page, '01-slip-miss-list-price-1470700-no-recent-marker')

    await unitPriceInput(page).fill(PRICE_P)
    await page.getByLabel('라인 1 수량').fill('2')
    await expectUnitPriceDigits(page, PRICE_P)
    await capture(page, '02-slip-manual-price-888000-entered')

    await saveSlipAndWait(page)
    console.log('[#809 R2] 01 price-memory 호출:', JSON.stringify(net.responses))

    const row = memoryRow(PARTNER_A.id, PRODUCT_X.id)
    console.log('[#809 R2] 01 DB 기억행 (A,X):', row)
    expect(row, 'DB 기억행 미생성 = WRITE 훅 죽음').toBe(`${PRICE_P}.00|LINE_SAVE`)
    await ctx.close()
  })

  test('02 [F/D-hit] 새 전표 — 거래처A+품목X → P 자동채움 + 최근가 마커 표시', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const net = trackPriceMemory(page)
    await login(page)

    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)

    await expectUnitPriceDigits(page, PRICE_P, 1, '기억단가 자동채움')
    // D: hit 라인에 최근가 마커 + tooltip(저장일)
    const marker = recentMarkers(page).first()
    await expect(marker, 'hit 라인에 최근가 마커 미표시').toBeVisible({ timeout: 10000 })
    const tooltip = await marker.getAttribute('title')
    console.log('[#809 R2] 02 최근가 tooltip:', tooltip)
    expect(tooltip, '최근가 tooltip 에 저장일 누락').toMatch(/최근 단가 · \d{4}-\d{2}-\d{2} 저장/)
    await capture(page, '03-KEY-slip-autofill-888000-with-recent-marker')

    expect(net.responses.some((r) => r.startsWith('200')), 'price-memory 200 미관측').toBeTruthy()
    console.log('[#809 R2] 02 price-memory 응답:', JSON.stringify(net.responses))
    await ctx.close()
  })

  test('03 [A] 🔴 견적 — 모델명 blur → 품목명 채움 + 기억단가 자동채움 + productId 실려 200 → 임시저장', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const net = trackPriceMemory(page)
    const posts = trackEstimatePost(page)
    await login(page)

    await page.goto(`${BASE_URL}/sales/estimates/new`)
    await expect(page.getByRole('combobox', { name: '거래처 검색' })).toBeVisible({ timeout: 30000 })
    await page.waitForTimeout(400)
    await pickAutocomplete(page, '거래처 검색', '거래처 목록', PARTNER_A.query)
    await expect(page.getByLabel('거래처명')).toHaveValue(PARTNER_A.name, { timeout: 15000 })

    // 견적은 모델명 onBlur lookup 경로 (R1: productId 누락 → 400 → 정가 fallback + 품목명 공백)
    const model = page.getByLabel('라인 1 모델명')
    await model.scrollIntoViewIfNeeded()
    await model.fill(PRODUCT_X.model)
    await model.blur()
    await page.waitForTimeout(2500)

    // ⓐ 품목명 칸이 채워지는가 (계약 정합 증거)
    await expect(page.getByLabel('라인 1 품목명'), '품목명 미채움 = lookup 계약 불일치 잔존').toHaveValue(
      PRODUCT_X.name,
      { timeout: 10000 },
    )
    // ⓑ 단가 = 기억단가 P (정가 아님)
    await expectUnitPriceDigits(page, PRICE_P, 1, '견적 기억단가 자동채움')
    // ⓒ price-memory 요청에 productId 가 실려 200
    console.log('[#809 R2] 03 견적 price-memory 요청:', JSON.stringify(net.calls))
    console.log('[#809 R2] 03 견적 price-memory 응답:', JSON.stringify(net.responses))
    expect(net.calls.some((u) => u.includes(`productId=${PRODUCT_X.id}`)), 'price-memory 요청에 productId 누락(R1 결함 잔존)').toBeTruthy()
    expect(net.responses.some((r) => r.startsWith('200')), 'price-memory 200 미관측').toBeTruthy()
    expect(net.responses.some((r) => r.startsWith('400')), 'price-memory 400 = R1 결함 잔존').toBeFalsy()
    await expect(recentMarkers(page).first(), '견적 hit 라인 최근가 마커 미표시').toBeVisible({ timeout: 10000 })
    await capture(page, '04-KEY-estimate-autofill-888000-productname-filled-recent-marker')

    // ⓓ 임시저장이 실제로 되는가 (R1: POST /estimates 요청조차 안 나감)
    await page.getByLabel('라인 1 수량').fill('2')
    await page.waitForTimeout(300)
    await page.getByRole('button', { name: '임시저장' }).click()
    await page.waitForTimeout(3000)
    console.log('[#809 R2] 03 POST /estimates 관측:', JSON.stringify(posts))
    await capture(page, '05-estimate-saved-after-draft-save')
    expect(posts.length, 'POST /estimates 요청 자체가 안 나감 = R1 BLOCKING-1 잔존').toBeGreaterThan(0)

    // DB: 견적 라인 + productId 각인 확인
    const line = psql(
      `SELECT el.product_id || '|' || el.unit_price FROM estimate_lines el
       JOIN estimates e ON e.id = el.estimate_id
       WHERE el.product_id='${PRODUCT_X.id}' AND e.is_deleted=false
       ORDER BY el.created_at DESC LIMIT 1`.replace(/\s+/g, ' '),
    )
    console.log('[#809 R2] 03 DB 견적라인 (product_id|unit_price):', line)
    expect(line, '견적 라인 DB 미생성 = 저장 경로 미동작').toContain(PRODUCT_X.id)
    await ctx.close()
  })

  test('04 [B] 🔴 BUNDLE 세트 — parent 만 BUNDLE_SET 기억 · 구성품 기억 금지 · 재선택 자동채움', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)

    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', BUNDLE.model)
    await page.waitForTimeout(1200)
    await expectUnitPriceDigits(page, BUNDLE.listPrice, 1, '세트 miss 정가')
    await unitPriceInput(page).fill(PRICE_BUNDLE)
    await expectUnitPriceDigits(page, PRICE_BUNDLE)
    await capture(page, '06-bundle-set-price-1100000-entered')
    await saveSlipAndWait(page)

    // 세트 parent = BUNDLE_SET 기억행
    const parent = memoryRow(PARTNER_A.id, BUNDLE.id)
    console.log('[#809 R2] 04 DB 세트 parent 기억행:', parent)
    expect(parent, '세트 parent 기억행이 BUNDLE_SET 로 생성되지 않음').toBe(`${PRICE_BUNDLE}.00|BUNDLE_SET`)

    // 구성품 productId 로는 기억행이 생기면 안 된다(납품가 각인 방지)
    const compRows = psql(
      `SELECT COUNT(*) FROM partner_product_price_memory
       WHERE partner_id='${PARTNER_A.id}' AND product_id IN (${BUNDLE_COMPONENT_IDS.map((i) => `'${i}'`).join(',')})`.replace(/\s+/g, ' '),
    )
    console.log('[#809 R2] 04 DB 구성품 기억행 수(0 이어야 함):', compRows)
    expect(compRows, '구성품 productId 로 기억행이 생성됨 = 납품가 각인 방지 실패').toBe('0')

    // 같은 거래처에 세트 재선택 → 저장단가 자동채움
    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', BUNDLE.model)
    await page.waitForTimeout(1200)
    await expectUnitPriceDigits(page, PRICE_BUNDLE, 1, '세트 재선택 자동채움')
    await expect(recentMarkers(page).first(), '세트 hit 라인 최근가 마커 미표시').toBeVisible({ timeout: 10000 })
    await capture(page, '07-KEY-bundle-set-refill-1100000-bundle-set-source')
    await ctx.close()
  })

  test('05 [C] 🔴 거래처 변경 재조회 — 자동채움 라인은 재적용 · 사용자 입력 라인은 보존', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)

    // 사전: (거래처B, 품목X) 기억 = 555000 을 실 GUI 저장으로 만든다(격리 재확인 겸용)
    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_B.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)
    // 거래처별 격리 — B 는 A 의 888000 이 아니라 정가여야 한다
    await expectUnitPriceDigits(page, PRODUCT_X.listPrice, 1, '거래처B 격리(정가)')
    await capture(page, '08-partnerB-isolated-list-price-1470700')
    await unitPriceInput(page).fill(PRICE_B)
    await saveSlipAndWait(page)
    expect(memoryRow(PARTNER_B.id, PRODUCT_X.id), '(B,X) 기억행 미생성').toBe(`${PRICE_B}.00|LINE_SAVE`)

    // 본 시나리오 — 거래처A 에서 시작
    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)
    await expectUnitPriceDigits(page, PRICE_P, 1, '거래처A 기억단가')

    // 라인2 = 사용자 직접입력(보존 대상)
    await page.getByRole('button', { name: '+ 라인 추가' }).click()
    await page.waitForTimeout(400)
    await pickAutocomplete(page, '라인 2 품목', '품목 목록', PRODUCT_Y.model)
    await page.waitForTimeout(1000)
    await unitPriceInput(page, 2).fill(PRICE_USER_LINE)
    await expectUnitPriceDigits(page, PRICE_USER_LINE, 2, '라인2 사용자 입력')
    await capture(page, '09-before-partner-change-A-888000-user-111111')

    // 거래처를 B 로 변경 → 라인1 은 B 기준 재조회, 라인2(USER)는 보존
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_B.query)
    await page.waitForTimeout(2500)
    await expectUnitPriceDigits(page, PRICE_B, 1, '거래처 변경 후 B 기준 재조회')
    await expectUnitPriceDigits(page, PRICE_USER_LINE, 2, '사용자 입력 라인 보존')
    await capture(page, '10-KEY-partner-changed-to-B-refetched-555000-user-line-preserved')
    await ctx.close()
  })

  test('06 [E] 🟠 수정 경로 — 상세화면 단가(VAT제외) Q 수정 → ×1.1 정규화 기억 → 새 전표 자동채움', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)

    // 수정 대상 전표 생성(거래처A + 품목X)
    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)
    await saveSlipAndWait(page)

    const slipId = psql(
      `SELECT id FROM slips WHERE partner_id='${PARTNER_A.id}' AND slip_type='OUTBOUND'
         AND is_deleted=false ORDER BY created_at DESC LIMIT 1`.replace(/\s+/g, ' '),
    )
    console.log('[#809 R2] 06 수정 대상 전표:', slipId)
    expect(slipId, '수정 대상 전표 미생성').toMatch(/^[0-9a-f-]{36}$/)

    await page.goto(`${BASE_URL}/sales/${slipId}`)
    await page.getByTestId('sales-slip-edit-button').click()
    await expect(page.getByTestId('sales-slip-edit-modal')).toBeVisible({ timeout: 20000 })
    await page.waitForTimeout(800)

    // 이 화면은 VAT 제외 입력
    const priceCell = page.getByLabel('단가(VAT제외) 1')
    await priceCell.scrollIntoViewIfNeeded()
    await priceCell.fill(EDIT_Q_EXCL_VAT)
    await page.waitForTimeout(300)
    await capture(page, '11-slip-detail-edit-unit-price-500000-vat-excluded')
    await page.getByTestId('sales-slip-edit-save').click()
    await page.waitForTimeout(3500)

    // DB: ×1.1 정규화 확인
    const row = memoryRow(PARTNER_A.id, PRODUCT_X.id)
    console.log('[#809 R2] 06 수정 후 DB 기억행 (A,X):', row, `— 기대 ${EDIT_Q_INCL_VAT}.00`)
    expect(row, '수정경로 기억 미반영 또는 ×1.1 정규화 오류').toBe(`${EDIT_Q_INCL_VAT}.00|LINE_SAVE`)

    // 새 전표에서 VAT 포함 단가로 자동채움
    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)
    await expectUnitPriceDigits(page, EDIT_Q_INCL_VAT, 1, '수정경로 반영 자동채움')
    await capture(page, '12-KEY-new-slip-autofill-550000-after-edit-path')
    await ctx.close()
  })

  /**
   * F 회귀 — R1 fix 가 override 판정을 `shouldAutoFill = !unitPrice || unitPrice==='0'` 에서
   * `priceSource !== 'USER'` 기반으로 바꿨으므로(선입력 시 onUnitPriceChange 가 USER 각인)
   * 선입력 보존이 여전히 성립하는지 실 GUI 로 재확인한다.
   */
  test('07 [F] override 보존(선입력 우선) + upsert 단일행 갱신', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)

    // 사전: (A,X) 기억 = 550000 (직전 테스트 결과)
    expect(memoryRow(PARTNER_A.id, PRODUCT_X.id), '사전 조건 (A,X)=550000').toBe(`${EDIT_Q_INCL_VAT}.00|LINE_SAVE`)

    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    // 품목 선택 전에 단가를 먼저 입력 → USER 각인
    await unitPriceInput(page).fill('123456')
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1500)

    // 기억단가(550000)도 정가(1470700)도 아닌 사용자 선입력값이 보존돼야 한다
    await expectUnitPriceDigits(page, '123456', 1, 'override 보존')
    await expect(recentMarkers(page), 'USER 라인에 최근가 마커가 뜨면 안 됨').toHaveCount(0)
    await capture(page, '13-override-preserved-123456-no-marker')

    // upsert 단일행 — 저장해도 (A,X) 행은 1건이어야 한다
    await saveSlipAndWait(page)
    const rows = psql(
      `SELECT COUNT(*) FROM partner_product_price_memory
       WHERE partner_id='${PARTNER_A.id}' AND product_id='${PRODUCT_X.id}'`.replace(/\s+/g, ' '),
    )
    console.log('[#809 R2] 07 (A,X) 행 수(1 이어야 함):', rows, '· 값:', memoryRow(PARTNER_A.id, PRODUCT_X.id))
    expect(rows, 'upsert 인데 중복행 발생').toBe('1')
    expect(memoryRow(PARTNER_A.id, PRODUCT_X.id), '선입력 저장값 미반영').toBe('123456.00|LINE_SAVE')
    await ctx.close()
  })
})
