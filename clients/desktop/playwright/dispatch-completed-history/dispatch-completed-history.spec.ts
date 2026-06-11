import { expect, test, type Page } from '@playwright/test'

const BASE_URL = process.env['AUDIT_BASE_URL'] ?? 'http://127.0.0.1:5173'
const UUID_REGEX =
  /\b(?:[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\b/i

type MockPerm = { pageCode: string; view?: boolean; edit?: boolean }

function mockPerms(perms: MockPerm[]): string {
  return btoa(JSON.stringify(perms))
}

async function gotoHistory(
  page: Page,
  role = 'DISPATCH',
  perms?: MockPerm[],
): Promise<void> {
  const suffix = perms
    ? `&mockPerms=${encodeURIComponent(mockPerms(perms))}`
    : ''
  await page.goto(`${BASE_URL}/#/dispatch-board/history?mockRole=${role}${suffix}`, {
    waitUntil: 'domcontentloaded',
    timeout: 20_000,
  })
  await page.waitForLoadState('networkidle', { timeout: 8_000 }).catch(() => {})
}

test.describe('AROLOGIS 완료배차 내역 뷰 mock', () => {
  test('DISPATCHED 완료배차 목록을 렌더한다', async ({ page }) => {
    await gotoHistory(page)

    await expect(page.getByTestId('dispatch-history-page')).toBeVisible()
    await expect(page.getByTestId('dispatch-history-table')).toBeVisible()
    await expect(page.getByTestId('dispatch-history-row-2026/06/11-1')).toBeVisible()
    await expect(page.getByTestId('dispatch-history-row-2026/06/05-2')).toBeVisible()
    await expect(page.getByTestId('dispatch-history-table').getByText('배차 완료').first()).toBeVisible()
  })

  test('상태 필터는 완료(DISPATCHED)만 노출한다 - 대상자는 배차현황 편입', async ({ page }) => {
    await gotoHistory(page)

    const statusSelect = page.getByTestId('dispatch-history-status')
    await expect(statusSelect.locator('option')).toHaveCount(1)
    await expect(statusSelect.locator('option')).toHaveText(['배차 완료'])

    await expect(page.getByTestId('dispatch-history-row-2026/06/11-1')).toBeVisible()
  })

  test('상세 모달은 조회 전용이며 수정/취소 mutation 버튼이 없다', async ({ page }) => {
    await gotoHistory(page)

    await page.getByTestId('dispatch-history-row-2026/06/11-1').click()
    await expect(page.getByTestId('dispatch-task-detail-body')).toBeVisible()

    await expect(page.getByTestId('dispatch-task-detail-request-modification')).toHaveCount(0)
    await expect(page.getByTestId('dispatch-task-detail-request-cancellation')).toHaveCount(0)
    await expect(page.getByRole('button', { name: '수정 요청' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '취소 요청' })).toHaveCount(0)
  })

  test('행 클릭 후 차량그룹, 전표, 기사 상세를 보여준다', async ({ page }) => {
    await gotoHistory(page)

    await page.getByTestId('dispatch-history-row-2026/06/11-1').click()
    const detail = page.getByTestId('dispatch-task-detail-body')
    await expect(detail).toBeVisible()
    await expect(detail).toContainText('1톤 #1')
    await expect(detail).toContainText('2026/06/11-001')
    await expect(detail).toContainText('동탄공조')
    await expect(detail).toContainText('기사 김배차 (DRV-101) 010-9000-1001')
  })

  test('dispatch.board view 없는 역할은 홈으로 redirect 된다', async ({ page }) => {
    await gotoHistory(page, 'DISPATCH', [
      { pageCode: 'dispatch.board', view: false, edit: false },
    ])

    await expect(page.getByTestId('dispatch-history-page')).toHaveCount(0)
    await expect(page).toHaveURL(/#\/$/)
  })

  test('화면 텍스트에 raw UUID가 노출되지 않는다', async ({ page }) => {
    await gotoHistory(page)

    await page.getByTestId('dispatch-history-row-2026/06/11-1').click()
    await expect(page.getByTestId('dispatch-task-detail-body')).toBeVisible()

    const bodyText = await page.locator('body').textContent()
    expect(bodyText ?? '').not.toMatch(UUID_REGEX)
  })
})
