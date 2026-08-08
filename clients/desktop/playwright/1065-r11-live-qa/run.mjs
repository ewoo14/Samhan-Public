import { chromium } from 'playwright'
import fs from 'node:fs'
import path from 'node:path'

const APP = 'http://localhost:5175'
const GATEWAY = 'http://localhost:8080'
const OUT = path.resolve(process.cwd(), '../../docs/qa-shots/1065-r11-live-qa')
fs.mkdirSync(OUT, { recursive: true })

const samples = {
  personal: { slipNo: '2026/03/08-1', id: '42b606c6-3d98-4391-891e-39dea106f5e6' },
  static: { slipNo: '2026/01/27-1', id: '139d3a98-40f7-406b-8223-29fb7111b91c' },
  blocked: { slipNo: '2026/01/28-1', id: '2d6f17c2-0e80-4b42-89bf-eae93e716b46' },
  confirmed: { slipNo: '2026/02/15-1', id: '67b46bea-8b55-4297-aa9d-0b3aca31edd6' },
  inboundBlocked: { slipNo: '2026/03/31-1', id: '604b9993-f880-4305-a830-9e075f73a59d' },
  inboundStatic: { slipNo: '2026/04/01-1', id: 'bb104676-3228-472b-a5d8-ad274d527da3' },
}

const observationPath = path.join(OUT, 'observations.json')
const observations = process.env.QA_RESUME === '1' && fs.existsSync(observationPath) ? JSON.parse(fs.readFileSync(observationPath, 'utf8')) : {
  startedAt: new Date().toISOString(),
  renderer: process.cwd(),
  app: APP,
  gateway: GATEWAY,
  mockRoutesInstalled: 0,
  scenarios: {},
  network: [],
  changedSlips: [],
}

function redactJson(text, isLogin) {
  if (!text) return text
  try {
    const value = JSON.parse(text)
    const walk = (node) => {
      if (!node || typeof node !== 'object') return
      for (const key of Object.keys(node)) {
        if (/^(token|accessToken|refreshToken|password)$/i.test(key)) node[key] = '<redacted>'
        else walk(node[key])
      }
    }
    walk(value)
    return JSON.stringify(value)
  } catch {
    return isLogin ? '<redacted non-JSON login payload>' : text
  }
}

async function bindNetwork(page, actor) {
  const pending = []
  page.on('response', (response) => {
    const url = response.url()
    if (!url.startsWith(GATEWAY)) return
    const pathname = new URL(url).pathname
    const isFiniteEvidenceResponse = pathname === '/auth/login'
      || pathname === '/slips'
      || /^\/slips\/[0-9a-f-]+(?:\/(?:inspect|ship|deliver|confirm))?$/.test(pathname)
    if (!isFiniteEvidenceResponse) return
    const task = (async () => {
      const request = response.request()
      const login = new URL(url).pathname === '/auth/login'
      let body = ''
      try { body = await response.text() } catch { body = '<unavailable>' }
      let serverAddr = null
      try { serverAddr = await response.serverAddr() } catch {}
      observations.network.push({
        actor,
        method: request.method(),
        url,
        requestBody: redactJson(request.postData() ?? '', login),
        status: response.status(),
        responseBody: redactJson(body, login),
        serverAddr,
        headers: await response.allHeaders(),
      })
    })()
    pending.push(task)
  })
  return async () => Promise.allSettled(pending)
}

async function closeUpdateDialog(page) {
  const close = page.getByRole('button', { name: '닫기', exact: true }).first()
  if (await close.isVisible().catch(() => false)) await close.click()
}

async function login(browser, loginId, password, actor) {
  const context = await browser.newContext({ viewport: { width: 1600, height: 1100 } })
  const page = await context.newPage()
  const flushNetwork = await bindNetwork(page, actor)
  await page.goto(`${APP}/#/login`, { waitUntil: 'domcontentloaded' })
  await page.getByTestId('login-id-input').fill(loginId)
  await page.getByTestId('login-password-input').fill(password)
  await page.screenshot({ path: path.join(OUT, `${actor}-00-login.png`), fullPage: true })
  const loginResponse = page.waitForResponse((r) => r.url() === `${GATEWAY}/auth/login` && r.request().method() === 'POST')
  await page.getByTestId('login-submit-button').click()
  const response = await loginResponse
  await page.waitForFunction(() => !location.hash.startsWith('#/login'), null, { timeout: 15_000 })
  await page.waitForTimeout(900)
  await closeUpdateDialog(page)
  await page.screenshot({ path: path.join(OUT, `${actor}-01-after-login.png`), fullPage: true })
  return { context, page, flushNetwork, loginStatus: response.status() }
}

