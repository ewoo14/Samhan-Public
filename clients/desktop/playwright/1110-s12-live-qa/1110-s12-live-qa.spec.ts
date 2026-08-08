import { execFileSync, spawn, type ChildProcessWithoutNullStreams } from 'node:child_process'
import * as fs from 'node:fs'
import * as path from 'node:path'
import { test, expect, type BrowserContext, type Page, type Response } from '@playwright/test'
import { resolveQaCredential } from '../../../../scripts/lib/qa-credentials.cjs'

const BASE_URL = 'http://127.0.0.1:5176'
const GATEWAY_URL = 'http://127.0.0.1:8080'
const SHOT_DIR = path.resolve(process.cwd(), '../../docs/qa-shots/1110-s12-live-qa')
const ORDER_A = { pathId: '2026-06-08-1980', orderNo: '2026/06/08-1980' }
const ORDER_B = { pathId: '2026-06-08-1982', orderNo: '2026/06/08-1982' }

type Evidence = {
  environment: Record<string, unknown>
  triggerCounts: Record<string, number>
  scenarios: Record<string, unknown>
  network: Array<{ page: string; method: string; path: string; status: number }>
}

fs.mkdirSync(SHOT_DIR, { recursive: true })
const evidence: Evidence = { environment: {}, triggerCounts: {}, scenarios: {}, network: [] }

function sanitizePath(rawUrl: string): string {
  const url = new URL(rawUrl)
  return url.pathname.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, '<uuid>')
}

async function login(loginId: string, envKey = 'QA_DEV_DEFAULT_PASSWORD'): Promise<string> {
  const response = await fetch(`${GATEWAY_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ loginId, password: resolveQaCredential(envKey) }),
  })
  const body = await response.json() as { data?: { token?: string } }
  if (!body.data?.token) throw new Error(`로그인 실패: ${loginId}, HTTP ${response.status}`)
  return body.data.token
}

function observe(page: Page, name: string): void {
  page.on('response', (response) => {
    if (!response.url().startsWith(GATEWAY_URL)) return
    const request = response.request()
    if (!request.url().includes('/partner-orders')) return
    evidence.network.push({
      page: name,
      method: request.method(),
      path: sanitizePath(request.url()),
      status: response.status(),
    })
  })
}

async function prepare(context: BrowserContext, name: string, token: string, pathId: string): Promise<Page> {
  const page = await context.newPage()
  observe(page, name)
  await page.addInitScript(({ token }) => {
    const qaWindow = window as typeof window & { __qaAuth?: { token: string; role: string; displayName: string } }
    qaWindow.__qaAuth = { token, role: 'MASTER', displayName: '[DEV-SEED] 개발마스터' }
    Object.defineProperty(window, 'samhanAuth', {
      configurable: true,
      value: {
        getToken: async () => ({
          token: qaWindow.__qaAuth?.token,
          userId: 'a0000000-0000-0000-0000-000000000001',
          role: qaWindow.__qaAuth?.role,
          displayName: qaWindow.__qaAuth?.displayName,
        }),
      },
    })
  }, { token })
  await page.goto(`${BASE_URL}/#/sales/partner-orders/${pathId}?mockRole=MASTER`, { waitUntil: 'domcontentloaded' })
  await expect(page.getByTestId('partner-order-collaboration-panel')).toBeVisible({ timeout: 20_000 })
  return page
}

async function openHistory(page: Page): Promise<void> {
  const dialog = page.getByRole('dialog', { name: '버전 이력' })
  if (await dialog.count() === 0) await page.getByTestId('partner-order-version-history-open').click()
  await expect(page.locator('[data-testid^="partner-order-version-history-row-"]').first()).toBeVisible({ timeout: 20_000 })
}

async function restoreByClick(page: Page, revisionNo: number): Promise<{ status: number; toast: string }> {
  await openHistory(page)
  await page.getByTestId(`partner-order-version-history-restore-button-${revisionNo}`).click()
  const responsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST'
      && response.url().includes(`/revisions/${revisionNo}/restore`), { timeout: 20_000 })
  await page.getByTestId('partner-order-version-history-restore-confirm').click()
  const response = await responsePromise
  const toast = page.getByTestId('partner-order-version-history-toast')
  await expect(toast).toBeVisible({ timeout: 20_000 })
  return { status: response.status(), toast: (await toast.innerText()).trim() }
}

