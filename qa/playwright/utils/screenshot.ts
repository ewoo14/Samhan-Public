import { Page, TestInfo } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';

/**
 * 스크린샷 캡처 helper.
 * docs/qa/<slice>/*.png 규칙 (PR QA 첨부 의무).
 *
 * 사용 예: await captureForQa(page, testInfo, 'auth/partner-bizgate-happy');
 */
export async function captureForQa(
  page: Page,
  testInfo: TestInfo,
  slug: string,
): Promise<string> {
  const repoRoot = process.env.QA_REPO_ROOT ?? join(process.cwd(), '..', '..');
  const target = join(repoRoot, 'docs', 'qa', 'phase7-e2e', `${slug}.png`);
  await mkdir(dirname(target), { recursive: true });
  await page.screenshot({ path: target, fullPage: true });
  await testInfo.attach(slug, { path: target, contentType: 'image/png' });
  return target;
}
