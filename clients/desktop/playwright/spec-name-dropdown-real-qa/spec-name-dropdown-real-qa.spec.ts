/**
 * 사양명 드롭박스 (사양 후속 #1) Docker 실서버 QA Playwright spec.
 *
 * 대상: 품목 등록 폼 사양 입력 행의 `사양명` 입력에 spec-key-templates 제안 드롭다운(datalist)
 *       + 자유입력(미수록 커스텀 사양명) 보존.
 * 실서버: http://localhost:8080 (api-gateway, 실 product-service), FE http://localhost:5173 (electron-vite renderer).
 * 인증: dev_master / dev_p05_pass! (MASTER, products.list VIEW).
 *
 * 실행:
 *   cd C:\dev\Samhan-Public\clients\desktop
 *   set AUDIT_BASE_URL=http://localhost:5173 && node_modules\.bin\playwright test \
 *     --config=playwright.real-qa.config.ts playwright/spec-name-dropdown-real-qa --reporter=line --timeout=60000
 */
import { test, expect, type Page } from '@playwright/test'
import * as path from 'path'
import * as fs from 'fs'
import { fileURLToPath } from 'url'

const _dirname = typeof __dirname !== 'undefined'
  ? __dirname
  : path.dirname(fileURLToPath(import.meta.url))

const BASE_URL = process.env['AUDIT_BASE_URL'] ?? 'http://localhost:5173'
const API_BASE = 'http://localhost:8080'

const SCREENSHOTS_DIR = path.resolve(_dirname, '../../../../docs/qa/spec-name-dropdown')
fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true })

async function loginAndInstallStub(page: Page, loginId: string, password: string): Promise<void> {
  const res = await page.request.post(`${API_BASE}/auth/login`, { data: { loginId, password } })
  const body = await res.json()
  const token: string = body.data?.token ?? ''
  const role: string = body.data?.role ?? 'MASTER'
  const userId: string = body.data?.userId ?? ''
  const displayName: string = body.data?.displayName ?? loginId
  await page.addInitScript(({ tok, r, uid, name }: { tok: string; r: string; uid: string; name: string }) => {
    Object.defineProperty(window, 'samhanAuth', {
      configurable: true,
      value: {
        getToken: async () => ({ token: tok, userId: uid, role: r, fullName: name, partnerCode: null }),
        setToken: async () => undefined,
        clearToken: async () => undefined,
      },
    })
  }, { tok: token, r: role, uid: userId, name: displayName })
}

test('사양명 드롭박스 — 실 spec-key-templates 제안 + 자유입력 보존', async ({ page }) => {
  await loginAndInstallStub(page, 'dev_master', 'dev_p05_pass!')

  await page.goto(`${BASE_URL}/#/products/new`)
  await page.waitForSelector('[data-testid="product-form-model-name"]', { timeout: 30000 })

  // 사양 추가 → 행 1개
  await page.click('[data-testid="product-form-add-spec"]')
  await page.waitForSelector('[data-testid="product-form-spec-0-key"]', { timeout: 10000 })

  // datalist 실 옵션(= 라이브 spec-key-templates) 수집 — DOM 진실
  const options = await page.$$eval(
    '[data-testid="product-form-spec-key-datalist"] option',
    (els) => els.map((e) => (e as HTMLOptionElement).value).filter(Boolean),
  )
  // 라이브 제안이 비어있지 않고, V4 시드 핵심 키가 포함되어야 함
  expect(options.length).toBeGreaterThan(0)
  const hasSeedKey = options.some((o) => ['배관경', '냉매가스', '냉방성능(kW)', '소비전력'].some((k) => o.includes(k.replace(/\(.*\)/, ''))))
  expect(hasSeedKey).toBeTruthy()

  // 사양명 입력에 list 연결 확인
  const listAttr = await page.getAttribute('[data-testid="product-form-spec-0-key"]', 'list')
  expect(listAttr).toBe('product-form-spec-key-options')

  // (1) 제안 키 선택 시뮬레이션 — 실 옵션 값 입력
  const pick = options[0]
  await page.fill('[data-testid="product-form-spec-0-key"]', pick)
  await page.fill('[data-testid="product-form-spec-0-value"]', '6.0kW')
  await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '01-suggested-key.png'), fullPage: true })

  // (2) 자유입력 보존 — 템플릿 미수록 커스텀 사양명
  const custom = '커스텀특수사양-현장협의'
  await page.fill('[data-testid="product-form-spec-0-key"]', custom)
  const preserved = await page.inputValue('[data-testid="product-form-spec-0-key"]')
  expect(preserved).toBe(custom)
  await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '02-free-input-preserved.png'), fullPage: true })

  // 실 옵션 덤프(증거)
  fs.writeFileSync(
    path.join(SCREENSHOTS_DIR, 'spec-key-options.txt'),
    `실 spec-key-templates 제안(라이브 :8080, datalist DOM 옵션 ${options.length}개)\n`
    + `list 연결=${listAttr}\n선택 시뮬레이션 키="${pick}"\n자유입력 보존="${preserved}" (커스텀, 템플릿 미수록)\n\n`
    + `--- 옵션 전체 ---\n${options.join('\n')}\n`,
    'utf8',
  )
})