function dbCounts(orderNo: string): Record<string, number | string> {
  const sql = `SELECT o.revision_count,`
    + `(SELECT COUNT(*) FROM partner_order_revisions r WHERE r.partner_order_id=o.id AND r.is_deleted=false),`
    + `(SELECT COALESCE(MAX(r.revision_no),0) FROM partner_order_revisions r WHERE r.partner_order_id=o.id AND r.is_deleted=false),`
    + `(SELECT COUNT(*) FROM partner_order_audit_logs a WHERE a.entity_id=o.id AND a.is_deleted=false),`
    + `(SELECT COUNT(*) FROM slip_publish_outbox x WHERE x.partner_order_id=o.id AND x.is_deleted=false),`
    + `(SELECT COUNT(*) FROM partner_order_lines l WHERE l.partner_order_id=o.id AND l.is_deleted=false),`
    + `(SELECT COUNT(*) FROM partner_order_lines l WHERE l.partner_order_id=o.id AND l.is_deleted=true),`
    + `md5(concat_ws('|',o.status,o.is_deleted,o.memo,o.due_date,o.total_amount,o.modified_at,o.lock_version)) `
    + `FROM partner_orders o WHERE o.order_no='${orderNo.replaceAll("'", "''")}';`
  const output = execFileSync('docker', [
    'exec', 'samhan-postgres', 'psql', '-U', 'samhan', '-d', 'partner_order_db', '-At', '-F', '|', '-c', sql,
  ], { encoding: 'utf8' }).trim()
  const [revisionCount, revisionRows, maxRevisionNo, auditRows, outboxRows, activeLines, deletedLines, headerFingerprint] = output.split('|')
  return {
    revisionCount: Number(revisionCount), revisionRows: Number(revisionRows), maxRevisionNo: Number(maxRevisionNo),
    auditRows: Number(auditRows), outboxRows: Number(outboxRows), activeLines: Number(activeLines),
    deletedLines: Number(deletedLines), headerFingerprint: headerFingerprint ?? '',
  }
}

async function holdOrderRow(orderNo: string): Promise<ChildProcessWithoutNullStreams> {
  const sql = `BEGIN; SELECT order_no FROM partner_orders WHERE order_no='${orderNo.replaceAll("'", "''")}' FOR UPDATE; SELECT pg_sleep(12); ROLLBACK;`
  const proc = spawn('docker', [
    'exec', 'samhan-postgres', 'psql', '-U', 'samhan', '-d', 'partner_order_db', '-v', 'ON_ERROR_STOP=1', '-At', '-c', sql,
  ], { stdio: 'pipe' })
  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(resolve, 1_500)
    proc.once('error', (error) => { clearTimeout(timer); reject(error) })
    proc.stdout.once('data', () => { clearTimeout(timer); resolve() })
  })
  return proc
}

async function waitForExit(proc: ChildProcessWithoutNullStreams): Promise<void> {
  if (proc.exitCode !== null) return
  await Promise.race([
    new Promise<void>((resolve) => proc.once('exit', () => resolve())),
    new Promise<void>((resolve) => setTimeout(resolve, 15_000)),
  ])
}

async function saveMemo(page: Page, value: string): Promise<Response> {
  await page.getByTestId('partner-order-collab-edit-open').click()
  await page.getByLabel('요청사항 수정값').fill(value)
  const responsePromise = page.waitForResponse((response) =>
    response.request().method() === 'POST' && response.url().includes('/collab/edits'), { timeout: 20_000 })
  await page.getByRole('button', { name: '수정완료' }).click()
  const response = await responsePromise
  await expect(page.getByText('수정완료되었습니다.')).toBeVisible({ timeout: 20_000 })
  return response
}

