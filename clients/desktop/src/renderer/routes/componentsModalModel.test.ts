import { describe, expect, it } from 'vitest'
import {
  buildBundleComponentInputs,
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
})
