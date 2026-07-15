/**
 * #809 (거래처+품목) 최근단가 자동채움 — R5 CODEX SOL 5.6 QA fix 후 라이브 재검증 (R5-postfix, mock OFF).
 *
 * 대상: R4 적대검증 27건 fix 6개 배치(BE·DevOps·FE·Design·통합·sweep) 적용 워킹트리
 * (base 71a6f0412 — R3 fix 3커밋 위). 실 게이트웨이(:8080) → 재배포 slip-service(V58 적용 실측)
 * → 실 Postgres. 합성/fixture 없음. 판정은 전부 실 GUI, DB 는 뒷받침 실측용.
 * (단 08 의 R4-F4 in-flight 관측 1곳만 실서버 응답을 그대로 전달하며 지연을 주입하고,
 *  11 은 정상 coedit 가 거래처 autocomplete 를 잠그는 현재 제약 때문에 초기 coedit GET 만 실패시켜
 *  앱 자체의 의도된 평문 폼 fallback 에 진입한다. 가격 조회/PUT 응답 내용 변조 없음. 각 주석 참조.)
 *
 * ⚠️ R2 의 "라이브 QA 7/7 PASS" 는 superseded — R3 QA(CB-3)가 스펙 자체의 false-green 을 적발했다.
 * (견적 저장 POST 가 500 이어도 통과 · 방금 만든 견적이 아니라 임의 기존 견적 조회 · 단가가 아니라
 *  productId 존재만 단언). 본 스펙은 R3 에서 경화됐고 R4 실행이 #809 의 첫 유효 라이브 증거다.
 *
 * ⚠️ 계정: `dev_master` 는 auth_db 상 "마스터" 권한그룹에 `sales.slip.create` 행이 없어
 * 전표 생성 자체가 403 이다(R1 INFO-1, #809 회귀 아님). 본 QA 는 `sales.slip.create` +
 * `purchases.slip.edit` 전권인 "매니저" 그룹 계정 `dev_manager` 로 수행한다.
 *
 * 실 시드(R4 당일 실 DB + 실 게이트웨이 API 로 재확인 — R2 당시 시드는 스택 재시드로 전량 소멸):
 *  - 거래처A 부산냉난방테크 (e8ae9c86-…-1f5a2bf31313) — /admin/partners/search?q=부산냉난방 total=1 실측
 *  - 거래처B 전주에어시스템 (1021fcf7-…-6518ab4c27c9) — q=전주에어 total=1 실측
 *  - 품목X AC200CNCDEH-77 / 삼성 천장형 4톤 (a6992eb0-…-7accfe06288c) 판매가 1,200,000
 *  - 품목Y AC300CNCDEH-78 / 삼성 천장형 5톤 (841e6a99-…-5227de864a62) 판매가 1,440,000
 *  - 세트 QA797-SET-01 / QA797 상업 시각폴리시 테스트 (1ea24f99-…-be1901284769) 판매가 1,000,000
 *    · 기본 구성품 2종(PART-01 7de11ab7 기본2개 / PART-02 ed278526 기본1개) — POST /slips 201 실측
 *    ⚠️ 다른 세트 TEST-BUNDLE-SET-01 은 시드 결함(bundle_component.component_product_code 에
 *      product_code 아닌 model명이 시드됨 → 구성품 resolve 실패)으로 저장 자체가 404 라 사용 불가
 *      (#809 무관 — pre-existing expand 경로 · 재시드 산물).
 *
 * 시나리오: A 견적 자동채움 · B BUNDLE_SET · C 거래처 변경 재조회(bulk 1회 + 배너 + 변경행 강조) ·
 *          D 최근가/판매가 마커 · E 수정경로 ×1.1 정규화 · F 전표 회귀 ·
 *          G 견적 거래처 변경(08) · H 견적 품목 교체 R4-F1(09) · I 거래처 미선택 카피 D4(a)(10)
 *
 * R4 강화(적대 검토 — 기존 단언 약화 없음, 추가만):
 *  - 01: miss 라인 '판매가' 마커 표시 + USER 전환 시 마커 소멸 (R3 fix 신규 UI —
 *        라벨은 D-R4-1 로 '정가'→'판매가' 확정 반영)
 *  - 05: 거래처 변경 창구간 POST /slips/price-memory/bulk 정확히 1건 · 단건 GET 0건 ·
 *        bulk body 에 자동채움 2라인(X,Y) 동시 적재 · 배너 표시 · 값 변경행(라인1)만 강조 (D-R3-2/D-R3-4)
 *  - 07: USER 라인 판매가 마커도 부재 확인
 *
 * R4-postfix 강화(R4-Q3 견적 커버리지 갭 해소 — 기존 단언 약화 없음, 추가만):
 *  - 08 [G]: 견적 거래처 변경 → bulk 정확히 1건 + 배너 + 변경행(라인1)만 강조 + USER 라인 보존
 *            + R4-F4(재조회 in-flight 중 저장 disabled + '최근단가 확인 중…' busy 단서)
 *            + R4-D9/S-1(배너·busy live region 상시 마운트)
 *  - 09 [H]: 견적 품목 교체(R4-F1) — X(REMEMBERED hit) → Y 교체 시 Y 기준 재적용
 *            (X 의 단가·저장일 마커 승계 없음) → X 재교체 시 재hit · 저장 후 DB 오염 부재
 *  - 10 [I]: 거래처 미선택 상태 품목 선택(R4-D4(a)) — 카피가 거래처를 단정하지 않음
 *            ('판매가를 적용했습니다') + R4-D2(마커 aria-live 제거) + 사후 거래처 선택 시 hit 전환
 *  - ⚠️ D-R4-4(거래처 해제 → 단가 유지 + 마커만 해제)는 라이브 GUI 로 도달 불가 — 정직 미커버.
 *    PartnerAutocomplete(AsyncAutocomplete)에 해제 어포던스가 없다(빈 입력 blur = onChange 미호출,
 *    free-text = 기존 선택 유지, clear 버튼 없음). 해당 분기는 FE 단위테스트가 커버:
 *    SlipFormPage.test.tsx 'keeps the remembered unit price and only releases the marker …' ·
 *    LineRow.test.tsx 'REMEMBERED without a partner hides the marker …'.
 *
 * R5-postfix 신규 강화(기존 단언 삭제/약화 없음, R4 false-green 커버리지 구멍만 추가):
 *  - 11 [R5-H6]: 실제 legacy QUOTE_DRAFT 견적의 필수 거래처 재선택 → 가격 무수정 저장 →
 *                 PUT 2xx + priceVatInclusive=false + unit_price 불변 / unit_price_with_vat=NULL 유지 +
 *                 원 공급단가×1.1 기준 price-memory 생성(약 9.1% 하락 명시 배제) + 같은 라인
 *                 가격 실제 편집→원복 시 priceVatInclusive=true 역방향 provenance 가드
 *  - 12 [R5-H7]: 전표·견적 각각 신규 BUNDLE 저장 → 상세 재진입 무수정 PUT → 세트 계보 보존,
 *                 구성품 기억행 0, parent BUNDLE_SET 정확히 1행
 *  - 13 [R5-H8]: 모델 lookup 2xx 뒤 단건 price-memory 실응답만 지연 → 중간 저장 disabled /
 *                 0원 POST 없음 → 응답 뒤 정확한 기억단가 적용·저장
 *
 * 단계별 캡처 → docs/qa/809-partner-product-price-memory/r5-postfix/
 * (r4/ 는 pre-fix 상태 증거로 보존 — R4 적대리뷰가 검증한 대상. r2/ 는 superseded 이나 이력 보존)
 */
import { expect, test, type Page, type Response } from '@playwright/test'
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
const SHOTS = path.resolve(_dirname, '../../../../docs/qa/809-partner-product-price-memory/r5-postfix')
fs.mkdirSync(SHOTS, { recursive: true })

const PARTNER_A = { name: '부산냉난방테크', query: '부산냉난방', id: 'e8ae9c86-afe1-3364-b484-1f5a2bf31313' }
const PARTNER_B = { name: '전주에어시스템', query: '전주에어', id: '1021fcf7-f63d-3fcd-9769-6518ab4c27c9' }
// D-R4-1: miss 자동채움 실체 = product.sellingPrice(제품 등록 화면 '판매가') — 구 상수명 listPrice
// 는 출고가 계열 별칭('정가')을 연상시켜 오도이므로 sellingPrice 로 정정(값 불변, R4-postfix).
const PRODUCT_X = { model: 'AC200CNCDEH-77', name: '삼성 천장형 4톤', sellingPrice: '1200000', id: 'a6992eb0-81fc-3b3d-957b-7accfe06288c' }
const PRODUCT_Y = { model: 'AC300CNCDEH-78', name: '삼성 천장형 5톤', sellingPrice: '1440000', id: '841e6a99-06fe-3252-8a4f-5227de864a62' }
const BUNDLE = { model: 'QA797-SET-01', sellingPrice: '1000000', id: '1ea24f99-631f-4e19-937f-be1901284769' }
const BUNDLE_COMPONENT_IDS = [
  '7de11ab7-e70c-421e-80a4-7c6b51a2c6e9', // QA797-PART-01 (기본 2개)
  'ed278526-0e16-427d-8a92-2ca06164254a', // QA797-PART-02 (기본 1개)
]

/** 라운드 고유값 — 판매가/직전 라운드 값과 명백히 구분되는 단가. */
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

interface NetLog { calls: string[]; responses: string[]; bulkBodies: string[] }