test('core: PR #1115 S12 복원·잠금·오류 GUI 적대 검증', async ({ browser }) => {
  const masterToken = await login('dev_master')
  const dispatchToken = await login('dev_dispatch')
  evidence.environment = {
    frontendWorktree: 'C:/dev/Samhan-Public/.claude/worktrees/t1110',
    frontend: BASE_URL,
    gateway: GATEWAY_URL,
    partnerOrderDirectPort: 18088,
    mockMode: false,
  }

  {
    // 발화 1: 혼자 쓰는 정상 복원 + 즉시 재복원. 6월 구 snapshot 두 건을 함께 검증한다.
    const normalContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const normalPage = await prepare(normalContext, 'normal-A', masterToken, ORDER_A.pathId)
    const solo = await restoreByClick(normalPage, 1)
    await normalPage.screenshot({ path: path.join(SHOT_DIR, '01-solo-legacy-restore.png'), fullPage: true })
    const immediateRepeat = await restoreByClick(normalPage, 1)
    evidence.triggerCounts.soloRestore = 1
    evidence.triggerCounts.immediateRepeatRestore = 1
    evidence.scenarios.soloAndRepeat = { solo, immediateRepeat }

    // 발화 2: 서로 다른 주문 두 건을 실제 GUI에서 동시에 복원한다.
    const contextA = await browser.newContext({ viewport: { width: 1280, height: 900 } })
    const contextB = await browser.newContext({ viewport: { width: 1280, height: 900 } })
    const pageA = await prepare(contextA, 'different-A', masterToken, ORDER_A.pathId)
    const pageB = await prepare(contextB, 'different-B', masterToken, ORDER_B.pathId)
    await Promise.all([openHistory(pageA), openHistory(pageB)])
    await Promise.all([
      pageA.getByTestId('partner-order-version-history-restore-button-1').click(),
      pageB.getByTestId('partner-order-version-history-restore-button-1').click(),
    ])
    const responseA = pageA.waitForResponse((r) => r.request().method() === 'POST' && r.url().includes('/revisions/1/restore'))
    const responseB = pageB.waitForResponse((r) => r.request().method() === 'POST' && r.url().includes('/revisions/1/restore'))
    await Promise.all([
      pageA.getByTestId('partner-order-version-history-restore-confirm').click(),
      pageB.getByTestId('partner-order-version-history-restore-confirm').click(),
    ])
    const differentStatuses = await Promise.all([responseA, responseB]).then((rows) => rows.map((row) => row.status()))
    await Promise.all([
      expect(pageA.getByTestId('partner-order-version-history-toast')).toBeVisible(),
      expect(pageB.getByTestId('partner-order-version-history-toast')).toBeVisible(),
    ])
    await pageB.screenshot({ path: path.join(SHOT_DIR, '02-different-order-concurrent-restore.png'), fullPage: true })
    evidence.triggerCounts.differentOrderConcurrentRequests = 2
    evidence.scenarios.differentOrderConcurrent = { statuses: differentStatuses }
    await Promise.all([contextA.close(), contextB.close()])

    // 발화 3: SELECT FOR UPDATE 로 실 row 경합을 만들고 GUI 복원 실패 전후 DB 쓰기 수를 비교한다.
    const beforeLockFailure = dbCounts(ORDER_A.orderNo)
    const lockProc = await holdOrderRow(ORDER_A.orderNo)
    const lockFailure = await restoreByClick(normalPage, 1)
    await normalPage.screenshot({ path: path.join(SHOT_DIR, '03a-row-lock-409-obscured.png'), fullPage: true })
    await normalPage.getByRole('dialog', { name: '버전 이력' }).getByRole('button', { name: '닫기' }).click()
    await normalPage.getByTestId('partner-order-version-history-toast')
      .screenshot({ path: path.join(SHOT_DIR, '03b-row-lock-409-after-close.png') })
    await waitForExit(lockProc)
    const afterLockFailure = dbCounts(ORDER_A.orderNo)
    evidence.triggerCounts.rowLockContention = 1
    evidence.scenarios.lockFailure = { response: lockFailure, before: beforeLockFailure, after: afterLockFailure }
    await normalContext.close()

    // 발화 4: 권한이 바뀐 장기 열린 화면. 버튼은 남아 있지만 실제 요청 토큰은 DISPATCH로 바뀐다.
    const permissionContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const permissionPage = await prepare(permissionContext, 'permission-stale', masterToken, ORDER_B.pathId)
    await openHistory(permissionPage)
    await permissionPage.evaluate(({ token }) => {
      const qaWindow = window as typeof window & { __qaAuth?: { token: string; role: string; displayName: string } }
      qaWindow.__qaAuth = { token, role: 'DISPATCH', displayName: '[DEV-SEED] 개발배차' }
    }, { token: dispatchToken })
    const forbidden = await restoreByClick(permissionPage, 1)
    await permissionPage.getByTestId('partner-order-version-history-toast')
      .screenshot({ path: path.join(SHOT_DIR, '04-forbidden-generic-message.png') })
    evidence.triggerCounts.stalePermissionRestore = 1
    evidence.scenarios.forbidden = forbidden
    evidence.triggerCounts.deduperWindowOverflow = 0
    evidence.triggerCounts.reachableNotFoundRestore = 0

    fs.writeFileSync(path.join(SHOT_DIR, 'evidence.json'), JSON.stringify(evidence, null, 2), 'utf8')

    expect(solo.status).toBe(200)
    expect(immediateRepeat.status).toBe(200)
    expect(differentStatuses).toEqual([200, 200])
    expect(lockFailure.status).toBe(409)
    expect(afterLockFailure).toEqual(beforeLockFailure)
    expect(forbidden.status).toBe(403)
  }
})

