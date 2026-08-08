import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import * as path from 'node:path'
import { resolveQaCredential } from '../../../../scripts/lib/qa-credentials.cjs'
import { resolveQaShotsDir } from '../support/qa-screenshot-dir'

const APP_BASE = process.env['AUDIT_BASE_URL'] ?? 'http://127.0.0.1:5825'
const API_BASE = process.env['API_BASE'] ?? 'http://127.0.0.1:8080'
const PASSWORD = resolveQaCredential('QA_DEV_DEFAULT_PASSWORD')
const SHOTS = resolveQaShotsDir(path.resolve(
  process.cwd(),
  '../../docs/qa/825-s25-live-post-integrity-real-qa/screenshots',
))
const POST_PATH = '/api/v1/partner-orders/convert-to-slip-merge'
const MARKER = 'S25-825'

type JsonObject = Record<string, any>
type CandidatePair = {
  partnerCode: string
  orderNumbers: [string, string]
}

async function envelopeJson(response: Awaited<ReturnType<APIRequestContext['get']>>): Promise<JsonObject> {
  expect(response.ok(), `GET ${response.url()} failed: HTTP ${response.status()}`).toBeTruthy()
  return (await response.json()) as JsonObject
}

function orderPath(orderNumber: string): string {
  return orderNumber.replaceAll('/', '-')
}

async function findReusablePair(request: APIRequestContext, token: string): Promise<CandidatePair> {
  const headers = { Authorization: `Bearer ${token}` }
  const listResponse = await request.get(`${API_BASE}/api/v1/partner-orders`, {
    headers,
    params: { page: 0, size: 500, includeDeleted: 'false' },
  })
  const listEnvelope = await envelopeJson(listResponse)
  const summaries = (listEnvelope.data?.content ?? []) as JsonObject[]
  const groups = new Map<string, JsonObject[]>()
  for (const order of summaries) {
    if (!['DRAFT', 'ON_HOLD'].includes(String(order.status))) continue
    if (order.isDeleted === true || order.mergeEligible === false || !order.partnerCode) continue
    const current = groups.get(String(order.partnerCode)) ?? []
    current.push(order)
    groups.set(String(order.partnerCode), current)
  }

  for (const [partnerCode, orders] of groups) {
    if (orders.length < 2) continue
    const partnerResponse = await request.get(`${API_BASE}/admin/partners/search`, {
      headers,
      params: { q: partnerCode, size: 20, status: 'ACTIVE' },
    })
    if (!partnerResponse.ok()) continue
    const partnerItems = (((await partnerResponse.json()) as JsonObject).data?.items ?? []) as JsonObject[]
    const exactPartner = partnerItems.find((partner) =>
      String(partner.partnerCode) === partnerCode && Boolean(partner.partnerId),
    )
    if (!exactPartner) continue
    const reusable: JsonObject[] = []
    for (const order of orders) {
      const detailResponse = await request.get(
        `${API_BASE}/api/v1/partner-orders/${encodeURIComponent(orderPath(String(order.orderNumber)))}`,
        { headers },
      )
      if (!detailResponse.ok()) continue
      const detail = ((await detailResponse.json()) as JsonObject).data as JsonObject
      const hasRemaining = (detail.lines ?? []).some((line: JsonObject) =>
        Number(line.quantity) - Number(line.convertedQuantity ?? 0) >= 1,
      )
      if (hasRemaining) reusable.push(detail)
      if (reusable.length >= 2) {
        const first = reusable[0]!
        const second = reusable.find((item) => String(item.memo ?? '') !== String(first.memo ?? ''))
        if (second) {
          return {
            partnerCode,
            orderNumbers: [String(first.orderNumber), String(second.orderNumber)],
          }
        }
      }
    }
  }
  throw new Error('S25 BLOCK: UI에서 선택 가능한 같은 거래처·서로 다른 메모·잔여수량 1 이상 실 주문 2건이 없습니다. 데이터 생성 금지 조건 때문에 POST 시나리오를 진행할 수 없습니다.')
}