/** price-memory 호출/응답을 실제로 관측한다(경로 렌더 ≠ 기능 동작 구분용). bulk 는 body 도 기록. */
function trackPriceMemory(page: Page): NetLog {
  const log: NetLog = { calls: [], responses: [], bulkBodies: [] }
  page.on('request', (req) => {
    if (req.url().includes('/slips/price-memory')) {
      log.calls.push(`${req.method()} ${req.url()}`)
      if (req.url().includes('/slips/price-memory/bulk')) log.bulkBodies.push(req.postData() ?? '')
    }
  })
  page.on('response', (res) => {
    if (res.url().includes('/slips/price-memory')) {
      log.responses.push(`${res.status()} ${res.request().method()} ${res.url()}`)
    }
  })
  return log
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

/** 단가 실측값 — 천단위 콤마만 제거한다. 부호와 소수점은 값의 일부로 보존한다. */
async function expectUnitPriceDigits(page: Page, expected: string, line = 1, msg = ''): Promise<void> {
  await expect
    .poll(async () => {
      const normalized = ((await unitPriceInput(page, line).inputValue()) || '').trim().replace(/,/g, '')
      return /^-?\d+(?:\.\d+)?$/.test(normalized) ? Number(normalized) : Number.NaN
    }, {
      timeout: 15000,
      message: `${msg || '단가 자동채움'} 기대값 ${expected}`,
    })
    .toBe(Number(expected))
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

function resetMemoryPair(partnerId: string, productId: string): void {
  psql(
    `DELETE FROM partner_product_price_memory
     WHERE partner_id='${partnerId}' AND product_id='${productId}'`.replace(/\s+/g, ' '),
  )
}

function seedMemoryRow(partnerId: string, productId: string, unitPrice: string, source = 'LINE_SAVE'): void {
  resetMemoryPair(partnerId, productId)
  psql(
    `INSERT INTO partner_product_price_memory
       (id, partner_id, product_id, unit_price, source, remembered_at,
        created_at, created_by, is_deleted)
     VALUES
       (gen_random_uuid(), '${partnerId}', '${productId}', ${unitPrice}, '${source}',
        TIMESTAMP '2000-01-01 00:00:00', CURRENT_TIMESTAMP, 'qa-r4', FALSE)`.replace(/\s+/g, ' '),
  )
}

async function expectMemoryRowEventually(
  partnerId: string,
  productId: string,
  unitPrice: string,
  source = 'LINE_SAVE',
): Promise<void> {
  await expect.poll(
    () => memoryRow(partnerId, productId),
    {
      timeout: 5000,
      intervals: [25, 50, 100, 250, 500],
      message: `bounded async 가격기억 flush 미완료: partner=${partnerId}, product=${productId}, price=${unitPrice}`,
    },
  ).toBe(`${unitPrice}.00|${source}`)
}

async function saveEstimateDraftAndGetId(page: Page): Promise<string> {
  const responsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/estimates(\?|$)/.test(response.url()),
    { timeout: 30000 },
  )
  await page.getByRole('button', { name: '임시저장' }).click()
  const response = await responsePromise
  expect(response.ok(), `POST /estimates 저장 실패: HTTP ${response.status()}`).toBeTruthy()
  const body = await response.json()
  const estimateId = body?.data?.id
  expect(estimateId, 'POST /estimates 2xx 응답에 신규 estimateId 누락').toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
  )
  return estimateId
}

/** 견적 신규 폼 열기 — 거래처 검색 combobox 가시화까지 대기(03/08/09/10 공용). */
async function openEstimateForm(page: Page): Promise<void> {
  await page.goto(`${BASE_URL}/sales/estimates/new`)
  await expect(page.getByRole('combobox', { name: '거래처 검색' })).toBeVisible({ timeout: 30000 })
  await page.waitForTimeout(400)
}

/** 견적 모델명 입력 + blur — onBlur lookup(자동채움 트리거) 경로(08/09/10 공용). */
async function fillEstimateModel(page: Page, line: number, model: string): Promise<void> {
  const input = page.getByLabel(`라인 ${line} 모델명`)
  await input.scrollIntoViewIfNeeded()
  await input.fill(model)
  await input.blur()
  await page.waitForTimeout(2000)
}

/**
 * 견적 데스크톱 변경행 강조 — 전표(LineRow priceRefreshed 클래스)와 달리 견적 데스크톱 라인은
 * inline style(background: var(--surface-selected) · border-left var(--action-brand))로 강조한다.
 * background 의 surface-selected 토큰 문자열은 priceRefreshChanged=true 행에만 존재한다(실 구현 대조).
 * data-price-source 는 라인 row div 에만 있어 qty/unit-price input 의 testid prefix 오탐을 배제한다.
 */
const estimateHighlightedRows = (page: Page) =>
  page.locator('[data-testid^="estimate-form-line-"][data-price-source][style*="surface-selected"]')

/** '거래처 최근단가' 마커 개수 — hit 라인에만 떠야 한다. */
const recentMarkers = (page: Page) => page.getByText('거래처 최근단가', { exact: true })

/**
 * '판매가' 마커 — miss 자동채움(CATALOG) 라인에만 떠야 한다(R3 fix 신규 UI).
 * D-R4-1(2026-07-15): 라벨 '정가' → '판매가' 확정(정가=출고가 계열 별칭 오도).
 * role=note + aria-label(설명 문구)로 좁혀 페이지 내 다른 '판매가' 문자열 오탐을 배제한다.
 * (본 스펙 시나리오는 모두 거래처 선택 상태 → 거래처 단정 카피가 정답.)
 */
const catalogMarkers = (page: Page) =>
  page.getByRole('note', { name: '이 거래처에 저장된 최근단가가 없어 판매가를 적용했습니다' })

/**
 * 거래처 미선택 CATALOG 마커 — R4-D4(a): 거래처를 선택하지 않고 품목만 고르면 카피가
 * 거래처를 단정하지 않아야 한다('판매가를 적용했습니다').
 * ⚠️ exact 필수 — 거래처 단정 카피('이 거래처에 저장된 최근단가가 없어 판매가를 적용했습니다')가
 * 이 문구를 부분 문자열로 포함하므로 substring 매칭이면 오탐된다.
 */
const catalogMarkersNoPartner = (page: Page) =>
  page.getByRole('note', { name: '판매가를 적용했습니다', exact: true })

async function saveSlipAndWait(page: Page): Promise<string> {
  const responsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/slips(\?|$)/.test(response.url()),
    { timeout: 30000 },
  )
  await page.getByRole('button', { name: '저장' }).click()
  const response = await responsePromise
  expect(response.ok(), `POST /slips 저장 실패: HTTP ${response.status()}`).toBeTruthy()
  const body = await response.json()
  const slipId = body?.data?.id
  expect(slipId, 'POST /slips 2xx 응답에 신규 slipId 누락').toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
  )
  await page.waitForURL('**/sales', { timeout: 30000 })
  return slipId
}

interface LegacyEstimateTarget {
  id: string
  estimateNo: string
  status: string
  partnerName: string
  productId: string
  productName: string
  modelName: string
  quantity: number
  unitPrice: string
}

/** 실 DB 의 편집 가능 legacy 견적 중 단일라인 1건을 동적으로 선택한다(합성/seed 없음). */
function findLegacyEstimateTarget(): LegacyEstimateTarget {
  const raw = psql(
    `SELECT row_to_json(target)::text FROM (
       SELECT e.id, e.estimate_no AS \"estimateNo\", e.status,
              e.partner_name AS \"partnerName\", el.product_id AS \"productId\",
               el.product_name AS \"productName\", el.model_name AS \"modelName\",
               el.quantity,
               el.unit_price::text AS \"unitPrice\"
       FROM estimates e
       JOIN estimate_lines el ON el.estimate_id=e.id
       WHERE e.is_deleted=false AND el.is_deleted=false
         AND e.status IN ('QUOTE_DRAFT','QUOTE_SENT')
         AND e.partner_id IS NULL
         AND e.partner_name='${PARTNER_A.name}'
         AND el.unit_price_with_vat IS NULL
         AND (SELECT COUNT(*) FROM estimate_lines active
              WHERE active.estimate_id=e.id AND active.is_deleted=false)=1
       ORDER BY e.created_at DESC
       LIMIT 1
     ) target`.replace(/\s+/g, ' '),
  )
  expect(raw, '실 DB 에 편집 가능한 단일라인 legacy 견적이 없음').not.toBe('')
  return JSON.parse(raw) as LegacyEstimateTarget
}

function estimatePriceSnapshot(estimateId: string): string {
  return psql(
    `SELECT string_agg(
       product_id::text || '|' || unit_price::text || '|' ||
       COALESCE(unit_price_with_vat::text, 'NULL'), ',' ORDER BY line_no)
     FROM estimate_lines
     WHERE estimate_id='${estimateId}' AND is_deleted=false`.replace(/\s+/g, ' '),
  )
}

/** 대상 품목의 가격기억 값/출처/시각/삭제상태 전체 스냅샷 — 행 추가·갱신·soft-delete 모두 감지. */
function memorySnapshotForProduct(productId: string): string {
  return psql(
    `SELECT COALESCE(string_agg(
       partner_id::text || '|' || unit_price::text || '|' || source || '|' ||
       remembered_at::text || '|' || is_deleted::text, ',' ORDER BY partner_id::text), '')
     FROM partner_product_price_memory
     WHERE product_id='${productId}'`.replace(/\s+/g, ' '),
  )
}

type BundleLineTable = 'slip_lines' | 'estimate_lines'

function bundleLineageSnapshot(table: BundleLineTable, ownerId: string): string {
  const ownerColumn = table === 'slip_lines' ? 'slip_id' : 'estimate_id'
  return psql(
    `SELECT COALESCE(string_agg(
       product_id::text || '|' || set_head::text || '|' || COALESCE(parent_set_model, 'NULL') || '|' ||
       unit_price::text || '|' || COALESCE(unit_price_with_vat::text, 'NULL'),
       ',' ORDER BY product_id::text), '')
     FROM ${table}
     WHERE ${ownerColumn}='${ownerId}' AND is_deleted=false`.replace(/\s+/g, ' '),
  )
}

function bundleLineageSummary(table: BundleLineTable, ownerId: string): string {
  const ownerColumn = table === 'slip_lines' ? 'slip_id' : 'estimate_id'
  return psql(
    `SELECT COUNT(*) || '|' || COUNT(*) FILTER (WHERE set_head) || '|' ||
            COUNT(*) FILTER (WHERE parent_set_model='${BUNDLE.model}') || '|' ||
            string_agg(product_id::text, ',' ORDER BY product_id::text)
     FROM ${table}
     WHERE ${ownerColumn}='${ownerId}' AND is_deleted=false`.replace(/\s+/g, ' '),
  )
}

function memoryRowCount(partnerId: string, productId: string): string {
  return psql(
    `SELECT COUNT(*) FROM partner_product_price_memory
     WHERE partner_id='${partnerId}' AND product_id='${productId}'`.replace(/\s+/g, ' '),
  )
}

function bundleComponentMemoryCount(partnerId: string): string {
  return psql(
    `SELECT COUNT(*) FROM partner_product_price_memory
     WHERE partner_id='${partnerId}'
       AND product_id IN (${BUNDLE_COMPONENT_IDS.map((id) => `'${id}'`).join(',')})`.replace(/\s+/g, ' '),
  )
}

