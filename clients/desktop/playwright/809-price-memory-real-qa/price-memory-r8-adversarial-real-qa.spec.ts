/**
 * #809 R8 — OPUS 4.8 1차 적대검증 QA(라이브) 재현 스펙 (mock OFF, 실 게이트웨이 :8080 → 실 Postgres).
 *
 * 왜 신규 파일인가 — 기존 `price-memory-r2-live-real-qa.spec.ts` 는 합성 시드 품목
 * (AC200CNCDEH-77 / AC300CNCDEH-78 / QA797-SET-01)에 의존하는데 그 품목들이 현 스택의
 * product_db 에서 전량 소멸했다(R8 실측: `/api/products?q=AC200CNCDEH-77` → totalElements=0,
 * `q=QA797` → 0, `products.product_code` 는 1116행 전부 NULL). 그래서 그 파일은 현재
 * 0 passed / 10 failed / 9 did not run 이다(describe.serial 연쇄 skip). 본 스펙은 **실 카탈로그에
 * 실재하는 품목만** 사용해 R8 적대검증 3건을 재현·박제한다. 기존 파일의 단언은 일절 건드리지 않는다.
 *
 * 사용 실품목(실 DB·실 API 실측):
 *  - 세트 AF17B6474GZS (21b20ce9-…-a2571c782d09) BUNDLE / 판매가 1,813,000
 *      → 전개 구성품 2종: AF17B6474GZN(f199c745, head) · AF17B6470DCX(c9c200ad)
 *  - 단품 AC032CN1DBC1 (a5b924cb-…-c9492c361b38) SINGLE / 판매가 334,400
 *  - 거래처 한울냉열시스템 (44f0cfc1-…-04ad5fa70922)
 *  - 창고 5ab14cf6-…-0c04ef60fee9
 *
 * 실행:
 *   cd clients/desktop
 *   VITE_API_BASE_URL=http://localhost:8080 node_modules/.bin/vite \
 *     --config playwright/809-price-memory-real-qa/vite.809-realqa.config.ts --port 5218 --strictPort
 *   QA_BASE_URL=http://localhost:5218 node_modules/.bin/playwright test \
 *     --config=playwright.real-qa.config.ts \
 *     playwright/809-price-memory-real-qa/price-memory-r8-adversarial-real-qa.spec.ts
 */
import { expect, test, type Page } from '@playwright/test'
import * as path from 'path'
import * as fs from 'fs'
import { execSync } from 'child_process'
import { fileURLToPath } from 'url'

const _dirname =
  typeof __dirname !== 'undefined' ? __dirname : path.dirname(fileURLToPath(import.meta.url))
const BASE_URL = process.env['QA_BASE_URL'] ?? 'http://localhost:5218'
const API_BASE = process.env['API_BASE'] ?? 'http://localhost:8080'
const PASSWORD = process.env['DEV_PASSWORD'] ?? 'dev_p05_pass!'
const ACCOUNT = 'dev_manager'
// r2/·r4/·r4-postfix/·r5/·r5-postfix/·r6/·r6-postfix/·r8/ 는 이력 보존 — 불가침.
// R8 적대리뷰 자체의 증거는 r8/ 에 박제돼 있다(RED 재현 19장). 본 스펙을 R8 fix 후 재실행하면
// 그 증거를 덮어쓰므로, post-fix 재검증 캡처는 r8-postfix/ 신규 디렉토리로 분리한다.
const SHOTS = path.resolve(_dirname, '../../../../docs/qa/809-partner-product-price-memory/r8-postfix')
fs.mkdirSync(SHOTS, { recursive: true })

const PARTNER = { id: '44f0cfc1-4a5f-4206-85cd-04ad5fa70922', name: '한울냉열시스템' }
/** [R8-postfix] D-R8-7 거래처 변경 검증용 두 번째 실 거래처 — 실 DB 실측(partner_db.partners). */
const OTHER_PARTNER = { id: '2cff65ba-2ec6-445c-9081-f277adcefce1', name: '(B.E.S.T)에어컨' }
const WAREHOUSE = '5ab14cf6-d97e-40c4-b991-0c04ef60fee9'
const BUNDLE = { id: '21b20ce9-d972-46d3-81dc-a2571c782d09', model: 'AF17B6474GZS' }
/** 세트 전개 구성품 — head(GZN) + 구성품(DCX). */
const COMP_HEAD = { id: 'f199c745-0629-496f-b04a-8e30e529549e', model: 'AF17B6474GZN' }
const COMP_TAIL = { id: 'c9c200ad-c75a-44a6-b1cd-a813267bfc45', model: 'AF17B6470DCX' }
/** 세트와 무관한 순수 단품 — 계보 오귀속의 피해자. */
const SINGLE = { id: 'a5b924cb-3a9c-44a8-813e-c9492c361b38', model: 'AC032CN1DBC1' }
// 전표 폼 품목 자동완성은 usageScope=PARTNER_ORDER 로 좁혀 검색한다(SlipFormPage:1291).
// SINGLE(AC032CN1DBC1)은 usage_scope=NONE 이라 GUI 검색에 뜨지 않는다 — QA-4 는 BOTH 스코프 실품목 사용.
const UI_HIT = { id: '3b342b65-0375-4c19-a51f-abc23c50e1ed', model: 'ACD-2558G' }
const UI_MISS = { id: '0de09f0a-491e-482e-9b57-e99c2f0deca1', model: 'ACM-A202DN' }

function psql(sql: string): string {
  // docker exec -tAc 는 한 줄 SQL 만 받는다 — 개행/연속공백을 접지 않으면 syntax error.
  const flat = sql.replace(/\s+/g, ' ').trim().replace(/"/g, '\\"')
  return execSync(`docker exec samhan-postgres psql -U samhan -d slip_db -tAc "${flat}"`, {
    encoding: 'utf-8',
  }).trim()
}

async function capture(page: Page, name: string): Promise<void> {
  await page.screenshot({ path: path.join(SHOTS, `${name}.png`), fullPage: false })
}

interface LoginResult { token: string; role: string; userId: string; displayName: string }

async function realLogin(page: Page, loginId: string): Promise<LoginResult> {
  const res = await page.request.post(`${API_BASE}/auth/login`, { data: { loginId, password: PASSWORD } })
  expect(res.ok(), `로그인 실패(${loginId}): HTTP ${res.status()}`).toBeTruthy()
  const d = (await res.json()).data ?? {}
  return { token: d.token ?? '', role: d.role ?? '', userId: d.userId ?? '', displayName: d.displayName ?? loginId }
}

async function login(page: Page): Promise<LoginResult> {
  const l = await realLogin(page, ACCOUNT)
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
    { tok: l.token, r: l.role, uid: l.userId, name: l.displayName },
  )
  return l
}

function authHeaders(auth: LoginResult): Record<string, string> {
  return { Authorization: `Bearer ${auth.token}`, 'Content-Type': 'application/json' }
}

/** 세트 전개(구성품 2) + 순수 단품 1 = 3라인 전표를 실 API 로 만든다. */
async function createBundlePlusSingleSlip(page: Page, auth: LoginResult): Promise<string> {
  const res = await page.request.post(`${API_BASE}/slips`, {
    headers: authHeaders(auth),
    data: {
      slipType: 'OUTBOUND',
      partnerId: PARTNER.id,
      partnerName: PARTNER.name,
      sourceWarehouseId: WAREHOUSE,
      lines: [
        { productId: BUNDLE.id, quantity: 1, unitPrice: 1813000 },
        { productId: SINGLE.id, quantity: 1, unitPrice: 334400 },
      ],
    },
  })
  expect(res.ok(), `전표 생성 실패: HTTP ${res.status()} ${await res.text().catch(() => '')}`).toBeTruthy()
  const id = (await res.json()).data.id as string
  // 전제: 화면 표시순 = [head(GZN), 구성품(DCX), 단품(AC032CN1DBC1)]
  expect(
    psql(`SELECT string_agg(model_name || ':' || set_head || ':' || coalesce(parent_set_model,'-'), '|' ORDER BY created_at) FROM slip_lines WHERE slip_id='${id}' AND is_deleted=false`),
    '전표 생성 직후 계보 전제 붕괴',
  ).toBe(`${COMP_HEAD.model}:true:${BUNDLE.model}|${COMP_TAIL.model}:false:${BUNDLE.model}|${SINGLE.model}:false:-`)
  return id
}