async function shot(page: Page, name: string): Promise<string> {
  const fileName = `${name}-real-qa.png`
  await page.screenshot({ path: path.join(SHOTS, fileName), fullPage: true })
  return fileName
}

async function choosePartnerAndOrders(page: Page, pair: CandidatePair, marker: string): Promise<void> {
  await page.getByTestId('merge-convert-open').click()
  await expect(page.getByTestId('merge-convert-dialog-body')).toBeVisible()
  const partnerSearch = page.getByTestId('merge-convert-partner-search')
  const partnerResponsePromise = page.waitForResponse((response) =>
    response.url().includes('/admin/partners/search') && response.request().method() === 'GET',
  )
  await partnerSearch.fill(pair.partnerCode)
  const partnerResponse = await partnerResponsePromise
  const partnerBody = await partnerResponse.json() as JsonObject
  console.log(`[S25_PARTNER_SEARCH] HTTP ${partnerResponse.status()} items=${partnerBody.data?.items?.length ?? 0}`)
  const partnerList = page.getByRole('listbox', { name: '거래처 목록' })
  await expect(partnerList).toBeVisible()
  await partnerList.getByRole('option').filter({ hasText: pair.partnerCode }).first().click()

  const orderSearch = page.getByTestId('merge-convert-order-search')
  for (const orderNumber of pair.orderNumbers) {
    await orderSearch.fill(orderNumber)
    const option = page.getByTestId(`merge-convert-order-option-${orderNumber}`)
    await expect(option).toBeVisible()
    await option.click()
    await expect(page.getByTestId(`merge-convert-order-chip-${orderNumber}`)).toBeVisible()
  }
  await expect(page.getByTestId('merge-convert-selected-order-count')).toContainText('2건')
  for (const orderNumber of pair.orderNumbers) {
    await expect(page.getByTestId(`merge-convert-order-group-${orderNumber}`)).toBeVisible()
  }

  const allQty = page.locator('[data-testid^="merge-convert-qty-"]')
  for (let index = 0; index < await allQty.count(); index += 1) {
    const input = allQty.nth(index)
    if (await input.isEnabled()) await input.fill('0')
  }
  for (const orderNumber of pair.orderNumbers) {
    const group = page.getByTestId(`merge-convert-order-group-${orderNumber}`)
    const input = group.locator('[data-testid^="merge-convert-qty-"]:enabled').first()
    await expect(input).toBeVisible()
    await input.fill('1')
  }

  const memoConflict = page.getByTestId('merge-convert-conflict-memo')
  await expect(memoConflict, 'S25-825 식별자를 넣을 메모 충돌 필드가 없습니다.').toBeVisible()
  await page.getByTestId('merge-convert-conflict-memo-radio-custom').check()
  await page.getByTestId('merge-convert-conflict-memo-input-custom').fill(marker)

  const conflictSection = page.getByTestId('merge-convert-conflict-section')
  const groups = conflictSection.locator('[role="radiogroup"]')
  for (let index = 0; index < await groups.count(); index += 1) {
    const group = groups.nth(index)
    const checked = group.locator('input[type="radio"]:checked')
    if (await checked.count() === 0) await group.locator('input[type="radio"]').first().check()
  }
}

async function explicitWarehouse(page: Page, code: string): Promise<string> {
  const input = page.getByTestId('merge-convert-warehouse').getByRole('combobox')
  await input.fill('')
  await input.fill('창')
  const selection = page.getByRole('dialog', { name: '출고 창고 검색 결과' })
  await expect(selection).toBeVisible()
  const radio = selection.getByRole('radio').filter({ hasText: code })
  const labels = await selection.getByRole('radio').evaluateAll((nodes) =>
    nodes.map((node) => node.getAttribute('aria-label') ?? ''),
  )
  const label = labels.find((candidate) => candidate.includes(code))
  expect(label, `${code} 창고 radio가 없습니다.`).toBeTruthy()
  await selection.getByRole('radio', { name: label! }).check()
  await selection.getByRole('button', { name: '선택 확정' }).click()
  await expect(selection).toBeHidden()
  await expect(input).toHaveValue(new RegExp(`^${code}`))
  return input.inputValue()
}