test('reconnect: 끊긴 동안 발생한 권위 커밋이 재접속 후 수렴하는가', async ({ browser }) => {
  const debugPath = path.join(SHOT_DIR, 'reconnect-debug.log')
  const checkpoint = (value: string) => fs.appendFileSync(debugPath, `${new Date().toISOString()} ${value}\n`, 'utf8')
  fs.writeFileSync(debugPath, '', 'utf8')
  checkpoint('start')
  const masterToken = await login('dev_master')
  checkpoint('login-ok')
  const reconnectA = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const reconnectPageA = await prepare(reconnectA, 'reconnect-A', masterToken, ORDER_A.pathId)
  checkpoint('page-prepared')
  const memoDisplay = reconnectPageA.getByLabel('요청사항')
  const beforeValue = await memoDisplay.count() > 0 ? await memoDisplay.inputValue() : ''
  await reconnectA.setOffline(true)
  await reconnectPageA.waitForTimeout(800)
  checkpoint('offline')
  const savedWhileOffline = `S12-오프라인중-권위저장-${Date.now()}`
  const saveResponse = await fetch(`${GATEWAY_URL}/api/v1/partner-orders/${ORDER_A.pathId}/collab/edits`, {
    method: 'POST',
    headers: { authorization: `Bearer ${masterToken}`, 'content-type': 'application/json' },
    signal: AbortSignal.timeout(10_000),
    body: JSON.stringify({
      changeSet: JSON.stringify({ memo: { before: beforeValue || null, after: savedWhileOffline } }),
      reason: 'S12 재접속 누락 사건 실측',
    }),
  })
  checkpoint(`save-response-${saveResponse.status}`)
  await reconnectA.setOffline(false)
  checkpoint('online')
  await reconnectPageA.waitForTimeout(7_500)
  checkpoint('reconnect-wait-finished')
  const afterReconnectValue = await memoDisplay.count() > 0 ? await memoDisplay.inputValue() : ''
  const reconnectEvidence = {
    triggerCount: 1,
    saveStatus: saveResponse.status,
    beforeValue,
    expectedValue: savedWhileOffline,
    afterReconnectValue,
    converged: afterReconnectValue === savedWhileOffline,
    reconnectNetwork: evidence.network.filter((row) => row.page === 'reconnect-A'),
  }
  await reconnectPageA.screenshot({ path: path.join(SHOT_DIR, '05-reconnect-missed-authority.png'), fullPage: true })
  fs.writeFileSync(path.join(SHOT_DIR, 'reconnect-evidence.json'), JSON.stringify(reconnectEvidence, null, 2), 'utf8')
  checkpoint(`evidence-written-converged-${reconnectEvidence.converged}`)
  expect(reconnectEvidence.converged).toBe(true)
})
