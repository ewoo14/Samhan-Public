/**
 * 매출 마감 API 클라이언트 — accounting-service `/accounting/closings/*`.
 *
 * P2-4 매출 마감 (Phase 10 Step 8) — slice 8.
 * 매뉴얼 출처: `docs/manual/02-창고/04-매출-마감.md`.
 *
 * 노출 endpoint:
 * - `POST /accounting/closings`            — 마감 실행 (DAILY/MONTHLY, ACCOUNTANT/MASTER)
 * - `GET  /accounting/closings`            — 마감 list (periodType / year filter, ACCOUNTANT/MASTER)
 * - `POST /accounting/closings/{id}/reverse` — 역마감 (MASTER 만)
 *
 * BE Java 와의 매핑 (`AccountingPeriodResponse` record):
 * - `periodType`: DAILY / MONTHLY
 * - `status`:     OPEN / CLOSED
 * - `periodDate`: LocalDate (DAILY=해당일, MONTHLY=해당월 1일)
 * - 금액 필드 (`totalSales`/`totalPurchase`/`totalExpense`): BigDecimal → string 직렬화
 *
 * UUID 비공개 가드:
 * - 마감 row 의 `id` 는 reverse path param 으로만 사용. 화면 표시 X.
 * - 화면에 노출되는 식별자는 `periodType` + `periodDate` 조합 (예: "월별 2026-05-01").
 */
import { apiClient, type ApiEnvelope } from './client'

/** 마감 기간 유형 — BE `PeriodType`. */
export type PeriodType = 'DAILY' | 'MONTHLY'

/** 마감 상태 — BE `PeriodStatus`. */
export type PeriodStatus = 'OPEN' | 'CLOSED'

/**
 * 마감 단건 응답 — BE `AccountingPeriodResponse` record.
 *
 * BigDecimal 직렬화는 Spring 기본 ObjectMapper 가 string 으로 처리.
 */
export interface AccountingPeriod {
  /** UUID — reverse 액션 path 용, 화면 미노출. */
  id: string
  /** 기간 유형. */
  periodType: PeriodType
  /** 기간 일자 (LocalDate "YYYY-MM-DD"). MONTHLY 는 1일로 normalize. */
  periodDate: string
  /** 마감 상태. */
  status: PeriodStatus
  /** 마감 시각 ISO 8601 — OPEN(역마감 후) 은 null. */
  closedAt: string | null
  /** 마감 실행자 (X-User-Id 또는 "system"). */
  closedBy: string | null
  /** 역마감 시각 — CLOSED 는 null. */
  reversedAt: string | null
  /** 역마감 실행자. */
  reversedBy: string | null
  /** 매출 합계 (KRW BigDecimal — string). */
  totalSales: string
  /** 매입 합계. */
  totalPurchase: string
  /** 판관비 합계. */
  totalExpense: string
  /** lock 처리된 슬립 건수 — slip-service.lock-by-period 응답 합계. */
  lockedSlipCount: number
  /** 마감 사유/메모 (≤500자). */
  description: string | null
}

/** 마감 실행 요청 — BE `CreateClosingRequest`. */
export interface CreateClosingRequest {
  /** 마감 유형 — DAILY / MONTHLY. */
  periodType: PeriodType
  /** 기간 일자 ("YYYY-MM-DD"). MONTHLY 는 service 가 1일로 normalize. */
  periodDate: string
  /** 마감 사유/메모 (옵션, ≤500자). */
  description?: string
}

/** 마감 list 옵션 — periodType / year. */
export interface ListClosingsOptions {
  periodType?: PeriodType
  year?: number
}

/**
 * 마감 list 조회.
 *
 * @return 마감 row 목록 (BE 가 createdAt DESC 정렬을 보장).
 */
export async function listClosings(
  options: ListClosingsOptions = {},
): Promise<AccountingPeriod[]> {
  const params: Record<string, string | number> = {}
  if (options.periodType) params['periodType'] = options.periodType
  if (options.year) params['year'] = options.year
  const res = await apiClient.get<ApiEnvelope<AccountingPeriod[]>>(
    '/accounting/closings',
    { params },
  )
  return res.data.data
}

/**
 * 마감 실행 — DAILY 또는 MONTHLY.
 *
 * BE 동작:
 * 1) slip-service.lock-by-period 호출 → 해당 기간 CONFIRMED 슬립 일괄 LOCKED
 * 2) accounting-service 가 매출/매입/판관비 합계 stamp + status=CLOSED
 * 3) 이후 분개 / 슬립 변경은 `AccountingPeriodGuard` 가 차단
 *
 * @return 신규 생성된 AccountingPeriod (status=CLOSED).
 */
export async function createClosing(
  body: CreateClosingRequest,
): Promise<AccountingPeriod> {
  const res = await apiClient.post<ApiEnvelope<AccountingPeriod>>(
    '/accounting/closings',
    body,
  )
  return res.data.data
}

/**
 * 역마감 — CLOSED → OPEN. MASTER 권한만 (BE 가 403 가드).
 *
 * @param id 마감 UUID (path param, 화면 미노출).
 * @return 갱신된 AccountingPeriod (status=OPEN, reversedAt/By stamp).
 */
export async function reverseClosing(id: string): Promise<AccountingPeriod> {
  const res = await apiClient.post<ApiEnvelope<AccountingPeriod>>(
    `/accounting/closings/${id}/reverse`,
    {},
  )
  return res.data.data
}

/**
 * 마감 권한 — ACCOUNTANT / MASTER (BE `@PreAuthorize` 와 동일).
 *
 * `feedback_role_naming_full.md` — role 표기 풀네임 의무.
 */
export function canExecuteClosing(role: string | undefined | null): boolean {
  if (!role) return false
  return role === 'ACCOUNTANT' || role === 'MASTER'
}

/**
 * 역마감 권한 — MASTER 만 (BE `@PreAuthorize("hasRole('MASTER')")` 와 동일).
 */
export function canReverseClosing(role: string | undefined | null): boolean {
  if (!role) return false
  return role === 'MASTER'
}

/** 마감 유형 한국어 라벨. */
export const PERIOD_TYPE_LABEL: Record<PeriodType, string> = {
  DAILY: '일별',
  MONTHLY: '월별',
}

/** 마감 상태 한국어 라벨. */
export const PERIOD_STATUS_LABEL: Record<PeriodStatus, string> = {
  OPEN: '열림',
  CLOSED: '마감',
}
