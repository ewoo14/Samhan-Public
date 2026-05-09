/**
 * 홈택스 일괄 등록 양식 export API — PR-E2 FE-9.
 *
 * <p>BE 출처: {@code services/accounting-service} commit c48e156 —
 * AccountingReportController#hometaxExport (BE-A11).
 *
 * <p>endpoint:
 * <ul>
 *   <li>{@code GET /accounting/tax-invoice/hometax-export?from=&to=}
 *       — binary xlsx (Content-Type
 *       {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet})</li>
 * </ul>
 *
 * <p>BE 동작:
 * <ul>
 *   <li>기간 ISSUED 세금계산서 → 홈택스 표준 컬럼 xlsx</li>
 *   <li>100건 초과 시 sheet 분할 (POI 5.2.5)</li>
 *   <li>응답 filename (Content-Disposition) — {@code hometax-export_YYYYMMDD_YYYYMMDD.xlsx}</li>
 * </ul>
 *
 * <p>FE 변환: 한국어 파일명 {@code 홈택스_일괄등록_YYYY-MM-DD_YYYY-MM-DD.xlsx} 으로
 * 사용자 다운로드 트리거 (BE filename 무시).
 *
 * <p>권한: ACCOUNTANT / MASTER 만 — RoleGuard 가 라우팅 단계에서 차단 (BE
 * {@code @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")} 일치).
 */
import { apiClient } from './client'

/**
 * 홈택스 일괄 등록 양식 .xlsx 다운로드.
 *
 * @param from ISO 날짜 (YYYY-MM-DD) — supplyDate 범위 시작
 * @param to   ISO 날짜 (YYYY-MM-DD) — supplyDate 범위 종료
 * @return     binary .xlsx Blob (Content-Type
 *             {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet})
 */
export async function downloadHometaxExport(
  from: string,
  to: string,
): Promise<Blob> {
  const res = await apiClient.get<Blob>(
    '/accounting/tax-invoice/hometax-export',
    {
      params: { from, to },
      responseType: 'blob',
      timeout: 60_000,
    },
  )
  return res.data
}

/**
 * 한국어 파일명 빌더 — `홈택스_일괄등록_YYYY-MM-DD_YYYY-MM-DD.xlsx`.
 *
 * <p>피드백 — 한국어 파일명 의무 (사용자 노출 파일명).
 */
export function buildHometaxExportFilename(from: string, to: string): string {
  return `홈택스_일괄등록_${from}_${to}.xlsx`
}

// ---------------------------------------------------------------------------
// 권한 헬퍼 (BE @PreAuthorize 와 일치 — feedback_role_naming_full.md 풀네임)
// ---------------------------------------------------------------------------

/** 홈택스 일괄 양식 화면 진입 — ACCOUNTANT / MANAGER / MASTER. */
export function canAccessHometaxExport(
  role: string | undefined | null,
): boolean {
  return role === 'ACCOUNTANT' || role === 'MANAGER' || role === 'MASTER'
}

/** 홈택스 일괄 양식 화면 진입 가능 ROLE 풀네임 화이트리스트 — RoleGuard prop 용. */
export const HOMETAX_EXPORT_ROLES = [
  'ACCOUNTANT',
  'MANAGER',
  'MASTER',
] as const
