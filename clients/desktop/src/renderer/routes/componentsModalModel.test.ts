import { describe, expect, it } from 'vitest'
import {
  buildBundleComponentInputs,
  canReorderBundleComponentDrafts,
  groupBundleComponentDrafts,
  reorderBundleComponentDrafts,
  toggleComponentDefault,
  type ComponentDraftModel,
} from './componentsModalModel'

const baseDrafts: ComponentDraftModel[] = [
  {
    componentProductCode: 'PNL-BASIC',
    componentName: '기본 판넬',
    defaultQty: 1,
    qtyMode: 'FOLLOW_SET',
    componentKind: 'PANEL',
    componentVariant: null,
    isDefault: false,
    specText: null,
    displayOrder: 1,
    _localId: 'row-1',
    _isNew: false,
  },
  {
    componentProductCode: 'PNL-BLACK',
    componentName: '블랙 판넬',
    defaultQty: 1,
    qtyMode: 'FOLLOW_SET',
    componentKind: 'PANEL',
    componentVariant: '블랙',
    isDefault: false,
    specText: null,
    displayOrder: 2,
    _localId: 'row-2',
    _isNew: true,
  },
]

describe('componentsModalModel', () => {
  it('구성품 행의 기본 여부를 사용자가 켜고 끌 수 있다', () => {
    const checked = toggleComponentDefault(baseDrafts, 'row-2', true)
    expect(checked.map((draft) => draft.isDefault)).toEqual([false, true])

    const unchecked = toggleComponentDefault(checked, 'row-2', false)
    expect(unchecked.map((draft) => draft.isDefault)).toEqual([false, false])
  })

  it('저장 요청에 구성품 기본 여부를 포함한다', () => {
    const drafts = toggleComponentDefault(baseDrafts, 'row-1', true)

    expect(buildBundleComponentInputs(drafts)).toEqual([
      {
        componentProductCode: 'PNL-BASIC',
        defaultQty: 1,
        qtyMode: 'FOLLOW_SET',
        componentKind: 'PANEL',
        componentVariant: undefined,
        isDefault: true,
        specText: undefined,
      },
      {
        componentProductCode: 'PNL-BLACK',
        defaultQty: 1,
        qtyMode: 'FOLLOW_SET',
        componentKind: 'PANEL',
        componentVariant: '블랙',
        isDefault: false,
        specText: undefined,
      },
    ])
  })

  it('구성품을 종류순으로 그룹화하고 종류 안에서는 기본 구성품을 먼저 둔다', () => {
    const drafts: ComponentDraftModel[] = [
      draft('remote-extra-2', 'REMOTE', false),
      draft('panel-extra-1', 'PANEL', false),
      draft('outdoor-default', 'OUTDOOR', true),
      draft('panel-default', 'PANEL', true),
      draft('indoor-default', 'INDOOR', true),
      draft('remote-default', 'REMOTE', true),
      draft('panel-extra-2', 'PANEL', false),
    ]

    const groups = groupBundleComponentDrafts(drafts)

    expect(groups.map((group) => group.kind)).toEqual(['INDOOR', 'OUTDOOR', 'PANEL', 'REMOTE'])
    expect(groups.flatMap((group) => group.items.map((item) => item._localId))).toEqual([
      'indoor-default',
      'outdoor-default',
      'panel-default',
      'panel-extra-1',
      'panel-extra-2',
      'remote-default',
      'remote-extra-2',
    ])
    expect(groups.flatMap((group) => group.items.map((item) => item.displayOrder))).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it('같은 종류의 비기본 구성품끼리만 드래그 재정렬을 허용한다', () => {
    const drafts: ComponentDraftModel[] = [
      draft('panel-default', 'PANEL', true),
      draft('panel-extra-1', 'PANEL', false),
      draft('panel-extra-2', 'PANEL', false),
      draft('remote-extra', 'REMOTE', false),
    ]

    expect(canReorderBundleComponentDrafts(drafts, 'panel-extra-1', 'panel-extra-2')).toBe(true)
    expect(canReorderBundleComponentDrafts(drafts, 'panel-extra-1', 'remote-extra')).toBe(false)
    expect(canReorderBundleComponentDrafts(drafts, 'panel-extra-1', 'panel-default')).toBe(false)
    expect(canReorderBundleComponentDrafts(drafts, 'panel-default', 'panel-extra-1')).toBe(false)

    const reordered = reorderBundleComponentDrafts(drafts, 'panel-extra-2', 'panel-extra-1')
    expect(reordered.map((item) => item._localId)).toEqual([
      'panel-default',
      'panel-extra-2',
      'panel-extra-1',
      'remote-extra',
    ])

    const rejected = reorderBundleComponentDrafts(drafts, 'panel-extra-1', 'remote-extra')
    expect(rejected.map((item) => item._localId)).toEqual([
      'panel-default',
      'panel-extra-1',
      'panel-extra-2',
      'remote-extra',
    ])
  })

  it('같은 종류의 비기본 구성품을 하향 다칸 드래그하면 over 행 뒤로 밀리지 않는다', () => {
    const drafts: ComponentDraftModel[] = [
      draft('panel-default', 'PANEL', true),
      draft('panel-extra-1', 'PANEL', false),
      draft('panel-extra-2', 'PANEL', false),
      draft('panel-extra-3', 'PANEL', false),
      draft('panel-extra-4', 'PANEL', false),
      draft('remote-extra', 'REMOTE', false),
    ]

    const reordered = reorderBundleComponentDrafts(drafts, 'panel-extra-1', 'panel-extra-4')

    expect(reordered.map((item) => item._localId)).toEqual([
      'panel-default',
      'panel-extra-2',
      'panel-extra-3',
      'panel-extra-4',
      'panel-extra-1',
      'remote-extra',
    ])
    expect(reordered.map((item) => item.displayOrder)).toEqual([1, 2, 3, 4, 5, 6])
  })

  it('저장 요청 배열은 종류순, 기본 먼저, 사용자 within-kind 순서를 따른다', () => {
    const drafts: ComponentDraftModel[] = [
      draft('remote-extra', 'REMOTE', false),
      draft('panel-extra-1', 'PANEL', false),
      draft('panel-default', 'PANEL', true),
      draft('panel-extra-2', 'PANEL', false),
      draft('indoor-default', 'INDOOR', true),
    ]

    expect(buildBundleComponentInputs(drafts).map((item) => item.componentProductCode)).toEqual([
      'indoor-default',
      'panel-default',
      'panel-extra-1',
      'panel-extra-2',
      'remote-extra',
    ])
  })
})

function draft(
  localId: string,
  kind: ComponentDraftModel['componentKind'],
  isDefault: boolean,
): ComponentDraftModel {
  return {
    componentProductCode: localId,
    componentName: localId,
    defaultQty: 1,
    qtyMode: 'FOLLOW_SET',
    componentKind: kind,
    componentVariant: null,
    isDefault,
    specText: null,
    displayOrder: 1,
    _localId: localId,
    _isNew: false,
  }
}