test.describe.serial('#809 R4-postfix — R4 적대 fix 후 라이브 재검증', () => {
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
    console.log('[#809 R4] 테스트 대상 쌍 초기화 — 잔여행:', left)
    expect(left, '테스트 대상 기억행 초기화 실패').toBe('0')
  })

  test('01 [F/D-miss] 전표 miss → 판매가 채움 · 최근가 마커 없음 → 단가 P 입력 → 저장 → DB 기억행 생성', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const net = trackPriceMemory(page)
    await login(page)
    resetMemoryPair(PARTNER_A.id, PRODUCT_X.id)

    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1000)

    // miss → 판매가 fallback + 마커 없음(D: miss 라인엔 최근가 마커가 뜨면 안 된다)
    await expectUnitPriceDigits(page, PRODUCT_X.sellingPrice, 1, 'miss 판매가 fallback')
    await expect(recentMarkers(page), 'miss 라인에 최근가 마커가 뜨면 안 됨').toHaveCount(0)
    // R3 fix 신규 UI: miss 라인엔 '판매가' 마커가 떠야 한다(자동채움 근거 노출, D-R4-1 라벨)
    const catalogMarker = catalogMarkers(page).first()
    await expect(catalogMarker, 'miss 라인에 판매가 마커 미표시(R3 fix 회귀)').toBeVisible({ timeout: 10000 })
    await expect(catalogMarker, '판매가 마커 라벨 불일치(D-R4-1)').toHaveText('판매가')
    await capture(page, '01-slip-miss-list-price-1200000-catalog-marker-no-recent-marker')

    await unitPriceInput(page).fill(PRICE_P)
    await page.getByLabel('라인 1 수량').fill('2')
    await expectUnitPriceDigits(page, PRICE_P)
    // 수동입력(USER 전환) 순간 판매가 마커는 사라져야 한다 — 근거 아닌 라벨 잔존 방지
    await expect(catalogMarkers(page), 'USER 전환 후에도 판매가 마커 잔존').toHaveCount(0)
    await capture(page, '02-slip-manual-price-888000-entered')

    await saveSlipAndWait(page)
    console.log('[#809 R4] 01 price-memory 호출:', JSON.stringify(net.responses))

    await expectMemoryRowEventually(PARTNER_A.id, PRODUCT_X.id, PRICE_P)
    const row = memoryRow(PARTNER_A.id, PRODUCT_X.id)
    console.log('[#809 R4] 01 DB 기억행 (A,X):', row)
    expect(row, 'DB 기억행 미생성 = WRITE 훅 죽음').toBe(`${PRICE_P}.00|LINE_SAVE`)
    await ctx.close()
  })

  test('02 [F/D-hit] 새 전표 — 거래처A+품목X → P 자동채움 + 최근가 마커 표시', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const net = trackPriceMemory(page)
    await login(page)
    seedMemoryRow(PARTNER_A.id, PRODUCT_X.id, PRICE_P)

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
    console.log('[#809 R4] 02 최근가 tooltip:', tooltip)
    expect(tooltip, '거래처 최근단가 tooltip 에 저장일 누락').toMatch(
      /이 거래처에 마지막으로 저장된 단가 · \d{4}-\d{2}-\d{2} 저장/,
    )
    await capture(page, '03-KEY-slip-autofill-888000-with-recent-marker')

    expect(net.responses.some((r) => r.startsWith('200')), 'price-memory 200 미관측').toBeTruthy()
    console.log('[#809 R4] 02 price-memory 응답:', JSON.stringify(net.responses))
    await ctx.close()
  })

  test('03 [A] 🔴 견적 — 모델명 blur → 품목명 채움 + 기억단가 자동채움 + productId 실려 200 → 임시저장', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const net = trackPriceMemory(page)
    await login(page)
    // 이 테스트가 직접 만든 sentinel row만 읽는다. 저장 훅이 죽으면 remembered_at이 2000년에 머문다.
    seedMemoryRow(PARTNER_A.id, PRODUCT_X.id, PRICE_P)

    await page.goto(`${BASE_URL}/sales/estimates/new`)
    await expect(page.getByRole('combobox', { name: '거래처 검색' })).toBeVisible({ timeout: 30000 })
    await page.waitForTimeout(400)
    await pickAutocomplete(page, '거래처 검색', '거래처 목록', PARTNER_A.query)
    await expect(page.getByLabel('거래처명')).toHaveValue(PARTNER_A.name, { timeout: 15000 })

    // 견적은 모델명 onBlur lookup 경로 (R1: productId 누락 → 400 → 판매가 fallback + 품목명 공백)
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
    // ⓑ 단가 = 기억단가 P (판매가 아님)
    await expectUnitPriceDigits(page, PRICE_P, 1, '견적 기억단가 자동채움')
    // ⓒ price-memory 요청에 productId 가 실려 200
    console.log('[#809 R4] 03 견적 price-memory 요청:', JSON.stringify(net.calls))
    console.log('[#809 R4] 03 견적 price-memory 응답:', JSON.stringify(net.responses))
    expect(net.calls.some((u) => u.includes(`productId=${PRODUCT_X.id}`)), 'price-memory 요청에 productId 누락(R1 결함 잔존)').toBeTruthy()
    expect(net.responses.some((r) => r.startsWith('200')), 'price-memory 200 미관측').toBeTruthy()
    expect(net.responses.some((r) => r.startsWith('400')), 'price-memory 400 = R1 결함 잔존').toBeFalsy()
    await expect(recentMarkers(page).first(), '견적 hit 라인 최근가 마커 미표시').toBeVisible({ timeout: 10000 })
    await capture(page, '04-KEY-estimate-autofill-888000-productname-filled-recent-marker')

    // ⓓ 임시저장이 실제로 되는가 (R1: POST /estimates 요청조차 안 나감)
    await page.getByLabel('라인 1 수량').fill('2')
    await page.waitForTimeout(300)
    const estimateId = await saveEstimateDraftAndGetId(page)
    console.log('[#809 R4] 03 POST /estimates 신규 ID:', estimateId)
    await capture(page, '05-estimate-saved-after-draft-save')

    // DB: 반드시 방금 2xx 응답에서 회수한 estimateId의 권위 VAT 포함 단가를 확인한다.
    const line = psql(
      `SELECT el.product_id || '|' || el.unit_price_with_vat FROM estimate_lines el
       JOIN estimates e ON e.id = el.estimate_id
       WHERE e.id='${estimateId}' AND el.product_id='${PRODUCT_X.id}'
         AND e.is_deleted=false AND el.is_deleted=false`.replace(/\s+/g, ' '),
    )
    console.log('[#809 R4] 03 DB 신규 견적라인 (product_id|unit_price_with_vat):', line)
    expect(line, '신규 견적 라인의 VAT 포함 단가가 P와 다름').toBe(`${PRODUCT_X.id}|${PRICE_P}.00`)
    await expect.poll(
      () => psql(
        `SELECT remembered_at > TIMESTAMP '2000-01-01 00:00:00'
         FROM partner_product_price_memory
         WHERE partner_id='${PARTNER_A.id}' AND product_id='${PRODUCT_X.id}'
           AND unit_price=${PRICE_P} AND source='LINE_SAVE' AND is_deleted=false`.replace(/\s+/g, ' '),
      ),
      {
        timeout: 5000,
        intervals: [25, 50, 100, 250, 500],
        message: '견적 저장 후 bounded async 가격기억 flush가 sentinel row를 갱신하지 않음',
      },
    ).toBe('t')
    expect(memoryRow(PARTNER_A.id, PRODUCT_X.id), '견적 저장 가격기억이 P와 다름')
      .toBe(`${PRICE_P}.00|LINE_SAVE`)
    await ctx.close()
  })

  test('04 [B] 🔴 BUNDLE 세트 — parent 만 BUNDLE_SET 기억 · 구성품 기억 금지 · 재선택 자동채움', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)
    resetMemoryPair(PARTNER_A.id, BUNDLE.id)
    BUNDLE_COMPONENT_IDS.forEach((productId) => resetMemoryPair(PARTNER_A.id, productId))

    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', BUNDLE.model)
    await page.waitForTimeout(1200)
    await expectUnitPriceDigits(page, BUNDLE.sellingPrice, 1, '세트 miss 판매가')
    await unitPriceInput(page).fill(PRICE_BUNDLE)
    await expectUnitPriceDigits(page, PRICE_BUNDLE)
    await capture(page, '06-bundle-set-price-1100000-entered')
    await saveSlipAndWait(page)

    // 세트 parent = BUNDLE_SET 기억행
    await expectMemoryRowEventually(PARTNER_A.id, BUNDLE.id, PRICE_BUNDLE, 'BUNDLE_SET')
    const parent = memoryRow(PARTNER_A.id, BUNDLE.id)
    console.log('[#809 R4] 04 DB 세트 parent 기억행:', parent)
    expect(parent, '세트 parent 기억행이 BUNDLE_SET 로 생성되지 않음').toBe(`${PRICE_BUNDLE}.00|BUNDLE_SET`)

    // 구성품 productId 로는 기억행이 생기면 안 된다(납품가 각인 방지)
    const compRows = psql(
      `SELECT COUNT(*) FROM partner_product_price_memory
       WHERE partner_id='${PARTNER_A.id}' AND product_id IN (${BUNDLE_COMPONENT_IDS.map((i) => `'${i}'`).join(',')})`.replace(/\s+/g, ' '),
    )
    console.log('[#809 R4] 04 DB 구성품 기억행 수(0 이어야 함):', compRows)
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

  test('05 [C] 🔴 거래처 변경 재조회 — bulk 1회(D-R3-4) · 배너+변경행 강조(D-R3-2) · 사용자 입력 보존', async ({ browser }) => {
    test.slow() // R4 강화: 사전 전표 저장 + 3라인 구성 + 네트워크/배너/강조 단언 — 기본 60s 한도 3배
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const net = trackPriceMemory(page)
    await login(page)
    seedMemoryRow(PARTNER_A.id, PRODUCT_X.id, PRICE_P)
    resetMemoryPair(PARTNER_B.id, PRODUCT_X.id)
    // 라인3(자동채움 Y)은 (A,Y)/(B,Y) 모두 miss 여야 한다 — bulk 부분 hit 계약의 대조군
    resetMemoryPair(PARTNER_A.id, PRODUCT_Y.id)
    resetMemoryPair(PARTNER_B.id, PRODUCT_Y.id)

    // 사전: (거래처B, 품목X) 기억 = 555000 을 실 GUI 저장으로 만든다(격리 재확인 겸용)
    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_B.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)
    // 거래처별 격리 — B 는 A 의 888000 이 아니라 판매가여야 한다
    await expectUnitPriceDigits(page, PRODUCT_X.sellingPrice, 1, '거래처B 격리(판매가)')
    await capture(page, '08-partnerB-isolated-list-price-1200000')
    await unitPriceInput(page).fill(PRICE_B)
    await saveSlipAndWait(page)
    await expectMemoryRowEventually(PARTNER_B.id, PRODUCT_X.id, PRICE_B)

    // 본 시나리오 — 거래처A 에서 시작
    await openSlipForm(page)
    // R4-D9/S-1(전표 측): 배너·busy live region 은 활성 전에도 DOM 에 상시 마운트(빈 텍스트)여야 한다
    const slipBanner = page.getByTestId('slip-price-refresh-banner')
    const slipBusy = page.getByTestId('slip-form-price-refresh-busy')
    await expect(slipBanner, '전표 배너 live region 이 비활성 시 미마운트(R4-D9 회귀)').toBeAttached()
    await expect(slipBanner, '전표 비활성 배너에 텍스트 잔존').toHaveText('')
    await expect(slipBusy, '전표 busy live region 이 비활성 시 미마운트(R4-D9/S-1 회귀)').toBeAttached()
    await expect(slipBusy, '전표 비활성 busy 단서에 텍스트 잔존').toHaveText('')
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

    // 라인3 = 자동채움 두 번째 라인(Y, (A,Y) miss → 판매가) — bulk 가 자동 라인 N개를 1요청에 실어야 한다
    await page.getByRole('button', { name: '+ 라인 추가' }).click()
    await page.waitForTimeout(400)
    await pickAutocomplete(page, '라인 3 품목', '품목 목록', PRODUCT_Y.model)
    await page.waitForTimeout(1000)
    await expectUnitPriceDigits(page, PRODUCT_Y.sellingPrice, 3, '라인3 (A,Y) miss 판매가')
    await capture(page, '09-before-partner-change-A-888000-user-111111-autoY-1440000')

    // 거래처를 B 로 변경 → 자동 라인(1·3)은 bulk 1회로 재조회, 라인2(USER)는 보존
    const callsBefore = net.calls.length
    const responsesBefore = net.responses.length
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_B.query)
    await page.waitForTimeout(2500)
    await expectUnitPriceDigits(page, PRICE_B, 1, '거래처 변경 후 B 기준 재조회')
    await expectUnitPriceDigits(page, PRICE_USER_LINE, 2, '사용자 입력 라인 보존')
    await expectUnitPriceDigits(page, PRODUCT_Y.sellingPrice, 3, '라인3 (B,Y) miss — 판매가 유지')

    // D-R3-4: 거래처 변경 창구간 네트워크 = bulk POST 정확히 1건 · 라인별 단건 GET 0건
    const windowCalls = net.calls.slice(callsBefore)
    const windowResponses = net.responses.slice(responsesBefore)
    console.log('[#809 R4] 05 거래처 변경 창구간 price-memory 호출:', JSON.stringify(windowCalls))
    console.log('[#809 R4] 05 거래처 변경 창구간 price-memory 응답:', JSON.stringify(windowResponses))
    const bulkCalls = windowCalls.filter((u) => u.includes('/slips/price-memory/bulk'))
    const singleCalls = windowCalls.filter((u) => !u.includes('/slips/price-memory/bulk'))
    expect(bulkCalls.length, '거래처 변경 시 bulk 호출이 정확히 1건이 아님(D-R3-4 회귀)').toBe(1)
    expect(singleCalls.length, '거래처 변경 시 라인별 단건 GET 발생(D-R3-4 회귀)').toBe(0)
    expect(
      windowResponses.some((r) => r.startsWith('200') && r.includes('/slips/price-memory/bulk')),
      'bulk 200 미관측',
    ).toBeTruthy()
    const bulkBody = JSON.parse(net.bulkBodies[net.bulkBodies.length - 1] ?? '{}') as {
      partnerId?: string
      productIds?: string[]
    }
    expect(bulkBody.partnerId, 'bulk 요청 partnerId 가 변경된 거래처B 가 아님').toBe(PARTNER_B.id)
    expect(
      [...(bulkBody.productIds ?? [])].sort(),
      'bulk productIds 에 자동채움 2라인(X,Y)이 한 요청으로 실리지 않음',
    ).toEqual([PRODUCT_X.id, PRODUCT_Y.id].sort())

    // D-R3-2: 배너 + 변경행 강조 — 값이 실제 바뀐 라인1만 강조돼야 한다
    const banner = page.getByRole('status').filter({ hasText: '거래처 변경으로 최근단가 재적용' })
    await expect(banner, '거래처 변경 배너 미표시(D-R3-2 회귀)').toBeVisible({ timeout: 10000 })
    const highlighted = page.locator('[role="row"][class*="priceRefreshed"]')
    await expect(highlighted, '변경행 강조가 정확히 1행(값 변경 라인)이 아님').toHaveCount(1)
    await expect(highlighted, '강조 행이 라인1이 아님').toHaveAttribute('data-line-number', '1')
    // 마커 정합: 라인1=거래처 최근단가 · 라인3=판매가(D-R4-1) · 라인2(USER)=마커 없음
    await expect(recentMarkers(page), '최근가 마커는 라인1 1개여야 함').toHaveCount(1)
    await expect(catalogMarkers(page), '판매가 마커는 라인3 1개여야 함').toHaveCount(1)
    // R4-D2(전표 측): 마커의 라인별 aria-live 제거 — 전역 고지는 배너(role=status) 단독
    expect(await recentMarkers(page).first().getAttribute('aria-live'), '전표 마커에 aria-live 잔존(R4-D2 회귀)').toBeNull()
    await banner.scrollIntoViewIfNeeded()
    await capture(page, '10-KEY-partner-changed-to-B-refresh-banner-visible')
    await highlighted.scrollIntoViewIfNeeded()
    await page.waitForTimeout(300)
    await capture(page, '11-KEY-partner-changed-bulk1-highlight-row1-555000-user-preserved-missY-1440000')
    await ctx.close()
  })

  test('06 [E] 🟠 수정 경로 — 상세화면 단가(VAT제외) Q 수정 → ×1.1 정규화 기억 → 새 전표 자동채움', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)
    resetMemoryPair(PARTNER_A.id, PRODUCT_X.id)

    // 수정 대상 전표 생성(거래처A + 품목X)
    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)
    const slipId = await saveSlipAndWait(page)
    console.log('[#809 R4] 06 수정 대상 전표:', slipId)

    await page.goto(`${BASE_URL}/sales/${slipId}`)
    await page.getByTestId('sales-slip-edit-button').click()
    await expect(page.getByTestId('sales-slip-edit-modal')).toBeVisible({ timeout: 20000 })
    await page.waitForTimeout(800)

    // 이 화면은 VAT 제외 입력
    const priceCell = page.getByLabel('단가(VAT제외) 1')
    await priceCell.scrollIntoViewIfNeeded()
    await priceCell.fill(EDIT_Q_EXCL_VAT)
    await page.waitForTimeout(300)
    await capture(page, '12-slip-detail-edit-unit-price-500000-vat-excluded')
    const updateResponsePromise = page.waitForResponse(
      (response) => response.request().method() === 'PUT' && response.url().includes(`/slips/${slipId}`),
      { timeout: 30000 },
    )
    await page.getByTestId('sales-slip-edit-save').click()
    const updateResponse = await updateResponsePromise
    expect(updateResponse.ok(), `PUT /slips/${slipId} 수정 실패: HTTP ${updateResponse.status()}`).toBeTruthy()

    // DB: ×1.1 정규화 확인
    await expectMemoryRowEventually(PARTNER_A.id, PRODUCT_X.id, EDIT_Q_INCL_VAT)
    const row = memoryRow(PARTNER_A.id, PRODUCT_X.id)
    console.log('[#809 R4] 06 수정 후 DB 기억행 (A,X):', row, `— 기대 ${EDIT_Q_INCL_VAT}.00`)
    expect(row, '수정경로 기억 미반영 또는 ×1.1 정규화 오류').toBe(`${EDIT_Q_INCL_VAT}.00|LINE_SAVE`)

    // 새 전표에서 VAT 포함 단가로 자동채움
    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1200)
    await expectUnitPriceDigits(page, EDIT_Q_INCL_VAT, 1, '수정경로 반영 자동채움')
    await capture(page, '13-KEY-new-slip-autofill-550000-after-edit-path')
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

    // 사전 조건은 직전 테스트 산출물을 재사용하지 않고 이 테스트가 직접 만든다.
    seedMemoryRow(PARTNER_A.id, PRODUCT_X.id, EDIT_Q_INCL_VAT)

    await openSlipForm(page)
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
    await pickWarehouse(page)
    // 품목 선택 전에 단가를 먼저 입력 → USER 각인
    await unitPriceInput(page).fill('123456')
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', PRODUCT_X.model)
    await page.waitForTimeout(1500)

    // 기억단가(550000)도 판매가(1200000)도 아닌 사용자 선입력값이 보존돼야 한다
    await expectUnitPriceDigits(page, '123456', 1, 'override 보존')
    await expect(recentMarkers(page), 'USER 라인에 최근가 마커가 뜨면 안 됨').toHaveCount(0)
    await expect(catalogMarkers(page), 'USER 라인에 판매가 마커가 뜨면 안 됨').toHaveCount(0)
    await capture(page, '14-override-preserved-123456-no-marker')

    // upsert 단일행 — 저장해도 (A,X) 행은 1건이어야 한다
    await saveSlipAndWait(page)
    await expectMemoryRowEventually(PARTNER_A.id, PRODUCT_X.id, '123456')
    const rows = psql(
      `SELECT COUNT(*) FROM partner_product_price_memory
       WHERE partner_id='${PARTNER_A.id}' AND product_id='${PRODUCT_X.id}'`.replace(/\s+/g, ' '),
    )
    console.log('[#809 R4] 07 (A,X) 행 수(1 이어야 함):', rows, '· 값:', memoryRow(PARTNER_A.id, PRODUCT_X.id))
    expect(rows, 'upsert 인데 중복행 발생').toBe('1')
    expect(memoryRow(PARTNER_A.id, PRODUCT_X.id), '선입력 저장값 미반영').toBe('123456.00|LINE_SAVE')
    await ctx.close()
  })

  /**
   * G — R4-Q3 견적 커버리지 갭 해소. R4 까지 거래처 변경 재조회(bulk·배너·강조)는 전표로만
   * 실증됐다. R4-F1 이 견적 전용 결함이었던 만큼 견적 경로를 독립 실증한다.
   *
   * R4-F4 in-flight 관측: bulk 응답이 로컬에서 수십 ms 라 busy 상태가 실측 불가능하므로,
   * 이 테스트에 한해 bulk 요청을 실서버로 그대로 보내고 받은 실응답을 지연 후 전달한다
   * (route.fetch → 2.5s hold → fulfill(실응답)). 응답 내용 변조/합성 없음 — 가짜 데이터 아님,
   * 네트워크 지연만 재현. 라우트는 bulk 1개 URL 로만 좁힌다(real-qa 프록시 글롭 규칙).
   */
  test('08 [G] 🔴 견적 거래처 변경 — bulk 1건 · 배너 · 변경행 강조 · USER 보존 · R4-F4 busy/저장차단 · R4-D9 상시 마운트', async ({ browser }) => {
    test.slow()
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const net = trackPriceMemory(page)
    await login(page)
    seedMemoryRow(PARTNER_A.id, PRODUCT_X.id, PRICE_P)
    seedMemoryRow(PARTNER_B.id, PRODUCT_X.id, PRICE_B)
    resetMemoryPair(PARTNER_A.id, PRODUCT_Y.id)
    resetMemoryPair(PARTNER_B.id, PRODUCT_Y.id)

    let delayBulk = false
    await page.route('**/slips/price-memory/bulk', async (route) => {
      const response = await route.fetch() // 실서버 실응답
      if (delayBulk) await new Promise((resolve) => setTimeout(resolve, 2500))
      await route.fulfill({ response })
    })

    await openEstimateForm(page)
    // R4-D9/S-1: 배너·busy live region 은 활성 전에도 DOM 에 상시 마운트(빈 텍스트)여야 한다
    const estBanner = page.getByTestId('estimate-price-refresh-banner')
    const estBusy = page.getByTestId('estimate-form-price-refresh-busy')
    const saveButton = page.getByTestId('estimate-form-save-button')
    await expect(estBanner, '배너 live region 이 비활성 시 미마운트(R4-D9 회귀)').toBeAttached()
    await expect(estBanner, '비활성 배너에 텍스트 잔존').toHaveText('')
    await expect(estBusy, 'busy live region 이 비활성 시 미마운트(R4-D9/S-1 회귀)').toBeAttached()
    await expect(estBusy, '비활성 busy 단서에 텍스트 잔존').toHaveText('')

    await pickAutocomplete(page, '거래처 검색', '거래처 목록', PARTNER_A.query)
    await expect(page.getByLabel('거래처명')).toHaveValue(PARTNER_A.name, { timeout: 15000 })

    // 라인1 = X 자동채움(A,X hit)
    await fillEstimateModel(page, 1, PRODUCT_X.model)
    await expectUnitPriceDigits(page, PRICE_P, 1, '견적 라인1 (A,X) 기억단가')
    // 라인2 = Y 자동채움 후 수동 덮어쓰기(USER 보존 대상)
    await page.getByTestId('estimate-form-add-line').click()
    await page.waitForTimeout(400)
    await fillEstimateModel(page, 2, PRODUCT_Y.model)
    await expectUnitPriceDigits(page, PRODUCT_Y.sellingPrice, 2, '견적 라인2 (A,Y) miss 판매가')
    await page.getByLabel('라인 2 단가').fill(PRICE_USER_LINE)
    await expectUnitPriceDigits(page, PRICE_USER_LINE, 2, '견적 라인2 사용자 입력')
    // 라인3 = Y 자동채움 유지((A,Y)/(B,Y) 모두 miss) — bulk 부분 hit 계약의 대조군
    await page.getByTestId('estimate-form-add-line').click()
    await page.waitForTimeout(400)
    await fillEstimateModel(page, 3, PRODUCT_Y.model)
    await expectUnitPriceDigits(page, PRODUCT_Y.sellingPrice, 3, '견적 라인3 (A,Y) miss 판매가')
    await capture(page, '15-estimate-3lines-A-888000-user-111111-autoY-1440000')

    // 거래처 B 로 변경 — bulk 지연 창에서 R4-F4(busy + 저장차단) 를 실측한다
    const callsBefore = net.calls.length
    const responsesBefore = net.responses.length
    delayBulk = true
    await pickAutocomplete(page, '거래처 검색', '거래처 목록', PARTNER_B.query)
    await expect(estBusy, 'R4-F4 재조회 in-flight busy 단서 미표시').toHaveText('최근단가 확인 중…', { timeout: 4000 })
    await expect(saveButton, 'R4-F4 재조회 in-flight 중 저장 미차단').toBeDisabled()
    await estBusy.scrollIntoViewIfNeeded()
    await capture(page, '16-KEY-estimate-partner-change-inflight-busy-save-disabled')
    // 실응답 도착 후 busy 해제 + 저장 재활성 (busy 고착 = R4-F3 계열 회귀)
    await expect(estBusy, 'bulk 완료 후 busy 단서 잔존(고착)').toHaveText('', { timeout: 15000 })
    await expect(saveButton, 'bulk 완료 후 저장 버튼 미복구').toBeEnabled()
    delayBulk = false

    await expectUnitPriceDigits(page, PRICE_B, 1, '견적 거래처 변경 후 (B,X) 재조회')
    await expectUnitPriceDigits(page, PRICE_USER_LINE, 2, '견적 USER 라인 보존')
    await expectUnitPriceDigits(page, PRODUCT_Y.sellingPrice, 3, '견적 라인3 (B,Y) miss — 판매가 유지')

    // D-R3-4 대칭: 견적 거래처 변경 창구간에도 bulk 정확히 1건 · 라인별 단건 GET 0건
    const windowCalls = net.calls.slice(callsBefore)
    const windowResponses = net.responses.slice(responsesBefore)
    console.log('[#809 R4-postfix] 08 견적 거래처 변경 창구간 호출:', JSON.stringify(windowCalls))
    console.log('[#809 R4-postfix] 08 견적 거래처 변경 창구간 응답:', JSON.stringify(windowResponses))
    const bulkCalls = windowCalls.filter((u) => u.includes('/slips/price-memory/bulk'))
    const singleCalls = windowCalls.filter((u) => !u.includes('/slips/price-memory/bulk'))
    expect(bulkCalls.length, '견적 거래처 변경 시 bulk 호출이 정확히 1건이 아님').toBe(1)
    expect(singleCalls.length, '견적 거래처 변경 시 라인별 단건 GET 발생').toBe(0)
    expect(
      windowResponses.some((r) => r.startsWith('200') && r.includes('/slips/price-memory/bulk')),
      '견적 bulk 200 미관측',
    ).toBeTruthy()
    const bulkBody = JSON.parse(net.bulkBodies[net.bulkBodies.length - 1] ?? '{}') as {
      partnerId?: string
      productIds?: string[]
    }
    expect(bulkBody.partnerId, '견적 bulk 요청 partnerId 가 변경된 거래처B 가 아님').toBe(PARTNER_B.id)
    expect(
      [...(bulkBody.productIds ?? [])].sort(),
      '견적 bulk productIds 에 자동채움 2라인(X,Y)이 한 요청으로 실리지 않음',
    ).toEqual([PRODUCT_X.id, PRODUCT_Y.id].sort())

    // 배너 + 변경행 강조 — 값이 실제 바뀐 라인1(estimate-form-line-0)만
    await expect(estBanner, '견적 거래처 변경 배너 미표시').toHaveText(
      '거래처 변경으로 최근단가 재적용 · 변경된 행을 확인해 주세요.',
    )
    const highlighted = estimateHighlightedRows(page)
    await expect(highlighted, '견적 변경행 강조가 정확히 1행이 아님').toHaveCount(1)
    await expect(highlighted, '견적 강조 행이 라인1이 아님').toHaveAttribute('data-testid', 'estimate-form-line-0')
    // 마커 정합: 라인1=거래처 최근단가 · 라인3=판매가 · 라인2(USER)=마커 없음
    await expect(recentMarkers(page), '견적 최근가 마커는 라인1 1개여야 함').toHaveCount(1)
    await expect(catalogMarkers(page), '견적 판매가 마커는 라인3 1개여야 함').toHaveCount(1)
    await estBanner.scrollIntoViewIfNeeded()
    await capture(page, '17-KEY-estimate-partner-changed-to-B-banner-highlight-row1-555000')
    await page.unroute('**/slips/price-memory/bulk')
    await ctx.close()
  })

  /**
   * H — R4-F1 실증. R4 적대검증이 적발한 견적 전용 데이터오염: X(REMEMBERED hit) 상태에서
   * 모델명을 Y 로 교체하면 X 의 단가·마커가 Y 라인에 승계됐다(마커 거짓 + 저장 시 (A,Y) 오염).
   * fix 후 기대: 교체 시 Y 기준 재채움(전표와 대칭 — 공유 헬퍼 shouldAutoFillPrice).
   */
  test('09 [H] 🔴 견적 품목 교체(R4-F1) — X hit → Y 교체 시 Y 기준 재적용(승계 없음) → X 재hit → 저장 DB 오염 부재', async ({ browser }) => {
    test.slow()
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)
    seedMemoryRow(PARTNER_A.id, PRODUCT_X.id, PRICE_P) // remembered_at=2000-01-01 sentinel
    resetMemoryPair(PARTNER_A.id, PRODUCT_Y.id)

    await openEstimateForm(page)
    await pickAutocomplete(page, '거래처 검색', '거래처 목록', PARTNER_A.query)
    await expect(page.getByLabel('거래처명')).toHaveValue(PARTNER_A.name, { timeout: 15000 })

    // 1) X hit — 888000 + 최근단가 마커(저장일 = seed sentinel 2000-01-01)
    await fillEstimateModel(page, 1, PRODUCT_X.model)
    await expect(page.getByLabel('라인 1 품목명')).toHaveValue(PRODUCT_X.name, { timeout: 10000 })
    await expectUnitPriceDigits(page, PRICE_P, 1, 'X hit 기억단가')
    const lineRow = page.getByTestId('estimate-form-line-0')
    await expect(lineRow, 'X hit 라인 priceSource 상태 불일치').toHaveAttribute('data-price-source', 'REMEMBERED')
    const hitMarker = recentMarkers(page).first()
    await expect(hitMarker, 'X hit 최근가 마커 미표시').toBeVisible({ timeout: 10000 })
    expect(await hitMarker.getAttribute('title'), 'X hit tooltip 저장일이 seed sentinel 이 아님').toBe(
      '이 거래처에 마지막으로 저장된 단가 · 2000-01-01 저장',
    )
    await capture(page, '18-estimate-line1-x-hit-888000-remembered-2000-01-01')

    // 2) 모델 X → Y 교체 — Y 기준 재적용((A,Y) miss → Y 판매가 + 판매가 마커).
    //    X 의 888000/최근단가 마커/저장일이 하나라도 남으면 R4-F1 잔존이다.
    await fillEstimateModel(page, 1, PRODUCT_Y.model)
    await expect(page.getByLabel('라인 1 품목명'), 'Y 교체 후 품목명 미갱신').toHaveValue(PRODUCT_Y.name, { timeout: 10000 })
    await expectUnitPriceDigits(page, PRODUCT_Y.sellingPrice, 1, 'R4-F1 교체 후 Y 판매가 재적용')
    await expect(lineRow, 'Y 교체 후 priceSource 가 CATALOG 로 재판정되지 않음(R4-F1 잔존)').toHaveAttribute('data-price-source', 'CATALOG')
    await expect(recentMarkers(page), 'Y(miss) 라인에 X 의 최근가 마커 승계(R4-F1 잔존)').toHaveCount(0)
    const swappedCatalogMarker = catalogMarkers(page).first()
    await expect(swappedCatalogMarker, 'Y(miss) 라인 판매가 마커 미표시').toBeVisible({ timeout: 10000 })
    await expect(swappedCatalogMarker, '판매가 마커 라벨 불일치(D-R4-1)').toHaveText('판매가')
    await capture(page, '19-KEY-estimate-swap-x-to-y-sellingprice-1440000-no-inheritance')

    // 3) 역방향 Y → X 재교체 — (A,X) 재hit(재조회 생존, D-R4-4 의 '재조회 자격 보존' 반증 방지)
    await fillEstimateModel(page, 1, PRODUCT_X.model)
    await expect(page.getByLabel('라인 1 품목명')).toHaveValue(PRODUCT_X.name, { timeout: 10000 })
    await expectUnitPriceDigits(page, PRICE_P, 1, 'X 재교체 재hit')
    await expect(recentMarkers(page), 'X 재hit 최근가 마커 미복원').toHaveCount(1)
    expect(await recentMarkers(page).first().getAttribute('title'), 'X 재hit tooltip 저장일 불일치').toBe(
      '이 거래처에 마지막으로 저장된 단가 · 2000-01-01 저장',
    )
    await capture(page, '20-estimate-swap-back-to-x-rehit-888000')

    // 4) 최종 Y 로 교체 후 저장 — DB 에 (A,Y)=Y 판매가(1440000)가 기록돼야 하고,
    //    구결함이면 X 의 888000 이 (A,Y) 로 오염된다. (A,X) 기억행은 불변이어야 한다.
    await fillEstimateModel(page, 1, PRODUCT_Y.model)
    await expectUnitPriceDigits(page, PRODUCT_Y.sellingPrice, 1, '최종 Y 판매가')
    const estimateId = await saveEstimateDraftAndGetId(page)
    console.log('[#809 R4-postfix] 09 POST /estimates 신규 ID:', estimateId)
    await capture(page, '21-estimate-final-y-1440000-saved')
    const line = psql(
      `SELECT el.product_id || '|' || el.unit_price_with_vat FROM estimate_lines el
       JOIN estimates e ON e.id = el.estimate_id
       WHERE e.id='${estimateId}' AND e.is_deleted=false AND el.is_deleted=false`.replace(/\s+/g, ' '),
    )
    console.log('[#809 R4-postfix] 09 DB 견적라인:', line)
    expect(line, '저장된 견적 라인이 Y/판매가와 다름').toBe(`${PRODUCT_Y.id}|${PRODUCT_Y.sellingPrice}.00`)
    await expectMemoryRowEventually(PARTNER_A.id, PRODUCT_Y.id, PRODUCT_Y.sellingPrice)
    expect(memoryRow(PARTNER_A.id, PRODUCT_Y.id), '(A,Y) 기억행이 X 단가로 오염(R4-F1 데이터오염 잔존)').toBe(
      `${PRODUCT_Y.sellingPrice}.00|LINE_SAVE`,
    )
    expect(memoryRow(PARTNER_A.id, PRODUCT_X.id), '(A,X) 기억행이 저장에 휘말려 변경됨').toBe(`${PRICE_P}.00|LINE_SAVE`)
    await ctx.close()
  })

  /**
   * I — R4-D4(a) 실증. 거래처 미선택 상태에서 품목만 선택하면 CATALOG 카피가 거래처를
   * 단정하지 않아야 한다('판매가를 적용했습니다'). + R4-D2(마커 aria-live 제거) +
   * 거래처를 나중에 선택하면 CATALOG 라인이 재조회 대상으로 hit 전환(배너·강조 포함).
   *
   * ⚠️ D-R4-4(선택 후 해제)는 라이브 GUI 도달 불가 — 파일 상단 주석 참조(정직 미커버).
   */
  test('10 [I] 🔴 거래처 미선택 카피(R4-D4(a)) — 거래처 단정 없음 · aria-live 제거(R4-D2) · 사후 선택 시 hit 전환', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const net = trackPriceMemory(page)
    await login(page)
    seedMemoryRow(PARTNER_A.id, PRODUCT_X.id, PRICE_P)

    await openEstimateForm(page)
    // 거래처를 선택하지 않은 채 품목만 — 판매가 채움 + 거래처 비단정 카피
    await fillEstimateModel(page, 1, PRODUCT_X.model)
    await expect(page.getByLabel('라인 1 품목명')).toHaveValue(PRODUCT_X.name, { timeout: 10000 })
    await expectUnitPriceDigits(page, PRODUCT_X.sellingPrice, 1, '거래처 미선택 판매가 채움')
    const noPartnerMarker = catalogMarkersNoPartner(page).first()
    await expect(noPartnerMarker, '거래처 미선택 판매가 마커 미표시(R4-D4(a) 회귀)').toBeVisible({ timeout: 10000 })
    await expect(noPartnerMarker, '판매가 마커 라벨 불일치(D-R4-1)').toHaveText('판매가')
    // 거래처 단정 카피('이 거래처에 저장된 최근단가가 없어…')가 미선택 상태에 뜨면 안 된다
    await expect(catalogMarkers(page), '거래처 미선택인데 거래처 단정 카피 표시(R4-D4(a) 회귀)').toHaveCount(0)
    // R4-D2: 마커의 라인별 aria-live 제거(전역 고지는 배너 단독)
    expect(await noPartnerMarker.getAttribute('aria-live'), '마커에 aria-live 잔존(R4-D2 회귀)').toBeNull()
    // 거래처가 없으므로 price-memory 조회 자체가 없어야 한다
    expect(net.calls.length, '거래처 미선택인데 price-memory 호출 발생').toBe(0)
    await capture(page, '22-KEY-estimate-no-partner-sellingprice-copy-without-partner-claim')

    // 사후 거래처 A 선택 — CATALOG 라인이 재조회 대상(bulk 1건)이 되어 hit 전환 + 배너/강조
    const callsBefore = net.calls.length
    await pickAutocomplete(page, '거래처 검색', '거래처 목록', PARTNER_A.query)
    await expect(page.getByLabel('거래처명')).toHaveValue(PARTNER_A.name, { timeout: 15000 })
    await expectUnitPriceDigits(page, PRICE_P, 1, '사후 거래처 선택 hit 전환')
    await expect(recentMarkers(page), 'hit 전환 후 최근가 마커 미표시').toHaveCount(1)
    await expect(catalogMarkersNoPartner(page), 'hit 전환 후 미선택 카피 잔존').toHaveCount(0)
    const windowCalls = net.calls.slice(callsBefore)
    const bulkCalls = windowCalls.filter((u) => u.includes('/slips/price-memory/bulk'))
    const singleCalls = windowCalls.filter((u) => !u.includes('/slips/price-memory/bulk'))
    console.log('[#809 R4-postfix] 10 사후 거래처 선택 창구간 호출:', JSON.stringify(windowCalls))
    expect(bulkCalls.length, '사후 거래처 선택 시 bulk 호출이 정확히 1건이 아님').toBe(1)
    expect(singleCalls.length, '사후 거래처 선택 시 라인별 단건 GET 발생').toBe(0)
    const bulkBody = JSON.parse(net.bulkBodies[net.bulkBodies.length - 1] ?? '{}') as {
      partnerId?: string
      productIds?: string[]
    }
    expect(bulkBody.partnerId, '사후 선택 bulk partnerId 불일치').toBe(PARTNER_A.id)
    expect(bulkBody.productIds, '사후 선택 bulk productIds 불일치').toEqual([PRODUCT_X.id])
    // 값이 실제 변한 라인(1200000→888000)이므로 배너 + 강조 1행
    const estBanner = page.getByTestId('estimate-price-refresh-banner')
    await expect(estBanner, '사후 거래처 선택 배너 미표시').toHaveText(
      '거래처 변경으로 최근단가 재적용 · 변경된 행을 확인해 주세요.',
    )
    await expect(estimateHighlightedRows(page), '사후 선택 강조가 정확히 1행이 아님').toHaveCount(1)
    await capture(page, '23-KEY-estimate-late-partner-select-rehit-888000-banner-highlight')
    await ctx.close()
  })
})