async function autoWarehouse(page: Page, sequential: boolean): Promise<string> {
  const input = page.getByTestId('merge-convert-warehouse').getByRole('combobox')
  await input.focus()
  await input.press(process.platform === 'darwin' ? 'Meta+A' : 'Control+A')
  if (sequential) await input.pressSequentially('HQ')
  else await input.press('H'), await input.press('Q')
  await expect(page.getByRole('dialog', { name: '출고 창고 검색 결과' })).toBeHidden()
  await expect(input).toHaveValue(/^HQ-001 · /)
  return input.inputValue()
}

async function publishAndCapture(
  page: Page,
  scenario: string,
  expectedWarehouse: string,
): Promise<{ raw: string; slipNo: string; responseStatus: number; responseBody: string }> {
  const submit = page.getByTestId('merge-convert-submit')
  await expect(submit).toBeEnabled()
  const requestPromise = page.waitForRequest((request) =>
    request.method() === 'POST' && request.url().endsWith(POST_PATH),
  )
  const responsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST' && response.url().endsWith(POST_PATH),
  )
  await submit.click()
  const [request, response] = await Promise.all([requestPromise, responsePromise])
  const raw = request.postData() ?? ''
  const parsed = JSON.parse(raw) as JsonObject
  expect(request.url()).toBe(`${API_BASE}${POST_PATH}`)
  expect(parsed.warehouseCode).toBe(expectedWarehouse)
  expect(parsed.shippingInfo?.memo).toContain(MARKER)
  const responseBody = await response.text()
  const responseJson = JSON.parse(responseBody) as JsonObject
  const slipNo = String(responseJson.data?.slipNo ?? '')
  console.log(`[S25_${scenario}_POST] ${raw}`)
  console.log(`[S25_${scenario}_RESPONSE] HTTP ${response.status()} body=${responseBody}`)
  if (response.ok()) {
    expect(slipNo).not.toBe('')
    await expect(page.getByTestId('merge-convert-success-toast')).toBeVisible()
  } else {
    await expect(page.getByTestId('merge-convert-error')).toBeVisible()
  }
  return { raw, slipNo, responseStatus: response.status(), responseBody }
}

