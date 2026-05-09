/**
 * 비밀번호 정책 / 재설정 / 변경 / 잠금 해제 API 클라이언트 — Phase 10 P0-2.
 *
 * BE: {@code services/auth-service/src/main/java/.../web/PasswordController.java} (commit ea9fb88).
 *
 * <p>제공 endpoint (5종):
 * <ul>
 *   <li>{@code GET    /auth/password/policy}                — 정책 조회 (인증 불필요)</li>
 *   <li>{@code POST   /auth/password/reset/request}         — 토큰 발급 + 메일</li>
 *   <li>{@code POST   /auth/password/reset/confirm}         — 토큰으로 비밀번호 교체</li>
 *   <li>{@code POST   /auth/password/change}                — 본인 변경 (인증 필요)</li>
 *   <li>{@code PATCH  /auth/admin/accounts/{id}/unlock}     — MASTER 잠금 해제</li>
 * </ul>
 *
 * <p>매뉴얼 출처: {@code docs/manual/06-트러블슈팅/01-로그인-실패.md} §1-3.
 */
import { apiClient, type ApiEnvelope } from './client'

/** {@code GET /auth/password/policy} 응답 — BE {@code PasswordPolicyResponse} 와 1:1. */
export interface PasswordPolicy {
  /** 최소 길이 (8). */
  minLength: number
  /** 최대 길이 (100). */
  maxLength: number
  /** 영문 1자 이상 필요 여부. */
  requireLetter: boolean
  /** 숫자 1자 이상 필요 여부. */
  requireDigit: boolean
  /** 특수문자 1자 이상 필요 여부. */
  requireSpecial: boolean
  /** 비밀번호 history 재사용 금지 개수 (5). */
  historyReuseBlock: number
  /** 5회 실패 시 잠금까지의 최대 시도 횟수 (5). */
  maxFailedLoginAttempts: number
  /** reset 토큰 유효 시간 (분, 30). */
  resetTokenTtlMinutes: number
  /** 사용자 노출용 한국어 설명 문구. */
  description: string
}

/** {@code POST /auth/password/reset/request} body. */
export interface PasswordResetRequestBody {
  loginId: string
  email: string
}

/** {@code POST /auth/password/reset/confirm} body. */
export interface PasswordResetConfirmBody {
  token: string
  newPassword: string
}

/** {@code POST /auth/password/change} body — 인증된 사용자 본인 변경. */
export interface PasswordChangeBody {
  oldPassword: string
  newPassword: string
}

/**
 * 비밀번호 정책 조회. 신규 비밀번호 입력 폼의 helper text 로 사용.
 * 인증 헤더 불필요 — login 화면에서도 호출 가능.
 *
 * @return 정책 메타 (최소 길이 / 영문/숫자/특수문자 / history / 잠금 / TTL / 설명)
 */
export async function getPasswordPolicy(): Promise<PasswordPolicy> {
  const res = await apiClient.get<ApiEnvelope<PasswordPolicy>>(
    '/auth/password/policy',
  )
  return res.data.data
}

/**
 * 비밀번호 reset 토큰 발급 요청. 사용자 존재 여부와 무관하게 항상 200 OK
 * (enumeration 방지 — BE 가 silent skip).
 *
 * @param body loginId + email
 */
export async function requestPasswordReset(
  body: PasswordResetRequestBody,
): Promise<void> {
  await apiClient.post<ApiEnvelope<void>>(
    '/auth/password/reset/request',
    body,
  )
}

/**
 * 토큰 + 신규 비밀번호 confirm. 토큰 무효 시 401, 정책 위반 시 400.
 *
 * @param body token + newPassword (8자 이상 + 영/숫/특)
 * @throws AxiosError — 401 (토큰 무효/만료) / 400 (정책 위반)
 */
export async function confirmPasswordReset(
  body: PasswordResetConfirmBody,
): Promise<void> {
  await apiClient.post<ApiEnvelope<void>>(
    '/auth/password/reset/confirm',
    body,
  )
}

/**
 * 본인 비밀번호 변경. 현재 비밀번호 + 신규 비밀번호.
 * 5회 history 재사용 금지 — 위반 시 400.
 *
 * @param body oldPassword + newPassword
 * @throws AxiosError — 401 (현재 비번 불일치) / 400 (정책/history 위반)
 */
export async function changePassword(body: PasswordChangeBody): Promise<void> {
  await apiClient.post<ApiEnvelope<void>>('/auth/password/change', body)
}

/**
 * MASTER 권한 계정 잠금 해제 (per-row 관리자 액션).
 * 본 슬라이스에서는 호출 UI 부재 — P0-5 (사용자 관리 화면) 구현 시 사용.
 *
 * @param accountId UUID — 대상 계정
 * @throws AxiosError — 403 (MASTER 아님) / 404 (계정 없음)
 */
export async function unlockAccount(accountId: string): Promise<void> {
  await apiClient.patch<void>(`/auth/admin/accounts/${accountId}/unlock`)
}
