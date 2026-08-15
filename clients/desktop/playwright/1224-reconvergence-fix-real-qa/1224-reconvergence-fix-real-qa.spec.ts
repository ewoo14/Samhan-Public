import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'
import { resolveQaShotsDir } from '../support/qa-screenshot-dir'

const require = createRequire(import.meta.url)
const { resolveQaCredential } = require('../../../../scripts/lib/qa-credentials.cjs') as {
  resolveQaCredential: (name: string) => string
}
const here = path.dirname(fileURLToPath(import.meta.url))
const committedShotsDir = path.resolve(here, '../../../../docs/qa/1224-chatroom-link-reconvergence-fix-real-qa')
const shots = resolveQaShotsDir(committedShotsDir)
const apiBase = process.env['QA_API_BASE'] ?? 'http://127.0.0.1:8080'

type Login = { token: string; userId?: string; role?: string; displayName?: string }

async function login(request: import('@playwright/test').APIRequestContext): Promise<Login> {
  const response = await request.post(`${apiBase}/auth/login`, {
    data: { loginId: 'dev_master', password: resolveQaCredential('QA_DEV_DEFAULT_PASSWORD') },
  })
  expect(response.ok(), `로그인 HTTP ${response.status()}`).toBeTruthy()
  return JSON.parse(await response.text()).data as Login
}

async function installAuth(page: import('@playwright/test').Page, session: Login): Promise<void> {
  await page.addInitScript(({ token, userId, role, displayName }) => {
    Object.defineProperty(window, 'samhanAuth', {
      configurable: true,
      value: {
        getToken: async () => ({ token, userId, role, fullName: displayName, partnerCode: null }),
        setToken: async () => undefined,
        clearToken: async () => undefined,
      },
    })
  }, session)
}

test('단톡방 연결 상태가 화면에서 연결·모호·미매칭으로 구분되고 alias가 숨겨진다', async ({ page }) => {
  await fs.mkdir(shots, { recursive: true })
  const session = await login(page.request)
  await installAuth(page, session)
  const writes: string[] = []
  page.on('request', (request) => {
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method()) && !request.url().endsWith('/auth/login')) {
      writes.push(`${request.method()} ${request.url()}`)
    }
  })

  await page.goto('/#/admin/chat-rooms', { waitUntil: 'domcontentloaded' })
  await expect(page.getByTestId('admin-chatrooms-table')).toBeVisible()
  await expect(page.getByText('연결 상태', { exact: true })).toBeVisible()
  await expect(page.getByText('연결', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('미연결', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('모호 · 후보 여러 건', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('미매칭 · 후보 없음', { exact: true }).first()).toBeVisible()
  await expect(page.getByText(/LEGACY-NAME-/)).toHaveCount(0)
  await expect(page.getByTestId('admin-chatrooms-row')).toHaveCount(112)
  await expect(page.getByTestId('admin-chatrooms-row').filter({ hasText: '미연결' })).toHaveCount(6)

  await page.screenshot({ path: path.join(shots, '01-unlinked-statuses-real-qa.png'), fullPage: true })
  await page.getByTestId('admin-chatrooms-search-input').fill('1117100334')
  await page.getByRole('button', { name: '검색', exact: true }).click()
  await expect(page.getByTestId('admin-chatrooms-row')).toHaveCount(2)
  await expect(page.getByText('1117100334', { exact: true }).first()).toBeVisible()
  await page.screenshot({ path: path.join(shots, '02-connected-list-real-qa.png'), fullPage: true })

  await page.setViewportSize({ width: 1024, height: 900 })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await expect(page.getByTestId('admin-chatrooms-row')).toHaveCount(112)
  const clipping = await page.locator('[data-testid="admin-chatrooms-table"] td').evaluateAll((cells) => cells.map((cell) => {
    const el = cell as HTMLElement
    const style = getComputedStyle(el)
    return { text: el.innerText, clipped: el.scrollWidth > el.clientWidth + 1, whiteSpace: style.whiteSpace, overflowWrap: style.overflowWrap }
  }).filter((probe) => probe.clipped))
  expect(clipping, `잘린 셀: ${JSON.stringify(clipping)}`).toEqual([])
  await page.screenshot({ path: path.join(shots, '03-1024-no-clipping-real-qa.png'), fullPage: true })

  expect(writes, '조회 QA 중 쓰기 요청이 발생하지 않아야 함').toEqual([])
})
