import { arrayMove } from '@dnd-kit/sortable'
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

export interface ComponentDraftGroup {
  kind: ComponentKind
  items: ComponentDraftModel[]
}

export const COMPONENT_KIND_ORDER: ComponentKind[] = [
  'INDOOR',
  'OUTDOOR',
  'PANEL',
  'REMOTE',
  'MATERIAL',
  'ACCESSORY',
  'FOOT',
]

const COMPONENT_KIND_RANK = new Map<ComponentKind, number>(
  COMPONENT_KIND_ORDER.map((kind, index) => [kind, index]),
)

function normalizedComponentKind(kind: ComponentKind | null): ComponentKind {
  return kind ?? 'ACCESSORY'
}

function withDisplayOrder(drafts: ComponentDraftModel[]): ComponentDraftModel[] {
  return drafts.map((draft, index) => ({ ...draft, displayOrder: index + 1 }))
}

/**
 * D-PCE-08 표시 순서: 종류순 + 종류 안 기본 먼저 + 기존 within-kind 순서 보존.
 *
 * <p>동일 종류·동일 기본 여부 안의 tie-breaker 는 입력 배열 순서에 의존한다.
 * 즉 호출자는 "입력 배열 순서 == 현재 표시 순서" 계약을 지켜야 사용자의
 * within-kind 드래그 결과가 저장 요청까지 보존된다.
 */
export function normalizeBundleComponentDraftOrder(
  drafts: ComponentDraftModel[],
): ComponentDraftModel[] {
  return withDisplayOrder(
    drafts
      .map((draft, index) => ({ draft, index }))
      .sort((a, b) => {
        const kindDelta =
          (COMPONENT_KIND_RANK.get(normalizedComponentKind(a.draft.componentKind)) ?? Number.MAX_SAFE_INTEGER) -
          (COMPONENT_KIND_RANK.get(normalizedComponentKind(b.draft.componentKind)) ?? Number.MAX_SAFE_INTEGER)
        if (kindDelta !== 0) return kindDelta
        if (a.draft.isDefault !== b.draft.isDefault) return a.draft.isDefault ? -1 : 1
        return a.index - b.index
      })
      .map((entry) => entry.draft),
  )
}

/** 구성품 모달 렌더용 그룹. 비어 있는 종류 헤더는 만들지 않는다. */
export function groupBundleComponentDrafts(
  drafts: ComponentDraftModel[],
): ComponentDraftGroup[] {
  const ordered = normalizeBundleComponentDraftOrder(drafts)
  return COMPONENT_KIND_ORDER
    .map((kind) => ({
      kind,
      items: ordered.filter((draft) => normalizedComponentKind(draft.componentKind) === kind),
    }))
    .filter((group) => group.items.length > 0)
}

export function canReorderBundleComponentDrafts(
  drafts: ComponentDraftModel[],
  activeId: string,
  overId: string,
): boolean {
  if (activeId === overId) return false
  const ordered = normalizeBundleComponentDraftOrder(drafts)
  const active = ordered.find((draft) => draft._localId === activeId)
  const over = ordered.find((draft) => draft._localId === overId)
  if (!active || !over) return false
  if (active.isDefault || over.isDefault) return false
  return normalizedComponentKind(active.componentKind) === normalizedComponentKind(over.componentKind)
}

/** 같은 종류의 비기본 구성품끼리만 이동한다. 위반 시 D-PCE-08 정규 순서만 반환한다. */
export function reorderBundleComponentDrafts(
  drafts: ComponentDraftModel[],
  activeId: string,
  overId: string,
): ComponentDraftModel[] {
  const ordered = normalizeBundleComponentDraftOrder(drafts)
  if (!canReorderBundleComponentDrafts(ordered, activeId, overId)) return ordered

  const activeIndex = ordered.findIndex((draft) => draft._localId === activeId)
  const overIndex = ordered.findIndex((draft) => draft._localId === overId)
  if (activeIndex < 0 || overIndex < 0) return ordered

  return withDisplayOrder(arrayMove(ordered, activeIndex, overIndex))
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
  return normalizeBundleComponentDraftOrder(drafts).map((draft) => ({
    componentProductCode: draft.componentProductCode,
    defaultQty: draft.defaultQty,
    qtyMode: draft.qtyMode ?? undefined,
    componentKind: draft.componentKind ?? undefined,
    componentVariant: draft.componentVariant ?? undefined,
    isDefault: draft.isDefault,
    specText: draft.specText ?? undefined,
  }))
}
