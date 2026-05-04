/**
 * 전표 도메인 API 클라이언트 (출고/입고 한정 — 첫 슬라이스 범위).
 *
 * 노출 endpoint (슬라이스 범위):
 * - `GET  /slips`        — Page<SlipSummary> 페이지 조회 (필터 옵션)
 * - `POST /slips`        — 신규 전표 생성 (DRAFT)
 *
 * 라이프사이클 transition (`/save`, `/send`, ... `/confirm`) 는 본 슬라이스에서
 * 호출하지 않으나, 후속 슬라이스 확장을 위해 endpoint URL 패턴은
 * `services/slip-service/.../web/SlipController.java` 와 일치시킨다.
 */
import {
  apiClient,
  type ApiEnvelope,
  type PageResponse,
} from './client'
import type { SlipStatus } from '@samhan/design-system'
import type { DeliveryTagCode } from '@samhan/design-system'

/** 본 슬라이스 범위 — 출고/입고 2종. */
export type SlipType = 'OUTBOUND' | 'INBOUND'

/** 목록용 요약 응답 — BE `SlipResponse`. */
export interface SlipSummary {
  id: string
  slipType: SlipType
  slipNo: string
  slipDate: string
  seqNo: number
  status: SlipStatus
  partnerId: string | null
  partnerName: string | null
  sourceWarehouseId: string | null
  destinationWarehouseId: string | null
  deliveryTag: DeliveryTagCode | null
  requesterId: string | null
  acceptedBy: string | null
  acceptedAt: string | null
  completedAt: string | null
  confirmedAt: string | null
  version: number
}

/** 라인 input — BE `CreateSlipRequest.SlipLineRequest`. */
export interface SlipLineInput {
  productId: string
  productName?: string
  modelName?: string
  quantity: number
  unitPrice: string
  note?: string
}

/** 신규 전표 생성 요청 body — BE `CreateSlipRequest`. */
export interface CreateSlipRequest {
  slipType: SlipType
  slipDate?: string
  sourceWarehouseId?: string
  destinationWarehouseId?: string
  partnerId?: string
  partnerName?: string
  deliveryTag?: DeliveryTagCode
  memo?: string
  lines: SlipLineInput[]
}

/** 페이지 조회 옵션 — slipType / status 필터, 0-based page. */
export interface ListSlipsOptions {
  slipType?: SlipType
  status?: SlipStatus
  page?: number
  size?: number
}

/**
 * 전표 페이지 조회. 빈 필터 시 전체.
 *
 * @return Spring `Page<SlipResponse>` 형태
 */
export async function listSlips(
  options: ListSlipsOptions = {},
): Promise<PageResponse<SlipSummary>> {
  const params: Record<string, string | number> = {
    page: options.page ?? 0,
    size: options.size ?? 20,
  }
  if (options.slipType) params['slipType'] = options.slipType
  if (options.status) params['status'] = options.status

  const res = await apiClient.get<ApiEnvelope<PageResponse<SlipSummary>>>(
    '/slips',
    { params },
  )
  return res.data.data
}

/**
 * 신규 전표 생성. 응답은 라인 포함 상세 (`SlipDetailResponse`) 지만
 * 본 슬라이스 LoginPage/SlipFormPage 흐름에서는 id/status 만 사용한다.
 *
 * @return 생성된 전표 (status=DRAFT)
 */
export async function createSlip(
  body: CreateSlipRequest,
): Promise<SlipSummary> {
  const res = await apiClient.post<ApiEnvelope<SlipSummary>>('/slips', body)
  return res.data.data
}