/**
 * 대상 (거래처, 품목) 쌍의 기억행을 물리 삭제해 테스트 창구간을 격리한다.
 * 공유 dev 스택이라 다른 라운드/에이전트가 남긴 잔여행이 단언을 오염시킬 수 있다.
 */
function resetMemoryPairs(productIds: string[]): void {
  psql(
    `DELETE FROM partner_product_price_memory WHERE partner_id='${PARTNER.id}'
       AND product_id IN (${productIds.map((p) => `'${p}'`).join(',')})`,
  )
}

function memoryOf(productId: string): string {
  return memoryOfFor(PARTNER.id, productId)
}

/** [R8-postfix] 임의 거래처 기준 기억행 조회 — D-R8-7(거래처 변경 시 새 거래처 각인) 검증용. */
function memoryOfFor(partnerId: string, productId: string): string {
  return psql(
    `SELECT coalesce((SELECT unit_price || '/' || source FROM partner_product_price_memory
       WHERE partner_id='${partnerId}' AND product_id='${productId}' AND is_deleted=false), 'NONE')`,
  )
}

/** [R8-postfix] 임의 거래처 기준 기억행 리셋 — 거래처 변경 검증은 두 거래처 모두 비워야 결정적이다. */
function resetMemoryPairsFor(partnerId: string, productIds: string[]): void {
  psql(
    `DELETE FROM partner_product_price_memory WHERE partner_id='${partnerId}'
       AND product_id IN (${productIds.map((p) => `'${p}'`).join(',')})`,
  )
}

function lineageOf(slipId: string): string {
  return psql(
    `SELECT string_agg(model_name || ':' || set_head || ':' || coalesce(parent_set_model,'-'), '|' ORDER BY created_at)
     FROM slip_lines WHERE slip_id='${slipId}' AND is_deleted=false`,
  )
}

/** 자동완성 실 후보만 매칭 — '검색 중…' 로딩행도 role=option 이라 id 접두사로 좁힌다. */
const realOptions = (page: Page, listboxLabel: string, idPrefix = 'ds-aac-list-') =>
  page.getByRole('listbox', { name: listboxLabel }).first().locator(`li[id^="${idPrefix}"]`)

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
  const options = realOptions(page, '창고 목록', 'ds-wh-list-')
  await expect(options.first(), '창고 후보 미표시').toBeVisible({ timeout: 20000 })
  await input.press('ArrowDown')
  await input.press('Enter')
  await expect(options.first(), '창고 확정 실패').toBeHidden({ timeout: 10000 })
  await page.waitForTimeout(200)
}

/** 매출 상세 진입 + '수정' 클릭 → coedit 편집 모달. provider 로드까지 대기. */
async function openSalesEdit(page: Page, slipId: string): Promise<void> {
  await page.goto(`${BASE_URL}/sales/${slipId}`)
  await page.getByTestId('sales-slip-edit-button').waitFor({ state: 'visible', timeout: 30000 })
  await page.getByTestId('sales-slip-edit-button').click()
  // coedit provider 로드 완료 = 라인 입력이 편집 가능해질 때.
  await expect(page.getByLabel('단가(VAT제외) 1')).toBeEnabled({ timeout: 30000 })
  await page.waitForTimeout(1500)
}