async function api(page, method, pathname, body) {
  return page.evaluate(async ({ gateway, method, pathname, body }) => {
    const response = await fetch(`${gateway}${pathname}`, {
      method,
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    return { status: response.status, text: await response.text() }
  }, { gateway: GATEWAY, method, pathname, body })
}

async function openList(page, actor, route = 'sales') {
  await page.goto(`${APP}/#/${route}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1800)
  await closeUpdateDialog(page)
  await page.screenshot({ path: path.join(OUT, `${actor}-02-list.png`), fullPage: true })
  return (await page.locator('body').innerText()).replace(/\s+/g, ' ').trim()
}

async function openDetail(page, actor, sample, route = 'sales', suffix = 'detail') {
  await page.goto(`${APP}/#/${route}/${sample.id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1800)
  await closeUpdateDialog(page)
  const text = (await page.locator('body').innerText()).replace(/\s+/g, ' ').trim()
  await page.screenshot({ path: path.join(OUT, `${actor}-${suffix}.png`), fullPage: true })
  return text
}

function parseEnvelope(text) {
  try { return JSON.parse(text) } catch { return null }
}

async function clickTransition(page, actor, sample, label, expectedStatus, sequence) {
  const button = page.getByRole('button', { name: `완료 (${label})`, exact: true })
  const enabled = await button.isEnabled()
  if (!enabled) throw new Error(`${actor}: ${label} button disabled`)
  const responsePromise = page.waitForResponse((r) =>
    r.url() === `${GATEWAY}/slips/${sample.id}/${sequence.action}` && r.request().method() === 'POST',
  )
  await button.click()
  const response = await responsePromise
  const raw = await response.text()
  const parsed = parseEnvelope(raw)
  const actualStatus = parsed?.data?.status ?? null
  observations.changedSlips.push({ slipNo: sample.slipNo, before: sequence.before, after: actualStatus, actor, api: sequence.action })
  await page.waitForTimeout(1100)
  await page.screenshot({ path: path.join(OUT, `${actor}-${sequence.shot}.png`), fullPage: true })
  if (response.status() !== 200 || actualStatus !== expectedStatus) {
    throw new Error(`${actor}: ${sequence.action} expected 200/${expectedStatus}, got ${response.status()}/${actualStatus}`)
  }
  return { status: response.status(), actualStatus, response: raw }
}

const browser = await chromium.launch({ headless: true })
try {
  // ① 결재선 개인: 목록 → INSPECTING 상세 → inspect → ship → deliver → confirm → 확정 재조회.
  if (process.env.QA_RESUME !== '1') {
    const actor = '01-kimgicheol'
    const { context, page, flushNetwork, loginStatus } = await login(browser, 'kimgicheol', 'samhan!2026', actor)
    const listText = await openList(page, actor)
    const detailText = await openDetail(page, actor, samples.personal, 'sales', '03-inspecting')
    const transitions = []
    transitions.push(await clickTransition(page, actor, samples.personal, '처리 완료', 'COMPLETED', { action: 'inspect', before: 'INSPECTING', shot: '04-completed' }))
    transitions.push(await clickTransition(page, actor, samples.personal, '배송 시작', 'SHIPPING', { action: 'ship', before: 'COMPLETED', shot: '05-shipping' }))
    transitions.push(await clickTransition(page, actor, samples.personal, '배송 완료', 'DELIVERED', { action: 'deliver', before: 'SHIPPING', shot: '06-delivered' }))
    transitions.push(await clickTransition(page, actor, samples.personal, '확정', 'CONFIRMED', { action: 'confirm', before: 'DELIVERED', shot: '07-confirmed' }))
    const confirmedText = await openDetail(page, actor, samples.personal, 'sales', '08-confirmed-reopen')
    observations.scenarios.personal = { loginStatus, listContainsTarget: listText.includes(samples.personal.slipNo), detailText, transitions, confirmedReopenContainsSlipNo: confirmedText.includes(samples.personal.slipNo), confirmedText }
    await flushNetwork()
    await context.close()
  }

  // ② 정적 권한자: 별도 INSPECTING 표본으로 같은 전체 사슬.
  {
    const actor = '02-dev-manager'
    const { context, page, flushNetwork, loginStatus } = await login(browser, 'dev_manager', 'dev_p05_pass!', actor)
    const listText = await openList(page, actor)
    const detailText = await openDetail(page, actor, samples.static, 'sales', '03-inspecting')
    const transitions = []
    const inspectButton = page.getByRole('button', { name: '완료 (처리 완료)', exact: true })
    const inspectButtonEnabled = await inspectButton.isEnabled()
    const inspectApi = await api(page, 'POST', `/slips/${samples.static.id}/inspect`, {})
    const inspectParsed = parseEnvelope(inspectApi.text)
    await page.screenshot({ path: path.join(OUT, `${actor}-04-after-api-denial.png`), fullPage: true })
    transitions.push({ status: inspectApi.status, actualStatus: inspectParsed?.data?.status ?? null, response: inspectApi.text, guiButtonEnabled: inspectButtonEnabled })
    const all = await api(page, 'GET', '/slips?slipType=OUTBOUND&page=0&size=500')
    const allData = parseEnvelope(all.text)?.data?.content ?? []
    observations.outboundSampleIds = allData.map((s) => ({ id: s.id, slipNo: s.slipNo, status: s.status }))
    observations.scenarios.static = { loginStatus, listContainsTarget: listText.includes(samples.static.slipNo), detailText, inspectButtonEnabled, transitions }
    await flushNetwork()
    await context.close()
  }

  // ③ 무권한 계정: 목록/상세/동일 전이 API와 실 데이터 상세 차단 건수.
  {
    const actor = '03-dev-accountant'
    const { context, page, flushNetwork, loginStatus } = await login(browser, 'dev_accountant', 'dev_p05_pass!', actor)
    const listText = await openList(page, actor)
    const detailText = await openDetail(page, actor, samples.blocked, 'sales', '03-blocked-detail')
    const transitionAttempt = await api(page, 'POST', `/slips/${samples.blocked.id}/inspect`, {})
    await page.screenshot({ path: path.join(OUT, `${actor}-04-after-api-denial.png`), fullPage: true })
    const detailChecks = []
    for (const sample of observations.outboundSampleIds ?? []) {
      if (!['INSPECTING', 'COMPLETED', 'SHIPPING', 'DELIVERED', 'CONFIRMED'].includes(sample.status)) continue
      const result = await api(page, 'GET', `/slips/${sample.id}`)
      detailChecks.push({ slipNo: sample.slipNo, state: sample.status, status: result.status })
    }
    observations.scenarios.blocked = {
      loginStatus,
      listContainsTarget: listText.includes(samples.blocked.slipNo),
      listText,
      detailText,
      transitionAttempt,
      detailCounts: detailChecks.reduce((acc, item) => { const key = `${item.state}:${item.status}`; acc[key] = (acc[key] ?? 0) + 1; return acc }, {}),
    }
    await flushNetwork()
    await context.close()
  }

  // ④ INBOUND: OUTBOUND 결재선 개인 차단 후 정적 권한자의 기존 검수 성공.
  {
    const actor = '04-kimgicheol-inbound-block'
    const { context, page, flushNetwork, loginStatus } = await login(browser, 'kimgicheol', 'samhan!2026', actor)
    const listText = await openList(page, actor, 'purchases')
    const detailText = await openDetail(page, actor, samples.inboundBlocked, 'purchases', '03-blocked-detail')
    const transitionAttempt = await api(page, 'POST', `/slips/${samples.inboundBlocked.id}/inspect`, {})
    observations.scenarios.inboundPersonalBlocked = { loginStatus, listContainsTarget: listText.includes(samples.inboundBlocked.slipNo), detailText, transitionAttempt }
    await flushNetwork()
    await context.close()
  }
  {
    const actor = '05-dev-inventory-inbound'
    const { context, page, flushNetwork, loginStatus } = await login(browser, 'dev_inventory', 'dev_p05_pass!', actor)
    const listText = await openList(page, actor, 'warehouse/inbound-inspections')
    const transitionResult = await api(page, 'POST', `/slips/${samples.inboundStatic.id}/inspect`, {})
    const transitionParsed = parseEnvelope(transitionResult.text)
    if (transitionResult.status === 200) {
      observations.changedSlips.push({ slipNo: samples.inboundStatic.slipNo, before: 'INSPECTING', after: transitionParsed?.data?.status ?? null, actor, api: 'inspect' })
    }
    await page.screenshot({ path: path.join(OUT, `${actor}-03-after-slip-inspect.png`), fullPage: true })
    observations.scenarios.inboundStatic = { loginStatus, listContainsTarget: listText.includes(samples.inboundStatic.slipNo), listText, transition: { status: transitionResult.status, actualStatus: transitionParsed?.data?.status ?? null, response: transitionResult.text } }
    await flushNetwork()
    await context.close()
  }
} finally {
  observations.finishedAt = new Date().toISOString()
  fs.writeFileSync(observationPath, JSON.stringify(observations, null, 2), 'utf8')
  await browser.close()
}
