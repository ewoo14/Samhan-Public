/**
 * 재고 도메인 (창고) API 클라이언트.
 *
 * 본 슬라이스 범위:
 * - `GET  /inventory/warehouses` — 활성 창고 목록 (displayOrder ASC)
 * - `POST /inventory/warehouses` — 신규 창고 등록 (MASTER/MANAGER/DEVELOPER)
 *
 * `WarehouseType` 은 디자인 시스템 `WarehouseSelector` 의 enum 과 1:1.
 */
import { apiClient, type ApiEnvelope } from './client'

/** BE `WarehouseType` enum 과 1:1. */
export type WarehouseType =
  | 'HEADQUARTERS'
  | 'VEHICLE'
  | 'CONSIGNMENT'
  | 'VIRTUAL'

/**
 * 창고 응답 — BE `WarehouseResponse` 의 핵심 필드.
 * `active` 는 BE 응답에 직접 포함되지 않지만, soft-delete 미삭제 행만
 * 반환되므로 렌더러에서는 항상 `true` 로 가정한다.
 */
export interface Warehouse {
  id: string
  code: string
  name: string
  type: WarehouseType
  address: string | null
  displayOrder: number
  description: string | null
  createdAt: string
  modifiedAt: string
  /** 디자인 시스템 `WarehouseSelector` 호환을 위한 가상 필드 (항상 true). */
  active: boolean
}

/** 창고 신규 등록 요청 body — BE `CreateWarehouseRequest`. */
export interface CreateWarehouseRequest {
  code: string
  name: string
  type: WarehouseType
  address?: string
  displayOrder?: number
  description?: string
}

/**
 * 활성 창고 전체 조회. displayOrder ASC.
 *
 * @return Warehouse[] — `active: true` 가 강제 주입된다.
 */
export async function listWarehouses(): Promise<Warehouse[]> {
  const res = await apiClient.get<ApiEnvelope<Warehouse[]>>(
    '/inventory/warehouses',
  )
  return res.data.data.map((w) => ({ ...w, active: true }))
}

/**
 * 신규 창고 생성. 권한 부족 시 403, code 중복 시 409.
 *
 * @param body 신규 창고 정의
 * @return 생성된 Warehouse
 */
export async function createWarehouse(
  body: CreateWarehouseRequest,
): Promise<Warehouse> {
  const res = await apiClient.post<ApiEnvelope<Warehouse>>(
    '/inventory/warehouses',
    body,
  )
  return { ...res.data.data, active: true }
}