test.describe('#809 R8 — OPUS 4.8 적대검증 라이브 재현', () => {
  /**
   * R8-QA-1 [BLOCKING] — lineId 미전송 PUT 이 세트 계보를 파괴하고 구성품 배분가를 각인한다.
   *
   * BE 계약(SlipUpdateRequest.LineRequest)은 lineId 를 "구 클라이언트 호환" 명목으로 optional 로
   * 열어두고(7-필드 호환 생성자 존재), null 이면 `BundleLineageResolver.assign` 이 즉시 return 해
   * 계보를 승계하지 않는다. 그 결과 **무수정 왕복 PUT 이 HTTP 200 으로 계보를 전량 파괴**하고,
   * 계보를 잃은 구성품이 `collectPriceMemory` 의 isBundleComponent 필터를 빠져나가
   * **구성품 배분가가 LINE_SAVE 로 각인**된다(= 이 PR 이 막으려는 오염 그 자체).
   *
   * 이 경로는 기존 스펙 자신의 헬퍼 `mirrorSlipLine`(lineId 미포함)이 그대로 밟는다.
   */
  test('R8-QA-1 [BLOCKING·fix 가드] 계약 마커 없는 lineId 미전송 무수정 PUT → 400 거부 · 세트 계보 보존 · 기억 미오염', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const auth = await login(page)
    resetMemoryPairs([COMP_HEAD.id, COMP_TAIL.id, SINGLE.id])
    const slipId = await createBundlePlusSingleSlip(page, auth)

    // 생성 직후 전제: 구성품은 기억되지 않는다(parent 만 BUNDLE_SET). 이게 정상 동작.
    expect(memoryOf(COMP_HEAD.id), 'R8-QA-1 전제: 생성 시 head 구성품은 기억되지 않아야 함').toBe('NONE')
    expect(memoryOf(COMP_TAIL.id), 'R8-QA-1 전제: 생성 시 구성품은 기억되지 않아야 함').toBe('NONE')

    await page.goto(`${BASE_URL}/sales/${slipId}`)
    await expect(page.getByText(COMP_HEAD.model).first()).toBeVisible({ timeout: 30000 })
    await capture(page, '01-r8-qa-1-slip-detail-set-lineage-intact')

    expect(lineageOf(slipId), 'PUT 전 계보 전제').toBe(
      `${COMP_HEAD.model}:true:${BUNDLE.model}|${COMP_TAIL.model}:false:${BUNDLE.model}|${SINGLE.model}:false:-`,
    )

    // 구 클라이언트 / 기존 스펙 mirrorSlipLine 과 동일한 lineId 미포함 무수정 왕복 PUT.
    const detail = (await (await page.request.get(`${API_BASE}/slips/${slipId}`, { headers: authHeaders(auth) })).json()).data
    const res = await page.request.put(`${API_BASE}/slips/${slipId}/sales`, {
      headers: authHeaders(auth),
      data: {
        updatedAt: detail.updatedAt,
        partnerName: detail.partnerName, partnerCode: detail.partnerCode,
        memo: detail.memo, businessNumber: detail.businessNumber,
        deliveryAddress: detail.deliveryAddress, supervisionAddress: detail.supervisionAddress,
        projectName: detail.projectName, recipientPhone: detail.recipientPhone,
        paymentDueDate: detail.paymentDueDate,
        lines: detail.lines.map((l: Record<string, unknown>) => ({
          productId: l['productId'], productName: l['productName'], modelName: l['modelName'],
          specification: l['specification'], quantity: l['quantity'],
          unitPrice: String(l['unitPrice']), note: l['note'],
        })),
      },
    })
    // D-R8-6 + D-R8-9 이후: 계약 마커(lineIdContract) 없는 요청 = 구 클라이언트 → 400 거부.
    // R8 리뷰 시점에는 이 단언이 `toBe(200)` 이었고 그게 곧 BLOCKING 의 증거였다(거부 없이 통과).
    expect(res.status(), 'R8-QA-1 가드: 계약 마커 없는 lineId 미전송 PUT 은 400 으로 거부돼야 함(구 클라이언트 차단)').toBe(400)

    await page.reload()
    await expect(page.getByText(SINGLE.model).first()).toBeVisible({ timeout: 30000 })
    // R8 리뷰 시점 캡처명은 '…-lineage-destroyed' 였다 — 그때는 실제로 파괴됐기 때문이다(r8/ 에 박제).
    // fix 후에는 400 거부로 계보가 보존되므로 이름도 사실에 맞춘다.
    await capture(page, '02-r8-qa-1-after-rejected-put-lineage-preserved')

    // 400 으로 거부됐으므로 계보는 PUT 전과 동일해야 한다(R8 리뷰 시점엔 set_head 전부 f · parent 전부 NULL 로 파괴됐다).
    expect(lineageOf(slipId), 'R8-QA-1 가드: 거부된 PUT 이 세트 계보를 건드리지 않아야 함(데이터 손실 차단)').toBe(
      `${COMP_HEAD.model}:true:${BUNDLE.model}|${COMP_TAIL.model}:false:${BUNDLE.model}|${SINGLE.model}:false:-`,
    )
    // 계보가 살아 있으므로 구성품은 isBundleComponent 필터에 계속 걸려 기억되지 않아야 한다.
    // (R8 리뷰 시점엔 계보를 잃어 필터를 통과, 구성품 배분가 501600·752400 이 LINE_SAVE 로 각인됐다.)
    expect(memoryOf(COMP_HEAD.id), 'R8-QA-1 가드: 거부된 PUT 이 head 구성품 배분가를 각인하지 않아야 함').toBe('NONE')
    expect(memoryOf(COMP_TAIL.id), 'R8-QA-1 가드: 거부된 PUT 이 구성품 배분가를 각인하지 않아야 함').toBe('NONE')
    await ctx.close()
  })

  /**
   * R8-QA-2a [BLOCKING·fix 가드] — R8 fix 후, 원래의 BLOCKING 트리거가 **봉쇄**됐는지 확인한다.
   *
   * R8 리뷰가 라이브 2/2 로 재현한 원 결함: `SlipDetailPage.coeditLinesToEditLines` 가 Y.Doc 행의
   * 셀 값은 직독하면서 **lineId 만은 로컬 배열 `current[index]` 에서 위치로 집었다**. 원격 피어가
   * 행을 지우면 Y.Array 가 통째 교체돼 인덱스가 밀리는데 수신 창의 `current` 는 아직 옛 길이라,
   * 삭제 지점 이후 모든 행이 **"내용은 다음 행, lineId 는 이전 행"** 으로 어긋난 채 PUT 됐다.
   *
   * R8 fix 는 두 겹으로 대응했다:
   *  1. **근본 fix** — `coeditLineIds.resolveServerLineId` 로 Y.Doc lineId 직독 + 서버 소유검증
   *  2. **심층방어** — coedit 중 라인 구조(행 삭제) 잠금 (`slipCoeditActive`)
   *
   * 이 테스트는 2번(잠금)을 단언한다. 1번(직독)은 **이 경로로는 더 이상 실증할 수 없다** —
   * 잠금이 트리거를 막아 원 시나리오가 GUI 로 도달 불가가 됐기 때문이다. 그 정직 고지는
   * 보고서에 남기고, 아직 열려 있는 경로는 R8-QA-2b 가 맡는다.
   */
  test('R8-QA-2a [BLOCKING·fix 가드] coedit 중 라인 구조 잠금 — 원격 삭제로 lineId 를 밀 어포던스 자체가 봉쇄', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const auth = await login(page)
    const slipId = await createBundlePlusSingleSlip(page, auth)

    await openSalesEdit(page, slipId)
    await capture(page, '03-r8-qa-2a-coedit-edit-modal-3lines')

    // 🔴 fix 가드 — coedit 활성 중에는 행 삭제 버튼이 비활성이어야 한다.
    //    R8 리뷰 시점(6ae5ccde9)엔 이 버튼이 활성이었고, 그게 원격삭제 → lineId 밀림 →
    //    계보 오귀속 BLOCKING 의 입구였다. 현재는 slipCoeditActive 로 잠긴다
    //    (SlipDetailPage:1704 `disabled={slipCoeditActive}` — 견적 lineStructureLocked 와 동일 계약).
    const del = page.getByRole('button', { name: '1번 행 삭제' })
    await expect(del, 'R8-QA-2a: 행 삭제 버튼 미표시').toBeVisible({ timeout: 10000 })
    await expect(
      del,
      'R8-QA-2a fix 가드: coedit 중 행 삭제가 활성 — 원격삭제 lineId 밀림 경로가 다시 열림(R8-QA-2 회귀)',
    ).toBeDisabled()
    await expect(del).toHaveAttribute('title', '협업 편집 중에는 행을 삭제할 수 없습니다')
    await capture(page, '04-r8-qa-2a-row-delete-disabled-during-coedit')
    await ctx.close()
  })

  /**
   * R8-QA-2b [BLOCKING·불변식 보존] — R8-QA-2 의 **불변식은 그대로** 두고, R8 fix 이후에도
   * **여전히 열려 있는 문**으로 같은 결함 계열을 유발할 수 있는지 확인한다.
   *
   * 왜 이 테스트가 필요한가: R8 fix 는 coedit 편집 모달의 행삭제(`×` → `removeSalesLine` →
   * Y.Doc `replaceItems`)를 **잠갔다**(R8-QA-2a). 그래서 R8-QA-2 의 원래 트리거는 GUI 로 도달
   * 불가가 됐다. 그러나 라인 삭제 경로는 **하나가 아니다** — 상세화면 툴바의 '행 삭제'
   * (`handleRemoveLine` → **BE DELETE**, SlipDetailPage:1270)는 `linesEditable`(status DRAFT/SAVED)
   * 만 보고 **`slipCoeditActive` 를 보지 않는다**. 즉 피어 A 가 상세화면에서 서버측 행삭제를
   * 하는 동안 피어 B 는 coedit 편집 모달에 머무를 수 있다.
   *
   * 이게 왜 위험한가: `coeditLineIds.ts` 의 재시드 게이트 주석은
   *   *"전표/견적 모두 coedit 중 라인 추가·삭제를 잠그므로(seed-lock) Y.Doc 행은 전부 seed 유래다"*
   * 를 **안전성의 근거로 명시**한다. 서버측 삭제는 그 전제 밖이다 — B 의 Y.Doc 은 서버에서
   * 사라진 라인의 lineId 를 계속 들고 있고, B 의 `knownServerLineIds` 도 삭제 이전 스냅샷이라
   * `resolveServerLineId` 가 그 lineId 를 **정상 승인**한다.
   *
   * 불변식은 R8 리뷰 원본과 동일하다 — 어떤 경로로도 깨져선 안 된다.
   */
  test('R8-QA-2b [BLOCKING·불변식] 피어 coedit 중 서버측 행 삭제(툴바) → 수신창 저장 — 계보 오귀속·head 탈취·단가 증발 없음', async ({ browser }) => {
    const ctxA = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const ctxB = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const pageA = await ctxA.newPage()
    const pageB = await ctxB.newPage()
    const auth = await login(pageA)
    await login(pageB)

    resetMemoryPairs([COMP_HEAD.id, COMP_TAIL.id, SINGLE.id])
    const slipId = await createBundlePlusSingleSlip(pageA, auth)
    const NEW_SINGLE_PRICE = '299000' // 수신창이 단품 라인에 새로 입력할 단가(VAT 제외)
    const expectedMemory = '328900.00/LINE_SAVE' // 299000 × 1.1
    // 생성 직후 단품 기억 = 334400 × 1.1. 저장이 정상이면 328900 으로 갱신돼야 한다.
    expect(memoryOf(SINGLE.id), 'R8-QA-2b 전제: 생성 시 단품은 판매가 기준으로 기억됨').toBe('367840.00/LINE_SAVE')

    // 창B: coedit 편집 모달 진입 → 단품(3행) 단가 직접 입력. 이 기억이 살아남아야 정상.
    await openSalesEdit(pageB, slipId)
    await capture(pageB, '05-r8-qa-2b-windowB-coedit-3lines')
    await pageB.getByLabel('단가(VAT제외) 3').fill(NEW_SINGLE_PRICE)
    await pageB.waitForTimeout(600)
    await capture(pageB, '06-r8-qa-2b-windowB-single-price-entered-299000')

    // 창A: 상세화면(편집 모달 아님) → 1행(세트 head) 선택 → 툴바 '행 삭제' → BE DELETE.
    // 이 경로는 slipCoeditActive 게이트 밖이라 B 가 coedit 중이어도 막히지 않는다.
    await pageA.goto(`${BASE_URL}/sales/${slipId}`)
    await pageA.getByTestId('sales-slip-edit-button').waitFor({ state: 'visible', timeout: 30000 })
    await pageA.getByRole('button', { name: '라인 1 선택' }).click()
    await pageA.waitForTimeout(400)
    await capture(pageA, '07-r8-qa-2b-windowA-line1-selected-toolbar')
    pageA.once('dialog', (d) => void d.accept())
    const delRes = pageA.waitForResponse(
      (r) => r.request().method() === 'DELETE' && r.url().includes(`/slips/${slipId}/lines/`),
      { timeout: 30000 },
    )
    await pageA.getByRole('button', { name: '행 삭제', exact: true }).click()
    const delStatus = (await delRes).status()
    console.log('[R8-QA-2b] 창A 서버측 행삭제 DELETE 상태:', delStatus)
    await pageA.waitForTimeout(1500)
    await capture(pageA, '08-r8-qa-2b-windowA-after-server-side-row-delete')

    // 창B 저장 — B 의 Y.Doc/knownServerLineIds 는 삭제 이전 스냅샷이다.
    const putBody: string[] = []
    pageB.on('request', (r) => {
      if (r.method() === 'PUT' && r.url().includes(`/slips/${slipId}/sales`)) putBody.push(r.postData() ?? '')
    })
    const putRes = pageB.waitForResponse(
      (r) => r.request().method() === 'PUT' && r.url().includes(`/slips/${slipId}/sales`),
      { timeout: 30000 },
    )
    await capture(pageB, '09-r8-qa-2b-windowB-before-save-after-remote-server-delete')
    await pageB.getByRole('button', { name: '저장', exact: true }).first().click()
    const resp = await putRes
    const putStatus = resp.status()
    console.log('[R8-QA-2b] 창B PUT 상태:', putStatus)
    console.log('[R8-QA-2b] 창B PUT body:', putBody.join('\n'))
    await pageB.waitForTimeout(2500)
    await capture(pageB, '10-r8-qa-2b-windowB-after-save')

    await pageB.reload()
    await pageB.getByTestId('sales-slip-edit-button').waitFor({ state: 'visible', timeout: 30000 })
    await capture(pageB, '11-r8-qa-2b-final-detail-after-save')

    const finalLineage = lineageOf(slipId)
    console.log('[R8-QA-2b] 저장 후 계보:', finalLineage)

    // 🔴 불변식 1 — 세트와 무관한 단품은 어떤 경우에도 세트 구성품이 될 수 없다. (R8 원본 동일)
    expect(
      psql(`SELECT count(*) FROM slip_lines WHERE slip_id='${slipId}' AND is_deleted=false
              AND product_id='${SINGLE.id}' AND parent_set_model IS NOT NULL`),
      `R8-QA-2b: 단품 ${SINGLE.model} 이 세트 구성품으로 오귀속됨 (계보=${finalLineage} · PUT=${putStatus})`,
    ).toBe('0')

    // 🔴 불변식 2 — head 행이 삭제된 뒤 남은 구성품이 head 지위를 훔쳐선 안 된다. (R8 원본 동일)
    expect(
      psql(`SELECT count(*) FROM slip_lines WHERE slip_id='${slipId}' AND is_deleted=false
              AND product_id='${COMP_TAIL.id}' AND set_head=true`),
      `R8-QA-2b: 삭제된 head 의 setHead 가 잔존 구성품으로 이식됨 (계보=${finalLineage} · PUT=${putStatus})`,
    ).toBe('0')

    // 🔴 불변식 3 — 저장 결과가 **일관**돼야 한다. 저장이 2xx 로 성공했다고 사용자에게 말했다면
    //    사용자가 입력한 299000(×1.1=328900)이 기억돼야 한다. 성공을 보고하고 입력을 조용히
    //    버리는 것이 R8-QA-2 가 적발한 결함의 본질이다. 거부(4xx/409)라면 사용자는 최소한
    //    자기 입력이 반영되지 않았음을 안다 — 그 경우는 이 불변식의 대상이 아니다.
    if (putStatus >= 200 && putStatus < 300) {
      expect(
        memoryOf(SINGLE.id),
        `R8-QA-2b: 저장이 ${putStatus} 로 성공했는데 사용자 입력 단가(${NEW_SINGLE_PRICE})의 기억이 증발함`,
      ).toBe(expectedMemory)
    } else {
      console.log(`[R8-QA-2b] 저장이 ${putStatus} 로 거부됨 — 사용자 입력 증발은 불변식 대상 아님(사용자가 인지 가능)`)
    }

    await ctxA.close()
    await ctxB.close()
  })

  /**
   * R8-QA-6 [HIGH] — 라인의 품목을 교체(lineId 유지)하면 옛 세트 계보가 새 품목에 이식되고
   * 그 품목의 가격기억이 조용히 증발한다.
   *
   * `BundleLineageResolver.assign` 은 lineId 로 **옛 라인의 계보만** 조회해 새 라인에 이식할 뿐,
   * "그 lineId 의 옛 productId 와 지금 productId 가 같은가" 를 확인하지 않는다. 따라서 사용자가
   * 라인의 모델명을 바꿔 전혀 다른 품목으로 교체해도 lineId 는 그대로 왕복되므로 옛 계보가
   * 새 품목에 이식된다. 이식된 품목은 isBundleComponent 필터에 걸려 사용자가 입력한 단가의
   * 기억이 아예 생성되지 않는다.
   *
   * R8 실측(전표): 세트 head 라인의 productId 만 무관한 단품 ACD-2558G 로 교체 + 단가 150000 →
   *   ACD-2558G:set_head=true:parent=AF17B6474GZS · 기억행 NONE (165000 미생성). PUT 은 200.
   * 견적 경로도 `restoreEstimateLines` → 동일 `assign` 이라 같은 계약을 공유한다.
   */
  test('R8-QA-6 [HIGH] 라인 품목 교체(lineId 유지) → 무관한 단품이 세트 head 로 오귀속 + 사용자 단가 기억 증발', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const auth = await login(page)
    resetMemoryPairs([UI_HIT.id])
    const SWAP_PRICE = '150000'
    const expectedMemory = '165000.00/LINE_SAVE' // 150000 × 1.1

    // 세트만 있는 전표(구성품 2행) 생성.
    const created = await page.request.post(`${API_BASE}/slips`, {
      headers: authHeaders(auth),
      data: {
        slipType: 'OUTBOUND', partnerId: PARTNER.id, partnerName: PARTNER.name,
        sourceWarehouseId: WAREHOUSE,
        lines: [{ productId: BUNDLE.id, quantity: 1, unitPrice: 1813000 }],
      },
    })
    expect(created.ok(), '세트 전표 생성 실패').toBeTruthy()
    const slipId = (await created.json()).data.id as string
    expect(lineageOf(slipId), 'R8-QA-6 전제: 세트 전개 계보').toBe(
      `${COMP_HEAD.model}:true:${BUNDLE.model}|${COMP_TAIL.model}:false:${BUNDLE.model}`,
    )

    await page.goto(`${BASE_URL}/sales/${slipId}`)
    await page.getByTestId('sales-slip-edit-button').waitFor({ state: 'visible', timeout: 30000 })
    await capture(page, '18-r8-qa-6-set-slip-before-product-swap')

    // head 구성품 라인의 lineId 는 유지한 채 productId/modelName 만 무관한 단품으로 교체.
    const detail = (await (await page.request.get(`${API_BASE}/slips/${slipId}`, { headers: authHeaders(auth) })).json()).data
    const res = await page.request.put(`${API_BASE}/slips/${slipId}/sales`, {
      headers: authHeaders(auth),
      data: {
        updatedAt: detail.updatedAt,
        // [D-R8-9] 이 케이스는 "정상 최신 클라이언트가 구성품의 품목을 교체" 하는 시나리오다 —
        // 계약 마커를 실어야 lineId 시맨틱이 활성화되고, 그래야 D-R8-8 productId 게이트가
        // 검증 대상이 된다. 마커가 없으면 400 에 막혀 이 케이스 자체가 성립하지 않는다.
        lineIdContract: true,
        partnerName: detail.partnerName, partnerCode: detail.partnerCode, memo: detail.memo,
        businessNumber: detail.businessNumber, deliveryAddress: detail.deliveryAddress,
        supervisionAddress: detail.supervisionAddress, projectName: detail.projectName,
        recipientPhone: detail.recipientPhone, paymentDueDate: detail.paymentDueDate,
        lines: detail.lines.map((l: Record<string, unknown>) =>
          l['setHead'] === true
            ? {
                lineId: l['id'], productId: UI_HIT.id, productName: '교체된 단품',
                modelName: UI_HIT.model, specification: null, quantity: 1,
                unitPrice: SWAP_PRICE, note: null,
              }
            : {
                lineId: l['id'], productId: l['productId'], productName: l['productName'],
                modelName: l['modelName'], specification: l['specification'],
                quantity: l['quantity'], unitPrice: String(l['unitPrice']), note: l['note'],
              },
        ),
      },
    })
    expect(res.status(), 'R8-QA-6: 품목 교체 PUT 이 200 으로 통과(계약 표면)').toBe(200)

    await page.reload()
    await page.getByTestId('sales-slip-edit-button').waitFor({ state: 'visible', timeout: 30000 })
    // R8 리뷰 시점엔 '…-single-marked-as-set-head'(오귀속 발생)였다 — fix 후엔 승계되지 않는다.
    await capture(page, '19-r8-qa-6-after-swap-no-lineage-inherited')

    // 🔴 불변식 1 — 교체된 단품은 세트 계보를 물려받아선 안 된다.
    expect(
      psql(`SELECT count(*) FROM slip_lines WHERE slip_id='${slipId}' AND is_deleted=false
              AND product_id='${UI_HIT.id}' AND parent_set_model IS NOT NULL`),
      `R8-QA-6: 교체된 단품 ${UI_HIT.model} 이 옛 라인의 세트 계보를 이식받음 (계보=${lineageOf(slipId)})`,
    ).toBe('0')

    // 🔴 불변식 2 — 사용자가 입력한 단가는 기억돼야 한다.
    expect(
      memoryOf(UI_HIT.id),
      'R8-QA-6: 교체 품목이 구성품으로 오귀속돼 사용자 입력 단가(150000) 기억이 증발함',
    ).toBe(expectedMemory)
    await ctx.close()
  })

  /**
   * R8-QA-4 [4순위·매 라운드 확인] — R3 fix 신규 UI 가 실 GUI 에 살아 있는지 라이브 실증.
   *  - hit 라인 마커 = '거래처 최근단가' (구 '최근가' 아님)
   *  - miss 라인 마커 = '판매가' (D-R4-1 로 '정가'→'판매가' 확정)
   *  - 단건 lookup 은 GET /slips/price-memory (bulk 아님)
   * 기존 스펙 01/02 가 같은 계약을 덮지만 합성 시드 소멸로 실행 불가 상태라 실품목으로 재확인한다.
   */
  test('R8-QA-4 [확인] 전표 폼 — hit=거래처 최근단가 마커 · miss=판매가 마커 · 단건 GET 경로', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)
    const calls: string[] = []
    page.on('request', (r) => {
      if (r.url().includes('/slips/price-memory')) calls.push(`${r.method()} ${r.url()}`)
    })

    // 알려진 기억단가를 심어 hit 를 결정적으로 만든다(실 서버 저장 경로가 만든 값과 동일 형식).
    resetMemoryPairs([UI_HIT.id, UI_MISS.id])
    psql(
      `INSERT INTO partner_product_price_memory (id, partner_id, product_id, unit_price, source,
         remembered_at, created_at, created_by, is_deleted)
       VALUES (gen_random_uuid(), '${PARTNER.id}', '${UI_HIT.id}', 913000, 'LINE_SAVE',
         TIMESTAMP '2026-01-02 03:04:05', CURRENT_TIMESTAMP, 'qa-r8', FALSE)`,
    )

    await page.goto(`${BASE_URL}/sales/new`)
    await expect(page.getByRole('combobox', { name: '거래처' })).toBeVisible({ timeout: 30000 })
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER.name)
    await pickWarehouse(page)
    await capture(page, '13-r8-qa-4-slip-form-partner-selected')

    // hit — 기억단가 913000 자동채움 + '거래처 최근단가' 마커.
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', UI_HIT.model)
    await page.waitForTimeout(1500)
    await expect(page.getByLabel('라인 1 단가'), 'hit 기억단가 자동채움 실패').toHaveValue(/913,?000/)
    await expect(
      page.getByRole('note', { name: '이 거래처에 마지막으로 저장된 단가' }).first(),
    ).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('거래처 최근단가').first(), "hit 마커 문구가 '거래처 최근단가' 가 아님").toBeVisible()
    await capture(page, '14-r8-qa-4-hit-marker-거래처최근단가-913000')

    // 단건 GET 경로 확인 — bulk 아님.
    expect(
      calls.filter((c) => c.startsWith('GET ') && c.includes('/slips/price-memory?')).length,
      '단건 hit 시 GET /slips/price-memory 미관측',
    ).toBeGreaterThan(0)
    expect(
      calls.filter((c) => c.includes('/slips/price-memory/bulk')).length,
      '단건 hit 시나리오에서 bulk 호출 발생(경로 오배선)',
    ).toBe(0)

    // miss — 기억 없는 품목은 '판매가' 마커('정가' 아님, D-R4-1). 폼은 1라인으로 시작 → 라인 추가.
    await page.getByRole('button', { name: '+ 라인 추가' }).click()
    await page.waitForTimeout(400)
    await pickAutocomplete(page, '라인 2 품목', '품목 목록', UI_MISS.model)
    await page.waitForTimeout(1500)
    await expect(
      page.getByText('판매가', { exact: true }).first(),
      "miss 마커 문구가 '판매가' 가 아님(D-R4-1 회귀)",
    ).toBeVisible({ timeout: 10000 })
    expect(await page.getByText('정가', { exact: true }).count(), "구 문구 '정가' 잔존(D-R4-1 위반)").toBe(0)
    await capture(page, '15-r8-qa-4-miss-marker-판매가')
    console.log('[R8-QA-4] price-memory 호출:', JSON.stringify(calls))
    await ctx.close()
  })

  /**
   * R8-QA-5 [4순위·매 라운드 확인] — 거래처 변경 시 재조회 계약.
   *  - POST /slips/price-memory/bulk **정확히 1건** (품목수만큼 단건 GET 아님, D-R3-4)
   *  - 배너 고지 표시 (D-R3-2)
   *  - 값이 바뀐 행만 '단가 변경' 강조 (D-R3-2)
   */
  test('R8-QA-5 [확인] 거래처 변경 → bulk 정확히 1건 · 배너 · 변경행만 강조', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    await login(page)

    // 거래처A 에는 기억 있음 / 거래처B(변경 대상)에는 다른 기억 → 변경 시 값이 바뀌어야 강조된다.
    resetMemoryPairs([UI_HIT.id])
    psql(
      `INSERT INTO partner_product_price_memory (id, partner_id, product_id, unit_price, source,
         remembered_at, created_at, created_by, is_deleted)
       VALUES (gen_random_uuid(), '${PARTNER.id}', '${UI_HIT.id}', 913000, 'LINE_SAVE',
         TIMESTAMP '2026-01-02 03:04:05', CURRENT_TIMESTAMP, 'qa-r8', FALSE)`,
    )

    await page.goto(`${BASE_URL}/sales/new`)
    await expect(page.getByRole('combobox', { name: '거래처' })).toBeVisible({ timeout: 30000 })
    await pickAutocomplete(page, '거래처', '거래처 목록', PARTNER.name)
    await pickWarehouse(page)
    await pickAutocomplete(page, '라인 1 품목', '품목 목록', UI_HIT.model)
    await page.waitForTimeout(1200)
    await expect(page.getByLabel('라인 1 단가')).toHaveValue(/913,?000/)
    await capture(page, '16-r8-qa-5-before-partner-change-913000')

    // 거래처 변경 창구간의 price-memory 호출만 센다.
    const during: string[] = []
    page.on('request', (r) => {
      if (r.url().includes('/slips/price-memory')) during.push(`${r.method()} ${r.url()}`)
    })
    await pickAutocomplete(page, '거래처', '거래처 목록', '(B.E.S.T)에어컨')
    await page.waitForTimeout(2500)
    await capture(page, '17-r8-qa-5-after-partner-change-banner-and-highlight')

    const bulk = during.filter((c) => c.includes('/slips/price-memory/bulk'))
    const singles = during.filter((c) => c.startsWith('GET ') && c.includes('/slips/price-memory?'))
    console.log('[R8-QA-5] 거래처 변경 창구간 호출:', JSON.stringify(during))
    expect(bulk.length, '거래처 변경 시 bulk 가 정확히 1건이 아님(D-R3-4)').toBe(1)
    expect(singles.length, '거래처 변경 시 품목수만큼 단건 GET 발생(D-R3-4 위반)').toBe(0)

    // 배너 고지 — 단일 live region.
    await expect(page.getByTestId('slip-price-refresh-banner'), '거래처 변경 배너 미표시(D-R3-2)')
      .toBeVisible({ timeout: 10000 })
    const bannerText = (await page.getByTestId('slip-price-refresh-banner').textContent()) ?? ''
    console.log('[R8-QA-5] 배너:', bannerText)
    expect(bannerText.trim().length, '거래처 변경 배너가 빈 텍스트').toBeGreaterThan(0)

    // 변경행 강조 — '단가 변경' 인디케이터가 값이 바뀐 1행에만.
    expect(await page.getByText('단가 변경', { exact: true }).count(), "변경행 '단가 변경' 강조가 1행이 아님(D-R3-2)").toBe(1)
    await ctx.close()
  })

  /**
   * R8-QA-3 [HIGH·fix 가드] — D-R8-7 이행 검증. 전표 수정의 거래처가 **자유입력 → PartnerAutocomplete**
   * 로 봉쇄됐고, 거래처를 바꿔 저장하면 가격기억이 **바뀐 거래처**에 각인돼야 한다.
   *
   * R8 리뷰 시점의 결함: `Slip.updateSalesHeader` 가 partnerName·partnerCode·businessNumber 만
   * 갱신하고 **partnerId 는 파라미터에 아예 없었고**, `collectPriceMemory` 가 헤더 갱신 **이전에**
   * 호출돼 갱신 전 `slip.getPartnerId()` 를 읽었다. 화면 거래처는 B 로 바뀌는데 기억은 A 에
   * 각인 — 마커가 거짓말을 했다(라이브 실증: `R8검증-다른거래처`/277000 → 304700 이 원 거래처
   * `44f0cfc1` 에 각인).
   *
   * D-R8-7 fix 3종을 **각각** 단언한다:
   *  1. 자유입력 봉쇄 — '거래처' 가 combobox(PartnerAutocomplete) 이고, 미선택 자유 타이핑은
   *     partnerName 을 바꾸지 못한다
   *  2. 계약에 partnerId 추가 — 실제 선택 시 `slips.partner_id` 가 갱신된다
   *  3. `collectPriceMemory` 를 헤더 갱신 이후로 이동 — 기억이 **새** 거래처에 각인된다
   */
  test('R8-QA-3 [HIGH·fix 가드] 전표 수정 거래처 = PartnerAutocomplete · 자유입력 봉쇄 · 거래처 변경 시 기억이 새 거래처에 각인', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const auth = await login(page)
    resetMemoryPairs([COMP_HEAD.id, COMP_TAIL.id, SINGLE.id])
    resetMemoryPairsFor(OTHER_PARTNER.id, [SINGLE.id])
    const slipId = await createBundlePlusSingleSlip(page, auth)
    const FREE_TEXT = 'R8검증-존재하지않는거래처'
    const NEW_PRICE = '277000'
    const expectedMemory = '304700.00/LINE_SAVE' // 277000 × 1.1

    await openSalesEdit(page, slipId)
    await capture(page, '10-r8-qa-3-sales-edit-partner-is-autocomplete')

    // 🔴 fix 가드 1 — 거래처가 combobox(PartnerAutocomplete) 다. 종전 자유입력
    //    CollaborativeSlipInput 이라면 role=combobox 로 잡히지 않는다.
    await expect(
      page.getByRole('combobox', { name: '거래처' }),
      'R8-QA-3 fix 가드: 전표 수정 거래처가 PartnerAutocomplete(combobox) 가 아님 — D-R8-7 미이행',
    ).toBeVisible({ timeout: 10000 })

    // 🔴 fix 가드 1-b — 후보를 고르지 않은 자유 타이핑은 거래처를 바꾸지 못한다.
    //    (종전엔 이 타이핑만으로 partner_name 이 바뀌고 partner_id 는 남아 기억이 오각인됐다.)
    await page.getByRole('combobox', { name: '거래처' }).fill(FREE_TEXT)
    await page.waitForTimeout(500)
    await page.keyboard.press('Escape')
    await capture(page, '11-r8-qa-3-free-text-typed-not-committed')

    // 실제 거래처 변경 — 자동완성 후보를 골라 확정한다.
    await pickAutocomplete(page, '거래처', '거래처 목록', OTHER_PARTNER.name)
    await page.getByLabel('단가(VAT제외) 3').fill(NEW_PRICE)
    await page.waitForTimeout(600)
    await capture(page, '12-r8-qa-3-partner-switched-via-autocomplete-and-price')

    const putRes = page.waitForResponse(
      (r) => r.request().method() === 'PUT' && r.url().includes(`/slips/${slipId}/sales`),
      { timeout: 30000 },
    )
    await page.getByRole('button', { name: '저장', exact: true }).first().click()
    expect((await putRes).status(), '거래처 변경 저장 PUT').toBe(200)
    await page.waitForTimeout(2500)

    await page.goto(`${BASE_URL}/sales/${slipId}`)
    await expect(page.getByText(OTHER_PARTNER.name).first()).toBeVisible({ timeout: 30000 })
    await capture(page, '13-r8-qa-3-detail-shows-new-partner')

    // 🔴 fix 가드 2 — partner_name 과 partner_id 가 **함께** 새 거래처로 이동해야 한다.
    //    R8 리뷰 시점엔 name 만 바뀌고 id 는 원 거래처로 고정이었다.
    expect(
      psql(`SELECT partner_name || '/' || coalesce(partner_id::text,'-') FROM slips WHERE id='${slipId}'`),
      'R8-QA-3 fix 가드: 거래처 변경이 partner_id 에 반영되지 않음(계약 partnerId 누락)',
    ).toBe(`${OTHER_PARTNER.name}/${OTHER_PARTNER.id}`)

    // 🔴 fix 가드 3 — 기억은 "그 전표에 표시된 거래처" = **새** 거래처에 각인돼야 한다.
    expect(
      memoryOfFor(OTHER_PARTNER.id, SINGLE.id),
      `R8-QA-3 fix 가드: 단가 ${NEW_PRICE} 을 '${OTHER_PARTNER.name}' 로 저장했는데 새 거래처에 기억이 없음`,
    ).toBe(expectedMemory)

    // 🔴 그리고 원 거래처는 그 단가를 가져가면 안 된다(R8 리뷰가 실증한 오각인의 역단언).
    expect(
      memoryOf(SINGLE.id),
      `R8-QA-3 fix 가드: 기억이 원 거래처(${PARTNER.id})에 각인됨 — 마커가 거짓말(R8-QA-3 회귀)`,
    ).not.toBe(expectedMemory)
    await ctx.close()
  })

  /**
   * 🆕 R8-QA-9 [HIGH·신규 결함] — D-R8-7 이 심은 회귀. 전표 수정 모달을 열면 거래처가
   * **항상 빈 칸**으로 보인다. 전표에는 거래처가 멀쩡히 있는데도.
   *
   * **메커니즘(전 단계 라이브 실측으로 확정)**:
   *  1. `SlipDetailPage:511-522` 매출 인라인 편집 진입 effect 가
   *     `input:not([readonly]):not([disabled])` **첫 요소에 focus** 한다. 주석이 명시하듯
   *     readonly(판매번호)를 건너뛰므로 **첫 편집가능 필드 = 거래처 PartnerAutocomplete** 다.
   *     D-R8-7 이전엔 이 자리가 자유입력 `CollaborativeSlipInput`(`value={salesPartnerName}`)
   *     이라 포커스돼도 값이 그대로 보였다.
   *  2. focus → `AsyncAutocomplete.handleFocus` → `setDraft('')` + **`setOpen(true)`**
   *     (의도된 동작 — "열릴 때 draft 초기화 → 즉시 후보 표시").
   *  3. 곧이어 coedit effect 가 `setSlipFormCoeditPending(true)` → 거래처 input 이
   *     **`disabled={slipFormCoeditPending}`** 로 비활성 → 브라우저가 포커스를 떼지만
   *     **React 는 disabled 요소에 onBlur 를 발화하지 않는다** → `handleBlur` 미실행 →
   *     **`open` 이 true 로 고착**되고 `draft` 는 '' 로 남는다.
   *  4. `displayValue = open ? draft : selectedLabel` → **영구히 ''**.
   *     provider 로드 후 input 이 다시 활성화돼도 `open` 은 여전히 true 다.
   *
   * **실측 증거**: 모달 진입 직후 `거래처` 표시값 `""` · `aria-expanded="true"` · **포커스 없음** ·
   * 타 필드 클릭해도 `""` 유지(포커스가 없으니 blur 자체가 안 남) · 그러나 **직접 클릭 후 Escape
   * → `"한울냉열시스템"` 복원** = 값은 처음부터 state 에 있었고 **표시만** 깨졌다.
   * 저장 payload 는 정상(`partnerName`·`partnerId` 실림) → 데이터 파괴는 아니다.
   *
   * **왜 HIGH 인가**: (a) 거래처코드·사업자번호는 채워져 있는데 상호만 비어 **화면이 자기모순** —
   * 사용자는 거래처가 날아간 걸로 읽는다. (b) 그 오해의 자연스러운 대응이 **거래처 재선택**인데,
   * D-R8-7 이후 거래처 선택은 `partner_id` 갱신 + CRDT 전파 + **가격기억 재각인**을 유발하는
   * 실제 데이터 행위다 — 표시 버그가 사용자를 불필요한 쓰기로 민다. (c) `aria-expanded="true"`
   * 인데 popup 도 포커스도 없어 WAI-ARIA combobox 패턴 위반(스크린리더가 '확장됨·빈 값'으로 낭독).
   *
   * **왜 749→763 vitest 가 놓쳤나**: `SlipDetailPage` 는 **렌더 테스트 0건**이다. FE 배치가
   * 스스로 *"교체한 PartnerAutocomplete 경로는 typecheck + 순수함수 테스트로만 검증됨. 라이브 QA
   * 필수"* 라고 정직 고지했고, 이 결함이 정확히 그 구멍에서 나왔다.
   */
  test('🆕 R8-QA-9 [HIGH·신규] 전표 수정 모달 진입 시 거래처가 빈 칸으로 표시 — 값은 state 에 있으나 open 고착으로 표시 소실', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const auth = await login(page)
    const slipId = await createBundlePlusSingleSlip(page, auth)

    // 전제 — 서버 상세는 거래처를 정상 보유한다.
    const detail = (await (await page.request.get(`${API_BASE}/slips/${slipId}`, { headers: authHeaders(auth) })).json()).data
    expect(detail.partnerName, 'R8-QA-9 전제: 전표에 거래처가 있어야 함').toBe(PARTNER.name)

    await openSalesEdit(page, slipId)
    const combo = page.getByRole('combobox', { name: '거래처' })
    await capture(page, '20-r8-qa-9-edit-modal-partner-renders-empty')

    // 보조 필드는 채워져 있다 — 화면 자기모순의 증거.
    expect(await page.getByLabel('거래처코드').inputValue(), 'R8-QA-9 전제: 거래처코드는 채워져 있음').not.toBe('')
    expect(await page.getByLabel('사업자번호').inputValue(), 'R8-QA-9 전제: 사업자번호는 채워져 있음').not.toBe('')

    // 🔴 결함 1 — 상호만 빈 칸이다.
    expect(
      await combo.inputValue(),
      'R8-QA-9: 전표 수정 모달의 거래처가 빈 칸 — 거래처코드/사업자번호는 채워져 있는데 상호만 소실(표시 회귀)',
    ).toBe(PARTNER.name)

    // 🔴 결함 2 — 포커스가 없는데 aria-expanded=true (WAI-ARIA combobox 위반 · open 고착의 직접 증거).
    expect(
      await combo.evaluate((el) => el === document.activeElement),
      'R8-QA-9 전제: 거래처 input 은 포커스를 갖고 있지 않음',
    ).toBe(false)
    expect(
      await combo.getAttribute('aria-expanded'),
      'R8-QA-9: 포커스도 popup 도 없는데 aria-expanded=true — open 고착(WAI-ARIA combobox 패턴 위반)',
    ).toBe('false')

    await ctx.close()
  })

  /**
   * 🆕 R8-QA-10 [2순위 확인] — D-R8-7 이 신설한 **CRDT 헤더 partnerId 전파** 실증 + FE 배치가
   * 정직 고지한 **배너 미전파 갭** 실측.
   *
   * FE 배치 주장: *"CRDT header partnerId 편입은 회피 불가. 전표 수정 폼은 coedit 항상 활성이라
   * applyProviderState 가 doc 변경마다 헤더를 되읽어 전파 없이는 로컬 선택이 즉시 구값으로 복귀"*.
   * → 창A 가 거래처를 바꾸면 창B 에 **전파**돼야 한다(안 그러면 B 가 구 partnerId 로 저장).
   *
   * 동시에 FE 배치는 갭을 고지했다: *"priceRefreshChanged(변경행 강조/배너)는 로컬 state 라 CRDT
   * 미전파 → 원격 피어가 배너 미수신"*. 이 테스트는 그 갭이 **실제로 그런지** 실측해 박제한다
   * (fix 대상 아님 · R9 판단 자료).
   */
  test('🆕 R8-QA-10 [확인] 2창 coedit — 거래처 변경이 원격 피어에 CRDT 전파 · 배너는 미전파(고지된 갭 실측)', async ({ browser }) => {
    const ctxA = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const ctxB = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const pageA = await ctxA.newPage()
    const pageB = await ctxB.newPage()
    const auth = await login(pageA)
    await login(pageB)
    const slipId = await createBundlePlusSingleSlip(pageA, auth)

    await openSalesEdit(pageA, slipId)
    await openSalesEdit(pageB, slipId)
    await capture(pageB, '21-r8-qa-10-windowB-before-remote-partner-change')

    // 창A 가 거래처를 변경 — CRDT 헤더 4필드 원자 전파(handleSlipPartnerSelect).
    await pickAutocomplete(pageA, '거래처', '거래처 목록', OTHER_PARTNER.name)
    await pageA.waitForTimeout(2000)
    await capture(pageA, '22-r8-qa-10-windowA-partner-changed')

    // 🔴 전파 확인 — B 의 거래처코드/사업자번호가 새 거래처 것으로 바뀌어야 한다.
    //    (상호 표시는 R8-QA-9 의 open 고착 때문에 신뢰할 수 없어 보조필드로 판정한다 —
    //     이 우회 자체가 R8-QA-9 가 실사용 관측을 어떻게 가리는지 보여준다.)
    const bizA = await pageA.getByLabel('사업자번호').inputValue()
    await expect(
      pageB.getByLabel('사업자번호'),
      'R8-QA-10: 창A 의 거래처 변경이 창B 에 CRDT 전파되지 않음 — B 는 구 partnerId 로 저장하게 된다',
    ).toHaveValue(bizA, { timeout: 20000 })
    await capture(pageB, '23-r8-qa-10-windowB-received-remote-partner-change')

    // 📋 고지된 갭 실측 — 배너/변경행 강조는 로컬 state 라 B 에 전파되지 않는다.
    const bannerA = await pageA.getByTestId('slip-price-refresh-banner').count()
    const bannerB = await pageB.getByTestId('slip-price-refresh-banner').count()
    console.log(`[R8-QA-10] 배너 — 창A(변경 주체) count=${bannerA} / 창B(원격 피어) count=${bannerB}`)
    const bannerTextA = bannerA > 0 ? (await pageA.getByTestId('slip-price-refresh-banner').textContent()) ?? '' : ''
    const bannerTextB = bannerB > 0 ? (await pageB.getByTestId('slip-price-refresh-banner').textContent()) ?? '' : ''
    console.log(`[R8-QA-10] 배너 텍스트 — A=${JSON.stringify(bannerTextA.trim())} / B=${JSON.stringify(bannerTextB.trim())}`)
    console.log(`[R8-QA-10] '단가 변경' 강조 — A=${await pageA.getByText('단가 변경', { exact: true }).count()} / B=${await pageB.getByText('단가 변경', { exact: true }).count()}`)

    await ctxA.close()
    await ctxB.close()
  })

  /**
   * 🆕 R8-QA-11 [HIGH·신규 결함] — D-R8-7 이 **새로운 교차 거래처 기억 오염 경로**를 열었다.
   * 전표 수정에서 거래처만 바꾸면 **옛 거래처의 협상단가가 새 거래처의 '최근단가'로 각인**된다.
   *
   * **왜 새 결함인가 — D-R8-7 이 만든 것이다**:
   *  - D-R8-7 **이전**: 거래처를 바꿔도 `partner_id` 가 안 움직였다 → 기억이 **원** 거래처에
   *    남았다(= R8-QA-3 이 적발한 결함).
   *  - D-R8-7 **이후**: `partner_id` 가 실제로 움직이고 `collectPriceMemory` 도 헤더 갱신
   *    **이후**로 옮겨졌다 → 기억이 **새** 거래처로 간다. 그런데 **라인 단가는 옛 거래처 값 그대로다.**
   *    → 거래처 A 와 협상한 단가가 거래처 B 의 '거래처 최근단가' 로 둔갑한다.
   *
   * **근본 원인 — 폼/수정모달 비대칭**(이 PR 이 8라운드째 반복 적발한 그 패턴):
   *  - `SlipFormPage`(신규 전표)는 거래처 변경 시 **bulk 재조회 + 배너 + 변경행 강조**를 한다
   *    (D-R3-2 · D-R3-4). grep: `priceRefresh|bulk|priceMemory` = **31건**.
   *  - `SlipDetailPage`(수정 모달)는 **가격 재조회 로직이 0건**이다. 같은 grep = **0건**.
   *    `handleSlipPartnerSelect` 는 헤더 4필드 설정 + CRDT 전파만 하고 재조회를 하지 않는다.
   *  - 실측(R8-QA-10): 거래처를 바꾼 창A 조차 배너 count=0 — 수정 모달엔 배너가 **애초에 없다**.
   *
   * **피해 시나리오**: 거래처 A 와 913,000 에 합의해 전표를 만든다 → 담당자가 "거래처를 잘못
   * 골랐다" 며 수정에서 B 로 바꾼다 → 단가는 913,000 그대로 → 저장 → **B 의 최근단가 = 913,000**
   * 으로 각인 → 다음에 B 전표를 쓰면 협상한 적 없는 913,000 이 자동채움된다. #809 가 막으려던
   * 오염 그 자체이며, 마커('거래처 최근단가')가 거짓말을 한다.
   */
  test('🆕 R8-QA-11 [HIGH·신규] 수정 모달에서 거래처만 변경 → 옛 거래처 단가가 새 거래처 최근단가로 각인(재조회 부재)', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const auth = await login(page)
    resetMemoryPairs([UI_HIT.id])
    resetMemoryPairsFor(OTHER_PARTNER.id, [UI_HIT.id])

    // 거래처 A 와 협상한 단가 — 이 값은 A 에만 유효하다.
    const NEGOTIATED_FOR_A = 913000
    const polluted = '1004300.00/LINE_SAVE' // 913000 × 1.1 — B 에 각인되면 안 되는 값

    const created = await page.request.post(`${API_BASE}/slips`, {
      headers: authHeaders(auth),
      data: {
        slipType: 'OUTBOUND', partnerId: PARTNER.id, partnerName: PARTNER.name,
        sourceWarehouseId: WAREHOUSE,
        lines: [{ productId: UI_HIT.id, quantity: 1, unitPrice: NEGOTIATED_FOR_A }],
      },
    })
    expect(created.ok(), 'R8-QA-11 전제: 전표 생성').toBeTruthy()
    const slipId = (await created.json()).data.id as string
    expect(memoryOf(UI_HIT.id), 'R8-QA-11 전제: A 의 협상단가가 A 에 기억됨').toBe(polluted)
    expect(memoryOfFor(OTHER_PARTNER.id, UI_HIT.id), 'R8-QA-11 전제: B 에는 기억 없음').toBe('NONE')

    await openSalesEdit(page, slipId)
    await capture(page, '24-r8-qa-11-edit-modal-partnerA-price-913000')

    // 거래처만 B 로 변경 — 단가는 **손대지 않는다**.
    await pickAutocomplete(page, '거래처', '거래처 목록', OTHER_PARTNER.name)
    await page.waitForTimeout(2500)
    await capture(page, '25-r8-qa-11-partner-switched-to-B-price-still-913000-no-banner')

    // 관측 — 재조회/고지 부재(판정은 말미에). SlipFormPage 는 같은 조작에 bulk+배너+강조를 한다.
    const bannerCount = await page.getByTestId('slip-price-refresh-banner').count()
    const priceAfterSwitch = await page.getByLabel('단가(VAT제외) 1').inputValue()
    console.log(`[R8-QA-11] 거래처 변경 후 — 배너 count=${bannerCount} · 라인1 단가=${priceAfterSwitch}`)

    const putRes = page.waitForResponse(
      (r) => r.request().method() === 'PUT' && r.url().includes(`/slips/${slipId}/sales`),
      { timeout: 30000 },
    )
    await page.getByRole('button', { name: '저장', exact: true }).first().click()
    expect((await putRes).status(), 'R8-QA-11: 거래처 변경 저장 PUT').toBe(200)
    await page.waitForTimeout(2500)
    await capture(page, '26-r8-qa-11-saved-partnerB')
    console.log(`[R8-QA-11] 저장 후 기억 — A=${memoryOf(UI_HIT.id)} · B=${memoryOfFor(OTHER_PARTNER.id, UI_HIT.id)}`)

    // 🔴 결함 1 (본질) — A 와 협상한 913,000 이 B 의 '최근단가' 로 각인돼선 안 된다.
    expect(
      memoryOfFor(OTHER_PARTNER.id, UI_HIT.id),
      `R8-QA-11: 거래처 A(${PARTNER.name})와 협상한 단가 ${NEGOTIATED_FOR_A} 이 거래처 B(${OTHER_PARTNER.name})의 최근단가로 각인됨 — 교차 거래처 기억 오염`,
    ).not.toBe(polluted)

    // 🔴 결함 2 (원인) — 거래처가 바뀌었는데 단가 재조회도 고지도 없다(SlipFormPage 는 둘 다 한다).
    expect(
      bannerCount,
      'R8-QA-11: 수정 모달에서 거래처를 바꿨는데 재적용 배너가 없음 — 사용자는 옛 거래처 단가가 남아있는 줄 모른다(폼/수정모달 비대칭)',
    ).toBeGreaterThan(0)

    await ctx.close()
  })
})
