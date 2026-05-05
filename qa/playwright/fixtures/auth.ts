import { Page } from '@playwright/test';
import partners from './partners.json' with { type: 'json' };

export type PartnerFixture = (typeof partners.partners)[number];

/**
 * 거래처 fixture 조회 helper.
 * status (ACTIVE / BLOCKED / EXPIRED / TEMP_PASSWORD) 필터.
 */
export function getPartner(filter: Partial<PartnerFixture>): PartnerFixture {
  const found = partners.partners.find((p) =>
    Object.entries(filter).every(([k, v]) => (p as Record<string, unknown>)[k] === v),
  );
  if (!found) {
    throw new Error(`partner fixture not found: ${JSON.stringify(filter)}`);
  }
  return found;
}

/**
 * JWT mock helper — backend 미가동 시 cookie/sessionStorage 직접 주입.
 * 실 backend 가동 시 partner-auth-service /api/auth/login 호출로 대체.
 */
export async function mockPartnerAuth(page: Page, partner: PartnerFixture): Promise<void> {
  const fakeJwt = [
    'eyJhbGciOiJIUzI1NiJ9',
    Buffer.from(
      JSON.stringify({
        sub: partner.id,
        partnerCode: partner.code,
        partnerName: partner.name,
        status: partner.status,
        exp: Math.floor(Date.now() / 1000) + 3600,
      }),
    ).toString('base64url'),
    'mock-signature',
  ].join('.');

  await page.addInitScript(([jwt, p]) => {
    window.sessionStorage.setItem('samhan.partner.jwt', jwt as string);
    window.sessionStorage.setItem(
      'samhan.partner.profile',
      JSON.stringify(p as Record<string, unknown>),
    );
  }, [fakeJwt, partner] as const);
}

/**
 * 백엔드 가동 여부 확인 (skip 가드 용).
 */
export async function isBackendAvailable(apiBase: string): Promise<boolean> {
  try {
    const res = await fetch(`${apiBase}/actuator/health`, { signal: AbortSignal.timeout(2_000) });
    return res.ok;
  } catch {
    return false;
  }
}