test.describe('#809 R5-postfix — R4 false-green 커버리지 구멍 실서버 재검증', () => {
  test('11 [R5-H6] 🔴 실제 legacy 견적 거래처 재선택·가격 무수정 저장 — PUT 2xx + 공급단가 불변·9.1% 하락 없음·기억값 정상 생성', async ({ browser }) => {
    test.slow()
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)
    let targetToRestore: LegacyEstimateTarget | null = null

    try {
      const legacyEditableCount = psql(
        `SELECT COUNT(*) FROM estimates e
         JOIN estimate_lines el ON el.estimate_id=e.id
         WHERE e.is_deleted=false AND el.is_deleted=false
           AND e.status IN ('QUOTE_DRAFT','QUOTE_SENT')
           AND el.unit_price_with_vat IS NULL`.replace(/\s+/g, ' '),
      )
      console.log('[#809 R5-postfix] 11 편집 가능 legacy 라인 실측:', legacyEditableCount)
      expect(legacyEditableCount, 'R5 브리프의 편집 가능 legacy 1,926건 실측과 다름').toBe('1926')

      const target = findLegacyEstimateTarget()
      targetToRestore = target
      expect(target.status, 'legacy 대상이 편집 가능 상태가 아님').toMatch(/^QUOTE_(DRAFT|SENT)$/)
      expect(target.id, 'legacy estimateId 형식 불일치').toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
      )
      const priceBefore = estimatePriceSnapshot(target.id)
      const memoryBefore = memorySnapshotForProduct(target.productId)
      const partnerMemoryBefore = memoryRow(PARTNER_A.id, target.productId)
      expect(priceBefore, 'legacy DB 사전값이 unit_price_with_vat=NULL 계약과 다름').toBe(
        `${target.productId}|${target.unitPrice}|NULL`,
      )
      expect(partnerMemoryBefore, 'legacy 대상 거래처+품목에 사전 price-memory 가 이미 존재함').toBe('')

      // 정상 coedit provider 연결 중에는 거래처 autocomplete 가 disabled라 legacy partner_id=NULL을
      // 사용자가 복구할 UI가 없다. 앱이 명시적으로 제공·단위검증하는 "provider 생성 실패 → 평문 폼"
      // fallback만 활성화한다. coedit 초기 GET 외 가격/거래처 검색/PUT/DB는 전부 실서버다.
      let coeditFallbackGetCount = 0
      const coeditInitialGet = new RegExp(
        `/slips/estimates/${target.id}/collab/coedit(?:\\?.*)?$`,
      )
      await page.route(coeditInitialGet, async (route) => {
        coeditFallbackGetCount += 1
        await route.abort('failed')
      })
      await page.goto(`${BASE_URL}/sales/estimates/${target.id}/edit`)
      await expect(page.getByLabel('라인 1 모델명'), 'legacy 견적 편집 폼 미표시').toHaveValue(target.modelName, {
        timeout: 30000,
      })
      await expect(page.getByLabel('라인 1 품목명')).toHaveValue(target.productName)
      await expectUnitPriceDigits(page, target.unitPrice, 1, 'legacy 공급단가 hydrate')
      const saveButton = page.getByTestId('estimate-form-save-button')
      await expect(saveButton, 'legacy 견적 저장 버튼 미활성').toBeEnabled({ timeout: 20000 })
      await expect(page.getByRole('combobox', { name: '거래처 검색' }), 'coedit 실패 뒤 평문 폼 fallback 미진입').toBeEnabled()
      expect(coeditFallbackGetCount, 'coedit 초기 GET 실패 주입이 정확히 1건이 아님').toBe(1)
      await page.unroute(coeditInitialGet)

      // legacy 레코드는 partner_id=NULL 이므로 main 에서도 저장 전 거래처 재선택이 필수다.
      // 이 단계는 가격 편집을 우회하지 않는다. 검증 대상은 단가 입력을 한 번도 건드리지 않은 채
      // legacyPriceUntouched provenance 가 유지되어 priceVatInclusive=false 로 전송되는 가격 basis 다.
      await pickAutocomplete(page, '거래처 검색', '거래처 목록', PARTNER_A.query)
      await expect(page.getByLabel('거래처명'), 'legacy 거래처 재선택 후 명칭 불일치').toHaveValue(PARTNER_A.name)
      await expectUnitPriceDigits(page, target.unitPrice, 1, '거래처 재선택은 가격 무수정이어야 함')
      await expect(saveButton, '거래처 재선택 후 legacy 견적 저장 버튼 미활성').toBeEnabled({ timeout: 20000 })
      await capture(page, '33-KEY-legacy-estimate-partner-reselected-price-untouched-1920000')

      let resolveUpdate!: (response: Response) => void
      const updateObserved = new Promise<Response>((resolve) => { resolveUpdate = resolve })
      const onResponse = (response: Response) => {
        if (
          response.request().method() === 'PUT'
          && response.url().includes(`/slips/estimates/${target.id}`)
        ) {
          resolveUpdate(response)
        }
      }
      page.on('response', onResponse)
      await saveButton.click()
      const updateResponse = await Promise.race([
        updateObserved,
        page.waitForTimeout(10000).then(() => null),
      ])
      page.off('response', onResponse)

      const validationMessage = await page.getByRole('alert').textContent().catch(() => null)
      expect(
        updateResponse?.status() ?? 0,
        `PUT /estimates/{id} 미관측(폼 오류: ${validationMessage ?? '없음'})`,
      ).toBeGreaterThanOrEqual(200)
      expect(updateResponse?.status() ?? 999, 'PUT /estimates/{id} 가 2xx 아님').toBeLessThan(300)

      const updateBody = updateResponse?.request().postDataJSON() as {
        partnerId?: string
        lines?: Array<{ unitPrice?: string | number; priceVatInclusive?: boolean }>
      } | undefined
      expect(updateBody?.partnerId, 'legacy PUT body partnerId 가 재선택 거래처와 다름').toBe(PARTNER_A.id)
      expect(updateBody?.lines, 'legacy PUT body 라인이 정확히 1개가 아님').toHaveLength(1)
      expect(Number(updateBody?.lines?.[0]?.unitPrice), 'legacy PUT body 가 원 공급단가를 보내지 않음').toBe(
        Number(target.unitPrice),
      )
      expect(updateBody?.lines?.[0]?.priceVatInclusive, '가격 무수정 legacy 라인을 VAT 포함 입력으로 오판').toBe(false)

      // PUT 2xx 뒤 DB 두 단가 필드를 실측한다. priceVatInclusive=false 이므로 원 공급단가는 불변이고
      // unit_price_with_vat 은 NULL 을 유지해야 한다. 이 exact 단언이 /1.1 재분리(약 9.1% 하락)를 막는다.
      const priceAfter = estimatePriceSnapshot(target.id)
      const incorrectlyDividedSupplyUnit = (
        Math.round((Number(target.unitPrice) * target.quantity) / 1.1) / target.quantity
      ).toFixed(2)
      console.log('[#809 R5-postfix] 11 legacy DB before/after:', priceBefore, '/', priceAfter)
      console.log('[#809 R5-postfix] 11 legacy 9.1% 하락 오판값:', incorrectlyDividedSupplyUnit)
      expect(priceAfter, 'legacy 무수정 저장으로 unit_price 또는 unit_price_with_vat 변형').toBe(priceBefore)
      expect(
        priceAfter,
        `legacy 공급단가가 VAT 포함으로 오판되어 약 9.1% 하락(${target.unitPrice}→${incorrectlyDividedSupplyUnit})`,
      ).not.toContain(`|${incorrectlyDividedSupplyUnit}|`)

      // price-memory 저장 basis 는 VAT 포함 입력단가다. legacy 공급단가 경로(false)는 원 공급단가×1.1로
      // 정규화되어야 하며, 잘못 하락한 공급단가나 원 공급단가 자체를 그대로 기억하면 FAIL 한다.
      const expectedMemoryPrice = (Number(target.unitPrice) * 1.1).toFixed(2)
      await expect.poll(
        () => memoryRow(PARTNER_A.id, target.productId),
        {
          timeout: 5000,
          intervals: [25, 50, 100, 250, 500],
          message: `legacy 원 공급단가 기준 price-memory 생성 미완료: ${target.unitPrice}×1.1=${expectedMemoryPrice}`,
        },
      ).toBe(`${expectedMemoryPrice}|LINE_SAVE`)
      const memoryAfter = memorySnapshotForProduct(target.productId)
      console.log('[#809 R5-postfix] 11 legacy price-memory before/after:', memoryBefore, '/', memoryAfter)
      expect(memoryAfter, '거래처가 채워진 legacy 저장인데 대상 품목 price-memory 가 생성되지 않음').not.toBe(memoryBefore)
      expect(memoryRow(PARTNER_A.id, target.productId), 'legacy price-memory 가 원 공급단가 기준이 아님').toBe(
        `${expectedMemoryPrice}|LINE_SAVE`,
      )
      await page.goto(`${BASE_URL}/sales/estimates/${target.id}/edit`)
      await expectUnitPriceDigits(page, target.unitPrice, 1, 'legacy 저장 후 재진입 공급단가 불변')
      await capture(page, '34-KEY-legacy-estimate-after-put-supply-price-unchanged-memory-created')

      // 역방향 provenance 가드: 값의 최종 동일성으로 "무수정"을 판정하면 안 된다.
      // 1920000→999000→1920000처럼 실제 입력을 거치면 legacyPriceUntouched=false,
      // 따라서 같은 숫자로 원복했어도 priceVatInclusive=true 로 전송되어야 한다.
      await expect(saveButton, '역방향 provenance 검증 전 저장 버튼 미활성').toBeEnabled({ timeout: 20000 })
      await unitPriceInput(page).fill('999000')
      await unitPriceInput(page).fill(target.unitPrice)
      await expectUnitPriceDigits(page, target.unitPrice, 1, 'legacy 가격 편집→원복 최종값')
      await capture(page, '35-KEY-legacy-price-edited-999000-restored-1920000-before-save')

      const editedRestoreResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'PUT'
          && response.url().includes(`/slips/estimates/${target.id}`),
        { timeout: 30000 },
      )
      await saveButton.click()
      const editedRestoreResponse = await editedRestoreResponsePromise
      expect(editedRestoreResponse.ok(), `편집→원복 PUT 실패: HTTP ${editedRestoreResponse.status()}`).toBeTruthy()
      const editedRestoreBody = editedRestoreResponse.request().postDataJSON() as {
        lines?: Array<{ unitPrice?: string | number; priceVatInclusive?: boolean }>
      }
      expect(Number(editedRestoreBody.lines?.[0]?.unitPrice), '편집→원복 PUT 최종 단가 불일치').toBe(
        Number(target.unitPrice),
      )
      expect(
        editedRestoreBody.lines?.[0]?.priceVatInclusive,
        '실제 가격 편집→원복을 legacy 가격 무수정으로 오판',
      ).toBe(true)

      const editedRestoreSnapshot = estimatePriceSnapshot(target.id)
      console.log('[#809 R5-postfix] 11 legacy 편집→원복 DB:', editedRestoreSnapshot)
      expect(editedRestoreSnapshot, 'priceVatInclusive=true 역방향 저장 DB 계약 불일치').toBe(
        `${target.productId}|${incorrectlyDividedSupplyUnit}|${Number(target.unitPrice).toFixed(2)}`,
      )
      await expect.poll(
        () => memoryRow(PARTNER_A.id, target.productId),
        {
          timeout: 5000,
          intervals: [25, 50, 100, 250, 500],
          message: '편집→원복 priceVatInclusive=true 기억값 반영 미완료',
        },
      ).toBe(`${Number(target.unitPrice).toFixed(2)}|LINE_SAVE`)
      await capture(page, '36-KEY-legacy-price-edited-restored-saved-as-vat-inclusive')
    } finally {
      // 실 legacy 문서를 반복 실행 가능하게 복구한다. 정확히 이 견적 헤더 1건과 방금 생성한
      // 라인 1건, (거래처, 품목) 기억쌍만 대상으로 하며 테이블 전체 DELETE/광역 정리는 하지 않는다.
      if (targetToRestore) {
        psql(
          `UPDATE estimate_lines
           SET unit_price=${targetToRestore.unitPrice}, unit_price_with_vat=NULL,
               supply_amount=${targetToRestore.unitPrice} * quantity,
               vat_amount=${targetToRestore.unitPrice} * quantity * 0.1,
               line_total=${targetToRestore.unitPrice} * quantity * 1.1
           WHERE estimate_id='${targetToRestore.id}' AND is_deleted=false`.replace(/\s+/g, ' '),
        )
        psql(`UPDATE estimates SET partner_id=NULL WHERE id='${targetToRestore.id}'`)
        resetMemoryPair(PARTNER_A.id, targetToRestore.productId)
      }
      await ctx.close()
    }
  })

  test('12a [R5-H7] 🔴 전표 BUNDLE — 신규 POST → 상세 무수정 PUT → 계보 보존·구성품 0·parent 1', async ({ browser }) => {
    test.slow()
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)
    resetMemoryPair(PARTNER_A.id, BUNDLE.id)
    BUNDLE_COMPONENT_IDS.forEach((productId) => resetMemoryPair(PARTNER_A.id, productId))

    try {
      await openSlipForm(page)
      await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER_A.query)
      await pickWarehouse(page)
      await pickAutocomplete(page, '라인 1 품목', '품목 목록', BUNDLE.model)
      await page.waitForTimeout(1200)
      await expectUnitPriceDigits(page, BUNDLE.sellingPrice, 1, '전표 BUNDLE 판매가')
      await unitPriceInput(page).fill(PRICE_BUNDLE)
      const slipId = await saveSlipAndWait(page)
      await expectMemoryRowEventually(PARTNER_A.id, BUNDLE.id, PRICE_BUNDLE, 'BUNDLE_SET')

      const componentIds = [...BUNDLE_COMPONENT_IDS].sort().join(',')
      const expectedSummary = `2|1|2|${componentIds}`
      const lineageBefore = bundleLineageSnapshot('slip_lines', slipId)
      console.log('[#809 R5-postfix] 12a 전표 POST 계보:', lineageBefore)
      expect(bundleLineageSummary('slip_lines', slipId), '전표 POST 세트 메타 불일치').toBe(expectedSummary)
      expect(bundleComponentMemoryCount(PARTNER_A.id), '전표 POST 구성품 기억행 오염').toBe('0')
      expect(memoryRowCount(PARTNER_A.id, BUNDLE.id), '전표 POST parent 기억행이 정확히 1건이 아님').toBe('1')
      expect(memoryRow(PARTNER_A.id, BUNDLE.id), '전표 POST parent source/단가 불일치').toBe(
        `${PRICE_BUNDLE}.00|BUNDLE_SET`,
      )

      await page.goto(`${BASE_URL}/sales/${slipId}`)
      await page.getByTestId('sales-slip-edit-button').click()
      const editModal = page.getByTestId('sales-slip-edit-modal')
      await expect(editModal, '전표 BUNDLE 상세 편집 모달 미표시').toBeVisible({ timeout: 20000 })
      await expect(editModal.getByLabel('단가(VAT제외) 1')).toBeVisible()
      await capture(page, '26-slip-bundle-detail-before-nochange-put')
      const updateResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'PUT' && response.url().includes(`/slips/${slipId}`),
        { timeout: 30000 },
      )
      await page.getByTestId('sales-slip-edit-save').click()
      const updateResponse = await updateResponsePromise
      expect(updateResponse.ok(), `전표 BUNDLE PUT 실패: HTTP ${updateResponse.status()}`).toBeTruthy()

      await expect.poll(() => bundleLineageSnapshot('slip_lines', slipId), {
        timeout: 5000,
        message: '전표 BUNDLE PUT 후 세트 계보/가격이 POST 직후와 다름',
      }).toBe(lineageBefore)
      expect(bundleLineageSummary('slip_lines', slipId), '전표 PUT 세트 메타 불일치').toBe(expectedSummary)
      expect(bundleComponentMemoryCount(PARTNER_A.id), '전표 PUT 구성품 기억행 오염(R5-H1 잔존)').toBe('0')
      expect(memoryRowCount(PARTNER_A.id, BUNDLE.id), '전표 PUT parent 기억행이 정확히 1건이 아님').toBe('1')
      expect(memoryRow(PARTNER_A.id, BUNDLE.id), '전표 PUT parent BUNDLE_SET 유실').toBe(
        `${PRICE_BUNDLE}.00|BUNDLE_SET`,
      )
      await capture(page, '27-slip-bundle-after-nochange-put')
    } finally {
      await ctx.close()
    }
  })

  test('12b [R5-H7] 🔴 견적 BUNDLE — 신규 POST → 상세 무수정 PUT → 계보 보존·구성품 0·parent 1', async ({ browser }) => {
    test.slow()
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)
    resetMemoryPair(PARTNER_B.id, BUNDLE.id)
    BUNDLE_COMPONENT_IDS.forEach((productId) => resetMemoryPair(PARTNER_B.id, productId))

    try {
      await openEstimateForm(page)
      await pickAutocomplete(page, '거래처 검색', '거래처 목록', PARTNER_B.query)
      await expect(page.getByLabel('거래처명')).toHaveValue(PARTNER_B.name, { timeout: 15000 })
      await fillEstimateModel(page, 1, BUNDLE.model)
      await expectUnitPriceDigits(page, BUNDLE.sellingPrice, 1, '견적 BUNDLE 판매가')
      await unitPriceInput(page).fill(PRICE_BUNDLE)
      const estimateId = await saveEstimateDraftAndGetId(page)
      await expectMemoryRowEventually(PARTNER_B.id, BUNDLE.id, PRICE_BUNDLE, 'BUNDLE_SET')

      const componentIds = [...BUNDLE_COMPONENT_IDS].sort().join(',')
      const expectedSummary = `2|1|2|${componentIds}`
      const lineageBefore = bundleLineageSnapshot('estimate_lines', estimateId)
      console.log('[#809 R5-postfix] 12b 견적 POST 계보:', lineageBefore)
      expect(bundleLineageSummary('estimate_lines', estimateId), '견적 POST 세트 메타 불일치').toBe(expectedSummary)
      expect(bundleComponentMemoryCount(PARTNER_B.id), '견적 POST 구성품 기억행 오염').toBe('0')
      expect(memoryRowCount(PARTNER_B.id, BUNDLE.id), '견적 POST parent 기억행이 정확히 1건이 아님').toBe('1')
      expect(memoryRow(PARTNER_B.id, BUNDLE.id), '견적 POST parent source/단가 불일치').toBe(
        `${PRICE_BUNDLE}.00|BUNDLE_SET`,
      )

      await page.goto(`${BASE_URL}/sales/estimates/${estimateId}/edit`)
      const saveButton = page.getByTestId('estimate-form-save-button')
      await expect(page.getByLabel('라인 1 모델명'), '견적 BUNDLE 편집 폼 미표시').toBeVisible({ timeout: 30000 })
      await expect(page.getByLabel('라인 2 모델명'), '견적 BUNDLE 구성품 2행 미표시').toBeVisible()
      await expect(saveButton, '견적 BUNDLE 저장 버튼 미활성').toBeEnabled({ timeout: 20000 })
      await capture(page, '28-estimate-bundle-detail-before-nochange-put')
      const updateResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'PUT'
          && response.url().includes(`/slips/estimates/${estimateId}`),
        { timeout: 30000 },
      )
      await saveButton.click()
      const updateResponse = await updateResponsePromise
      expect(updateResponse.ok(), `견적 BUNDLE PUT 실패: HTTP ${updateResponse.status()}`).toBeTruthy()

      await expect.poll(() => bundleLineageSnapshot('estimate_lines', estimateId), {
        timeout: 5000,
        message: '견적 BUNDLE PUT 후 세트 계보/가격이 POST 직후와 다름',
      }).toBe(lineageBefore)
      expect(bundleLineageSummary('estimate_lines', estimateId), '견적 PUT 세트 메타 불일치').toBe(expectedSummary)
      expect(bundleComponentMemoryCount(PARTNER_B.id), '견적 PUT 구성품 기억행 오염(R5-H1 잔존)').toBe('0')
      expect(memoryRowCount(PARTNER_B.id, BUNDLE.id), '견적 PUT parent 기억행이 정확히 1건이 아님').toBe('1')
      expect(memoryRow(PARTNER_B.id, BUNDLE.id), '견적 PUT parent BUNDLE_SET 유실').toBe(
        `${PRICE_BUNDLE}.00|BUNDLE_SET`,
      )
      await capture(page, '29-estimate-bundle-after-nochange-put')
    } finally {
      await ctx.close()
    }
  })

  /**
   * R5-H8 지연 주입: 모델 lookup 은 실 2xx 응답을 먼저 관측한다. 이어지는 단건 price-memory
   * 요청은 route.fetch() 로 실서버 응답을 그대로 받은 뒤 gate 동안만 hold 하고,
   * route.fulfill({ response }) 로 원본 status/header/body 를 무변조 전달한다. 합성/내용 변조 없음.
   */
  test('13 [R5-H8] 🔴 lookup→price 중간상태 — 저장 disabled·0원 POST 없음 → 실응답 후 기억단가 정확 적용', async ({ browser }) => {
    test.slow()
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)
    seedMemoryRow(PARTNER_A.id, PRODUCT_X.id, PRICE_P)

    let releasePriceResponse!: () => void
    let released = false
    const priceGate = new Promise<void>((resolve) => {
      releasePriceResponse = () => {
        if (released) return
        released = true
        resolve()
      }
    })
    let resolveUpstreamReady!: () => void
    const upstreamReady = new Promise<void>((resolve) => { resolveUpstreamReady = resolve })
    let upstreamStatus = 0
    let estimatePostCount = 0
    page.on('request', (request) => {
      if (request.method() === 'POST' && /\/estimates(\?|$)/.test(request.url())) estimatePostCount += 1
    })

    await page.route('**/slips/price-memory?*', async (route) => {
      const response = await route.fetch() // 실서버 실응답 — 내용 변조/합성 금지
      upstreamStatus = response.status()
      resolveUpstreamReady()
      await priceGate
      await route.fulfill({ response })
    })

    try {
      await openEstimateForm(page)
      await pickAutocomplete(page, '거래처 검색', '거래처 목록', PARTNER_A.query)
      await expect(page.getByLabel('거래처명')).toHaveValue(PARTNER_A.name, { timeout: 15000 })

      const lookupResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'GET'
          && response.url().includes('/slips/lookup-product')
          && response.url().includes(`modelName=${encodeURIComponent(PRODUCT_X.model)}`),
        { timeout: 30000 },
      )
      const priceResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'GET'
          && response.url().includes('/slips/price-memory?'),
        { timeout: 30000 },
      )
      const model = page.getByLabel('라인 1 모델명')
      await model.fill(PRODUCT_X.model)
      await model.blur()

      const lookupResponse = await lookupResponsePromise
      expect(lookupResponse.ok(), `실 모델 lookup 실패: HTTP ${lookupResponse.status()}`).toBeTruthy()
      await upstreamReady
      expect(upstreamStatus, 'hold 대상 price-memory 실 upstream 이 2xx 아님').toBeGreaterThanOrEqual(200)
      expect(upstreamStatus, 'hold 대상 price-memory 실 upstream 이 2xx 아님').toBeLessThan(300)

      // lookup 2xx 완료 + price-memory upstream 완료, 브라우저 전달 전의 정확한 중간 창.
      const busy = page.getByTestId('estimate-form-price-refresh-busy')
      const saveButton = page.getByTestId('estimate-form-save-button')
      await expect(busy, 'lookup→price 중간 창 busy 단서 미표시').toHaveText('최근단가 확인 중…')
      await expect(saveButton, 'lookup→price 중간 창 저장 미차단').toBeDisabled()
      await expectUnitPriceDigits(page, '0', 1, 'price-memory 응답 전 중간 단가')
      await saveButton.click({ force: true })
      await page.waitForTimeout(400)
      expect(estimatePostCount, 'disabled 상태에서 0원 견적 POST 발생').toBe(0)
      await capture(page, '30-KEY-estimate-model-lookup-done-price-memory-held-save-disabled-zero')

      releasePriceResponse()
      const priceResponse = await priceResponsePromise
      expect(priceResponse.ok(), `전달된 실 price-memory 응답 실패: HTTP ${priceResponse.status()}`).toBeTruthy()
      await expect(busy, 'price-memory 응답 후 busy 고착').toHaveText('', { timeout: 15000 })
      await expect(saveButton, 'price-memory 응답 후 저장 버튼 미복구').toBeEnabled()
      await expect(page.getByLabel('라인 1 품목명')).toHaveValue(PRODUCT_X.name, { timeout: 10000 })
      await expectUnitPriceDigits(page, PRICE_P, 1, 'price-memory 응답 후 기억단가')
      await expect(recentMarkers(page), 'price-memory 응답 후 최근단가 마커 미표시').toHaveCount(1)
      await capture(page, '31-KEY-estimate-price-memory-resolved-888000-save-enabled')

      const estimateId = await saveEstimateDraftAndGetId(page)
      expect(estimatePostCount, '응답 완료 뒤 견적 POST 가 정확히 1건이 아님').toBe(1)
      const savedLine = estimatePriceSnapshot(estimateId)
      console.log('[#809 R5-postfix] 13 저장 DB:', savedLine)
      expect(savedLine, '응답 후 저장된 견적 단가가 888000(VAT포함)이 아님').toBe(
        // 수량1: round(888000 / 1.1)=807273 (HALF_UP). 기존 807272.50 행은 수량2의
        // round(1776000 / 1.1)=1614545 를 2로 나눈 값 — R5 실코드/실DB 대조 완료.
        `${PRODUCT_X.id}|807273.00|${PRICE_P}.00`,
      )
      expect(memoryRow(PARTNER_A.id, PRODUCT_X.id), 'H8 저장 후 price-memory 값 변형').toBe(
        `${PRICE_P}.00|LINE_SAVE`,
      )
      await capture(page, '32-estimate-price-memory-resolved-888000-saved')
    } finally {
      releasePriceResponse()
      await page.unroute('**/slips/price-memory?*')
      await ctx.close()
    }
  })
})
