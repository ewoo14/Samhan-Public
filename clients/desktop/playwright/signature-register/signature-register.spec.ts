import { test, expect } from '@playwright/test'

// VITE_MOCK_MODE dev server (webServer) 위에서 실행. UsersPage 진입 = MASTER mock 세션.
const USERS_URL = '/#/admin/users?mockRole=MASTER&mockDepartment=대표실'

test.describe('사원 서명 등록 모달 (C2)', () => {
  test('관리 셀 서명 등록 버튼 → 모달 오픈', async ({ page }) => {
    await page.goto(USERS_URL)
    await page.getByTestId('admin-users-table').waitFor()
    await page.getByTestId('admin-user-signature-button').first().click()
    await expect(page.getByTestId('admin-user-signature-modal')).toBeVisible()
    await expect(page.getByTestId('signature-tab-upload')).toBeVisible()
    await expect(page.getByTestId('signature-tab-mobile')).toBeVisible()
  })

  test('이미지 업로드 → 미리보기 → 등록 성공', async ({ page }) => {
    await page.goto(USERS_URL)
    await page.getByTestId('admin-users-table').waitFor()
    await page.getByTestId('admin-user-signature-button').first().click()
    await page.getByTestId('signature-tab-upload').click()
    const buffer = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64')
    await page.getByTestId('signature-file-input').setInputFiles({ name: 's.png', mimeType: 'image/png', buffer })
    await expect(page.getByTestId('signature-preview')).toBeVisible()
    await page.getByTestId('signature-upload-submit').click()
    await expect(page.getByTestId('signature-upload-done')).toBeVisible()
  })

  test('모바일로 그리기 → QR 발급 + 복사링크 + 폴링 used 감지', async ({ page }) => {
    await page.goto(USERS_URL)
    await page.getByTestId('admin-users-table').waitFor()
    await page.getByTestId('admin-user-signature-button').first().click()
    await page.getByTestId('signature-tab-mobile').click()
    await page.getByTestId('signature-handoff-issue').click()
    await expect(page.getByTestId('signature-qr-image')).toBeVisible()
    await expect(page.getByTestId('signature-copy-link')).toBeVisible()
    await expect(page.getByTestId('signature-mobile-done')).toBeVisible({ timeout: 8000 })
  })
})
