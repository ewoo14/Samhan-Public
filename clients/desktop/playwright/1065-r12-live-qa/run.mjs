import { chromium } from 'playwright'
import fs from 'node:fs'
import path from 'node:path'

const APP = 'http://localhost:5175'
const GATEWAY = 'http://localhost:8080'
const OUT = path.resolve(process.cwd(), '../../docs/qa-shots/1065-r12-live-qa')
fs.mkdirSync(OUT, { recursive: true })

const samples = {
  manager: { slipNo: '2026/08/07-3', id: 'd3893eda-0873-42ff-8b4d-b7c0614cb1a1' },
  personal: { slipNo: '2026/03/09-1', id: '714e2d40-4b2e-4dff-9a68-99f04d269a9f' },
  blocked: { slipNo: '2026/02/26-1', id: '20582d1e-4744-4ac6-9b1d-a8b5ec21e69c' },
}

const observations = {
  startedAt: new Date().toISOString(),
  rendererWorktree: process.cwd(),
  app: APP,
  gateway: GATEWAY,
  headless: true,
  mockRoutesInstalled: 0,
  scenarios: {},
  changedSlips: [],
  network: [],
}

function redactJson(text, login = false) {
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
    return login ? '<redacted non-JSON login payload>' : text
  }
}

function bindNetwork(page, actor) {
  const pending = []
  page.on('response', (response) => {
    const url = response.url()
    if (!url.startsWith(GATEWAY)) return
    const pathname = new URL(url).pathname
    if (pathname !== '/auth/login'
      && !/^\/slips\/[0-9a-f-]+(?:\/(?:ship|deliver|confirm))?$/.test(pathname)) return
    pending.push((async () => {
      const request = response.request()
      const login = pathname === '/auth/login'
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
    })())
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
  const flushNetwork = bindNetwork(page, actor)
  await page.goto(`${APP}/#/login`, { waitUntil: 'domcontentloaded' })
  await page.getByTestId('login-id-input').fill(loginId)
  await page.getByTestId('login-password-input').fill(password)
  await page.screenshot({ path: path.join(OUT, `${actor}-00-login.png`), fullPage: true })
  const responsePromise = page.waitForResponse((r) => r.url() === `${GATEWAY}/auth/login` && r.request().method() === 'POST')
  await page.getByTestId('login-submit-button').click()
  const response = await responsePromise
  await page.waitForFunction(() => !location.hash.startsWith('#/login'), null, { timeout: 15_000 })
  await page.waitForTimeout(800)
  await closeUpdateDialog(page)
  await page.screenshot({ path: path.join(OUT, `${actor}-01-after-login.png`), fullPage: true })
  return { context, page, flushNetwork, loginStatus: response.status() }
}

async function openList(page, actor) {
  await page.goto(`${APP}/#/sales`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1600)
  await closeUpdateDialog(page)
  const body = (await page.locator('body').innerText()).replace(/\s+/g, ' ').trim()
  await page.screenshot({ path: path.join(OUT, `${actor}-02-list.png`), fullPage: true })
  return body
}

async function openDetail(page, actor, sample, shot) {
  await page.goto(`${APP}/#/sales/${sample.id}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1700)
  await closeUpdateDialog(page)
  const body = (await page.locator('body').innerText()).replace(/\s+/g, ' ').trim()
  await page.screenshot({ path: path.join(OUT, `${actor}-${shot}.png`), fullPage: true })
  return body
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

function parseEnvelope(text) {
  try { return JSON.parse(text) } catch { return null }
}

async function transition(page, actor, sample, label, action, before, after, shot) {
  const button = page.getByRole('button', { name: `완료 (${label})`, exact: true })
  const visible = await button.isVisible()
  const enabled = visible && await button.isEnabled()
  await page.screenshot({ path: path.join(OUT, `${actor}-${shot}-before.png`), fullPage: true })
  if (!enabled) return { visible, enabled, action, before, expectedAfter: after, status: null, actualStatus: null, responseBody: null }
  const responsePromise = page.waitForResponse((r) => r.url() === `${GATEWAY}/slips/${sample.id}/${action}` && r.request().method() === 'POST')
  await button.click()
  const response = await responsePromise
  const raw = await response.text()
  const actualStatus = parseEnvelope(raw)?.data?.status ?? null
  await page.waitForTimeout(1000)
  await page.screenshot({ path: path.join(OUT, `${actor}-${shot}-after.png`), fullPage: true })
  if (response.status() === 200 && actualStatus === after) {
    observations.changedSlips.push({ slipNo: sample.slipNo, actor, api: action, before, after: actualStatus })
  }
  return { visible, enabled, action, before, expectedAfter: after, status: response.status(), actualStatus, responseBody: raw }
}

async function allowedPath(browser, { actor, loginId, password, sample }) {
  const { context, page, flushNetwork, loginStatus } = await login(browser, loginId, password, actor)
  try {
    const listText = await openList(page, actor)
    const completedText = await openDetail(page, actor, sample, '03-completed')
    const transitions = []
    transitions.push(await transition(page, actor, sample, '배송 시작', 'ship', 'COMPLETED', 'SHIPPING', '04-ship'))
    transitions.push(await transition(page, actor, sample, '배송 완료', 'deliver', 'SHIPPING', 'DELIVERED', '05-deliver'))
    transitions.push(await transition(page, actor, sample, '확정', 'confirm', 'DELIVERED', 'CONFIRMED', '06-confirm'))
    const confirmedText = await openDetail(page, actor, sample, '07-confirmed-reopen')
    observations.scenarios[actor] = {
      loginStatus,
      listContainsTarget: listText.includes(sample.slipNo),
      completedText,
      transitions,
      confirmedReopenContainsSlipNo: confirmedText.includes(sample.slipNo),
      confirmedText,
    }
  } finally {
    await flushNetwork()
    await context.close()
  }
}

const browser = await chromium.launch({ headless: true })
try {
  await allowedPath(browser, { actor: '01-dev-manager', loginId: 'dev_manager', password: 'dev_p05_pass!', sample: samples.manager })
  await allowedPath(browser, { actor: '02-kimgicheol', loginId: 'kimgicheol', password: 'samhan!2026', sample: samples.personal })

  const actor = '03-dev-accountant'
  const { context, page, flushNetwork, loginStatus } = await login(browser, 'dev_accountant', 'dev_p05_pass!', actor)
  try {
    const listText = await openList(page, actor)
    const detailText = await openDetail(page, actor, samples.blocked, '03-completed-blocked')
    const button = page.getByRole('button', { name: '완료 (배송 시작)', exact: true })
    const buttonVisible = await button.isVisible().catch(() => false)
    const buttonEnabled = buttonVisible && await button.isEnabled().catch(() => false)
    const attempt = await api(page, 'POST', `/slips/${samples.blocked.id}/ship`, {})
    await page.screenshot({ path: path.join(OUT, `${actor}-04-after-ship-denial.png`), fullPage: true })
    observations.scenarios[actor] = { loginStatus, listContainsTarget: listText.includes(samples.blocked.slipNo), detailText, buttonVisible, buttonEnabled, attempt }
  } finally {
    await flushNetwork()
    await context.close()
  }
} finally {
  observations.finishedAt = new Date().toISOString()
  fs.writeFileSync(path.join(OUT, 'observations.json'), JSON.stringify(observations, null, 2), 'utf8')
  await browser.close()
}
