import type {
  BundleComponentInput,
  ComponentKind,
  QtyMode,
} from '../api/productCatalogApi'

/**
 * 구성품 draft — BE 응답 필드 전체 보존 + 로컬 메타.
 * 기존 행: GET 응답의 qtyMode/componentKind/componentVariant/isDefault/specText 유지.
 * 신규 행: componentKind 사용자 선택 가능 (null -> BE 기본 ACCESSORY), 나머지 null.
 */
export interface ComponentDraftModel {
  /** BE BundleComponentItem 필드 전체 */
  componentProductCode: string
  componentName: string
  defaultQty: number
  qtyMode: QtyMode
  componentKind: ComponentKind | null
  componentVariant: string | null
  isDefault: boolean
  specText: string | null
  displayOrder: number
  /** 로컬 임시 ID — 저장 시 제거 */
  _localId: string
  /** 신규 추가 여부 — true: componentKind 셀렉트 노출 */
  _isNew: boolean
}

/** 사용자가 선택한 행의 기본 구성품 여부만 갱신한다. */
export function toggleComponentDefault(
  drafts: ComponentDraftModel[],
  localId: string,
  checked: boolean,
): ComponentDraftModel[] {
  return drafts.map((draft) =>
    draft._localId === localId ? { ...draft, isDefault: checked } : draft,
  )
}

/** BE BundleComponentRequest 1:1 매핑 — 배열 인덱스가 displayOrder. */
export function buildBundleComponentInputs(
  drafts: ComponentDraftModel[],
): BundleComponentInput[] {
  return drafts.map((draft) => ({
    componentProductCode: draft.componentProductCode,
    defaultQty: draft.defaultQty,
    qtyMode: draft.qtyMode ?? undefined,
    componentKind: draft.componentKind ?? undefined,
    componentVariant: draft.componentVariant ?? undefined,
    isDefault: draft.isDefault,
    specText: draft.specText ?? undefined,
  }))
}