test('PR #1120 S25 — 실 POST 창고 식별자 무결성', async ({ page }) => {
  mkdirSync(SHOTS, { recursive: true })

  const login = await page.request.post(`${API_BASE}/auth/login`, {
    data: { loginId: 'dev_master', password: PASSWORD },
  })
  expect(login.ok(), `실 gateway 로그인 실패: HTTP ${login.status()}`).toBeTruthy()
  const loginData = ((await login.json()) as JsonObject).data ?? {}
  expect(String(loginData.token ?? '')).not.toBe('')
  const token = String(loginData.token)
  await page.addInitScript((session: JsonObject) => {
    Object.defineProperty(window, 'samhanAuth', {
      configurable: true,
      value: {
        getToken: async () => session,
        setToken: async () => undefined,
        clearToken: async () => undefined,
      },
    })
  }, {
    token,
    userId: loginData.userId ?? '',
    role: loginData.role ?? 'MASTER',
    fullName: loginData.displayName ?? '개발마스터',
    partnerCode: null,
  })

  const liveRequests: string[] = []
  page.on('request', (request) => {
    if (['xhr', 'fetch'].includes(request.resourceType())) liveRequests.push(request.url())
  })
  await page.goto(`${APP_BASE}/#/sales/partner-orders`, { waitUntil: 'domcontentloaded' })
  await expect(page.getByTestId('merge-convert-open')).toBeVisible({ timeout: 30_000 })
  await expect.poll(() => liveRequests.some((url) => url.startsWith(`${API_BASE}/`))).toBeTruthy()
  expect(liveRequests.some((url) => url.includes(':5825/api/'))).toBeFalsy()
  console.log(`[S25_ENV] app=${APP_BASE} gateway=${API_BASE} observed=${liveRequests.filter((url) => url.startsWith(`${API_BASE}/`)).slice(0, 8).join(',')}`)
  await shot(page, '00-environment-real-gateway')

  const pair = await findReusablePair(page.request, token)
  console.log(`[S25_FIXTURE] partner=${pair.partnerCode} orders=${pair.orderNumbers.join(',')}`)

  // #4 미확정 상태: disabled 및 POST 0건.
  await choosePartnerAndOrders(page, pair, `${MARKER} / S25-04`)
  const postsBefore = liveRequests.filter((url) => url.endsWith(POST_PATH)).length
  const submit = page.getByTestId('merge-convert-submit')
  await expect(submit).toBeDisabled()
  await submit.press('Enter')
  await page.waitForTimeout(400)
  expect(liveRequests.filter((url) => url.endsWith(POST_PATH)).length).toBe(postsBefore)
  await shot(page, '04-unconfirmed-disabled-post-zero')

  // #3 #1141 표시 이상 상태. 표시 결함 자체는 계수하지 않고 POST 값만 판정한다.
  await page.getByTestId('merge-convert-conflict-memo-input-custom').fill(`${MARKER} / S25-03`)
  await shot(page, '03-1141-before-hq-input')
  const anomalousValue = await autoWarehouse(page, true)
  console.log(`[S25_03_DISPLAY] ${anomalousValue}`)
  expect(anomalousValue).toBe('HQ-001 · 본사창고Q')
  await shot(page, '03-1141-anomalous-display-before-publish')
  await publishAndCapture(page, '03', 'HQ-001')
  await shot(page, '03-1141-after-post-response')

  // #1 같은 실 표본에서 모달 명시확정 후 POST. 앞 요청은 409라 주문이 소모되지 않았다.
  await page.getByTestId('merge-convert-conflict-memo-input-custom').fill(`${MARKER} / S25-01`)
  await shot(page, '01-explicit-before-warehouse-confirm')
  const explicitValue = await explicitWarehouse(page, 'HQ-001')
  console.log(`[S25_01_DISPLAY] ${explicitValue}`)
  await shot(page, '01-explicit-after-warehouse-confirm')
  await publishAndCapture(page, '01', 'HQ-001')
  await shot(page, '01-explicit-after-post-response')

  // #2 Ctrl+A → HQ 단건 자동확정.
  await page.getByTestId('merge-convert-conflict-memo-input-custom').fill(`${MARKER} / S25-02`)
  await shot(page, '02-auto-before-hq-input')
  const autoValue = await autoWarehouse(page, false)
  console.log(`[S25_02_DISPLAY] ${autoValue}`)
  await shot(page, '02-auto-after-hq-confirm')
  await publishAndCapture(page, '02', 'HQ-001')
  await shot(page, '02-auto-after-post-response')

  // #5 취소·backdrop 뒤 다른 창고를 최종 확정한다.
  await page.getByTestId('merge-convert-conflict-memo-input-custom').fill(`${MARKER} / S25-05`)
  const firstWarehouse = await explicitWarehouse(page, 'CS-001')
  console.log(`[S25_05_FIRST_DISPLAY] ${firstWarehouse}`)
  const warehouseInput = page.getByTestId('merge-convert-warehouse').getByRole('combobox')
  await warehouseInput.fill('창')
  let selection = page.getByRole('dialog', { name: '출고 창고 검색 결과' })
  await expect(selection).toBeVisible()
  await selection.getByRole('button', { name: '취소' }).click()
  await expect(selection).toBeHidden()
  await shot(page, '05-after-selection-cancel')
  await warehouseInput.fill('')
  await warehouseInput.fill('창')
  selection = page.getByRole('dialog', { name: '출고 창고 검색 결과' })
  await expect(selection).toBeVisible()
  await page.getByTestId('ds-modal-backdrop').last().click({ position: { x: 4, y: 4 } })
  await expect(selection).toBeHidden()
  await shot(page, '05-after-selection-backdrop')
  const finalWarehouse = await explicitWarehouse(page, 'HQ-001')
  console.log(`[S25_05_FINAL_DISPLAY] ${finalWarehouse}`)
  await shot(page, '05-final-warehouse-before-post')
  await publishAndCapture(page, '05', 'HQ-001')
  await shot(page, '05-after-post-response')
})
