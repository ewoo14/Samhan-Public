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
// r2/·r4/·r4-postfix/·r5/·r5-postfix/·r6/·r6-postfix/ 는 이력 보존 — 불가침. R8 은 r8/ 신규.
const SHOTS = path.resolve(_dirname, '../../../../docs/qa/809-partner-product-price-memory/r8')
fs.mkdirSync(SHOTS, { recursive: true })

const PARTNER = { id: '44f0cfc1-4a5f-4206-85cd-04ad5fa70922', name: '한울냉열시스템' }
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
  return psql(
    `SELECT coalesce((SELECT unit_price || '/' || source FROM partner_product_price_memory
       WHERE partner_id='${PARTNER.id}' AND product_id='${productId}' AND is_deleted=false), 'NONE')`,
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
  test('R8-QA-1 [BLOCKING] lineId 미전송 무수정 PUT → 세트 계보 전량 파괴 + 구성품 배분가 LINE_SAVE 각인', async ({ browser }) => {
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
    expect(res.status(), 'lineId 미전송 PUT 이 거부되지 않고 200 으로 통과한다(계약 표면)').toBe(200)

    await page.reload()
    await expect(page.getByText(SINGLE.model).first()).toBeVisible({ timeout: 30000 })
    await capture(page, '02-r8-qa-1-after-nolineid-put-lineage-destroyed')

    // 🔴 계보 전량 파괴 — set_head 전부 f, parent_set_model 전부 NULL.
    expect(lineageOf(slipId), 'R8-QA-1: 무수정 PUT 인데 세트 계보가 보존되지 않음(데이터 손실)').toBe(
      `${COMP_HEAD.model}:true:${BUNDLE.model}|${COMP_TAIL.model}:false:${BUNDLE.model}|${SINGLE.model}:false:-`,
    )
    // 🔴 구성품 배분가 각인 — 계보를 잃어 isBundleComponent 필터를 통과해버린다.
    expect(memoryOf(COMP_HEAD.id), 'R8-QA-1: head 구성품 배분가가 LINE_SAVE 로 각인됨(기억 오염)').toBe('NONE')
    expect(memoryOf(COMP_TAIL.id), 'R8-QA-1: 구성품 배분가가 LINE_SAVE 로 각인됨(기억 오염)').toBe('NONE')
    await ctx.close()
  })

  /**
   * R8-QA-2 [BLOCKING] — 전표 coedit 원격 라인삭제 → lineId 위치 오정렬 → 계보 오귀속 + 기억 증발.
   *
   * `SlipDetailPage.coeditLinesToEditLines` 는 Y.Doc 행에서 셀 값을 읽으면서 **lineId 만은
   * 로컬 배열 `current[index]` 에서 위치로 집는다**:
   *     const previous = current[index]
   *     lineId: previous?.lineId ?? null,
   *     productId: provider.getItemValue(index, 'productId') || previous?.productId || ''
   * 원격 피어가 행을 지우면 `removeSalesLine` → `provider.replaceItems(next)` 로 Y.Array 가
   * 통째 교체돼 인덱스가 밀리는데, 수신 창의 `current` 는 아직 옛 길이다. 따라서 삭제 지점
   * 이후 모든 행이 **"내용은 다음 행, lineId 는 이전 행"** 으로 어긋난 채 PUT 된다.
   * 정작 Y.Doc 행에는 서버 lineId 가 `lineId` 필드로 실려 있다(`replaceItems` 가 보존) —
   * 권위값이 바로 옆에 있는데 쓰지 않는다.
   *
   * 서버는 이를 막지 못한다: `validateLineIds` 는 "이 전표 소유 + 중복 없음" 만 보므로
   * 밀린 lineId 도 전부 합법이고, 원격 삭제는 Y.Doc 전용이라 `slip.modifiedAt` 이 안 변해
   * `verifyVersion` 낙관적 잠금도 발화하지 않는다.
   *
   * 피해: 세트와 무관한 **단품이 세트 구성품으로 각인**되고, 그 결과 `collectPriceMemory` 의
   * isBundleComponent 필터에 걸려 **사용자가 방금 입력한 단가의 기억이 조용히 증발**한다.
   */
  test('R8-QA-2 [BLOCKING] coedit 원격 1행 삭제 → 수신창 저장 시 lineId 밀림 → 단품이 세트 구성품으로 오귀속 + 단품 기억 증발', async ({ browser }) => {
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
    expect(memoryOf(SINGLE.id), 'R8-QA-2 전제: 생성 시 단품은 판매가 기준으로 기억됨').toBe('367840.00/LINE_SAVE')

    // 두 창 모두 상세 → 수정(coedit) 진입.
    await openSalesEdit(pageA, slipId)
    await capture(pageA, '03-r8-qa-2-windowA-coedit-3lines')
    await openSalesEdit(pageB, slipId)
    await capture(pageB, '04-r8-qa-2-windowB-coedit-3lines')

    // 창B: 단품(3행) 단가를 사용자가 직접 입력 — 이 기억이 살아남아야 정상.
    await pageB.getByLabel('단가(VAT제외) 3').fill(NEW_SINGLE_PRICE)
    await pageB.waitForTimeout(600)
    await capture(pageB, '05-r8-qa-2-windowB-single-price-entered-299000')

    // 창A: 1행(세트 head) 삭제 — Y.Doc 전용 변경(서버 미호출 → updatedAt 불변 → 409 없음).
    await pageA.getByRole('button', { name: '1번 행 삭제' }).click()
    await pageA.waitForTimeout(1200)
    await capture(pageA, '06-r8-qa-2-windowA-deleted-row1-set-head')

    // 창B: 원격 삭제 수신 대기(POST debounce 150ms + SSE, 최악 resync 5s).
    await expect(pageB.getByLabel('단가(VAT제외) 3')).toHaveCount(0, { timeout: 20000 })
    await pageB.waitForTimeout(1500)
    await capture(pageB, '07-r8-qa-2-windowB-received-remote-delete-2lines')

    // 창B 저장 — 밀린 lineId 로 PUT.
    const putBody: string[] = []
    pageB.on('request', (r) => {
      if (r.method() === 'PUT' && r.url().includes(`/slips/${slipId}/sales`)) putBody.push(r.postData() ?? '')
    })
    const putRes = pageB.waitForResponse(
      (r) => r.request().method() === 'PUT' && r.url().includes(`/slips/${slipId}/sales`),
      { timeout: 30000 },
    )
    await pageB.getByRole('button', { name: '저장', exact: true }).first().click()
    const resp = await putRes
    expect(resp.status(), 'R8-QA-2: 밀린 lineId PUT 이 서버에 거부되지 않는다(낙관적잠금·validateLineIds 모두 미발화)').toBe(200)
    console.log('[R8-QA-2] 창B PUT body:', putBody.join('\n'))
    await pageB.waitForTimeout(2500)
    await capture(pageB, '08-r8-qa-2-windowB-saved')

    await pageB.reload()
    // 잔존 라인 조합이 실행마다 달라 특정 모델명으로 기다리면 불안정 — 상세 렌더 완료 신호로 기다린다.
    await pageB.getByTestId('sales-slip-edit-button').waitFor({ state: 'visible', timeout: 30000 })
    await capture(pageB, '09-r8-qa-2-final-detail-after-corrupted-save')

    // 판정은 "정확한 잔존 라인 조합"이 아니라 **불변식**으로 한다. 원격삭제/동기화 타이밍에 따라
    // 어느 행이 남는지는 라운드마다 달라지지만(R8 실측: 실행마다 잔존 조합 상이 — 그 자체가 경합의
    // 방증), 아래 두 불변식은 어떤 순서로도 깨져선 안 된다.
    const finalLineage = lineageOf(slipId)
    console.log('[R8-QA-2] 저장 후 계보:', finalLineage)

    // 🔴 불변식 1 — 세트와 무관한 단품은 어떤 경우에도 세트 구성품이 될 수 없다.
    expect(
      psql(`SELECT count(*) FROM slip_lines WHERE slip_id='${slipId}' AND is_deleted=false
              AND product_id='${SINGLE.id}' AND parent_set_model IS NOT NULL`),
      `R8-QA-2: 단품 ${SINGLE.model} 이 세트 구성품으로 오귀속됨 (계보=${finalLineage})`,
    ).toBe('0')

    // 🔴 불변식 2 — head 행이 삭제된 뒤 남은 구성품이 head 지위를 훔쳐선 안 된다.
    expect(
      psql(`SELECT count(*) FROM slip_lines WHERE slip_id='${slipId}' AND is_deleted=false
              AND product_id='${COMP_TAIL.id}' AND set_head=true`),
      `R8-QA-2: 삭제된 head 의 setHead 가 잔존 구성품으로 이식됨 (계보=${finalLineage})`,
    ).toBe('0')

    // 🔴 불변식 3 — 단품 기억 증발. 사용자가 입력한 299000(×1.1=328900)이 기억돼야 한다.
    //    오귀속된 단품은 collectPriceMemory 의 isBundleComponent 필터에 걸려 갱신 자체가 누락된다
    //    (R8 실측: 생성시 값 367840 이 그대로 남음).
    expect(
      memoryOf(SINGLE.id),
      'R8-QA-2: 단품이 구성품으로 오귀속돼 사용자 입력 단가(299000) 기억이 증발함',
    ).toBe(expectedMemory)

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
    await capture(page, '19-r8-qa-6-after-swap-single-marked-as-set-head')

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
   * R8-QA-3 [HIGH] — 전표 수정에서 거래처를 바꿔 저장하면 가격기억이 **원래 거래처**에 귀속된다.
   *
   * `Slip.updateSalesHeader` / `Slip.updateHeader` 는 partnerName·partnerCode·businessNumber 만
   * 갱신하고 **partnerId 는 건드리지 않는다**(파라미터 목록에 아예 없음). 게다가
   * `collectPriceMemory(slip, ...)` 는 헤더 갱신 **이전에** 호출돼 `slip.getPartnerId()` 를 읽는다.
   * 즉 화면 거래처는 B 로 바뀌는데 기억은 A 에 각인된다 — 마커가 거짓말을 한다.
   */
  test('R8-QA-3 [HIGH] 전표 수정 GUI 에서 거래처 자유입력 변경 저장 → 화면은 새 거래처, 가격기억은 원 거래처에 귀속', async ({ browser }) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await ctx.newPage()
    const auth = await login(page)
    resetMemoryPairs([COMP_HEAD.id, COMP_TAIL.id, SINGLE.id])
    const slipId = await createBundlePlusSingleSlip(page, auth)
    const OTHER_PARTNER_NAME = 'R8검증-다른거래처'
    const NEW_PRICE = '277000'

    await openSalesEdit(page, slipId)
    await capture(page, '10-r8-qa-3-sales-edit-partner-is-free-text')

    // 전표 수정 모달의 '거래처' 는 PartnerAutocomplete 가 아니라 자유입력 CollaborativeSlipInput 이다
    // → 어떤 상호든 타이핑되며 partnerId 바인딩이 애초에 없다.
    await page.getByLabel('거래처', { exact: true }).fill(OTHER_PARTNER_NAME)
    await page.getByLabel('단가(VAT제외) 3').fill(NEW_PRICE)
    await page.waitForTimeout(600)
    await capture(page, '11-r8-qa-3-typed-other-partner-and-price')

    const putRes = page.waitForResponse(
      (r) => r.request().method() === 'PUT' && r.url().includes(`/slips/${slipId}/sales`),
      { timeout: 30000 },
    )
    await page.getByRole('button', { name: '저장', exact: true }).first().click()
    expect((await putRes).status(), '거래처 변경 저장 PUT').toBe(200)
    await page.waitForTimeout(2500)

    await page.goto(`${BASE_URL}/sales/${slipId}`)
    await expect(page.getByText(OTHER_PARTNER_NAME).first()).toBeVisible({ timeout: 30000 })
    await capture(page, '12-r8-qa-3-detail-shows-changed-partner-but-memory-elsewhere')

    // 화면 거래처는 바뀌었는데 partner_id 는 원 거래처로 고정 — 계약에 partnerId 자체가 없다.
    expect(
      psql(`SELECT partner_name || '/' || coalesce(partner_id::text,'-') FROM slips WHERE id='${slipId}'`),
      'R8-QA-3 전제: partner_name 만 바뀌고 partner_id 는 원 거래처로 고정',
    ).toBe(`${OTHER_PARTNER_NAME}/${PARTNER.id}`)

    // 🔴 불변식: 가격기억은 "그 전표에 표시된 거래처" 에 귀속돼야 한다.
    //    화면상 거래처는 이제 OTHER_PARTNER_NAME 이므로 원 거래처(PARTNER.id)가 새 단가
    //    277000×1.1=304700 을 가져가면 안 된다. 결함이 있는 한 이 단언은 RED.
    expect(
      memoryOf(SINGLE.id),
      `R8-QA-3: 단가 277000 을 '${OTHER_PARTNER_NAME}' 로 저장했는데 기억이 원 거래처(${PARTNER.id})에 각인됨 — 마커가 거짓말`,
    ).not.toBe('304700.00/LINE_SAVE')
    await ctx.close()
  })
})
