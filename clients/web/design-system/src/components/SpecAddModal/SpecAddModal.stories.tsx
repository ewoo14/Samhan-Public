import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { SpecAddModal, type SpecKeyTemplate } from './SpecAddModal'
import { Button } from '../Button/Button'

/**
 * `<SpecAddModal>` Storybook 등재 — 추천 키 chip + 자유 입력.
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2 / DOMAIN-EXTENSIONS §4 D15
 */
const meta: Meta<typeof SpecAddModal> = {
  title: 'Components/SpecAddModal',
  component: SpecAddModal,
  parameters: {
    docs: {
      description: {
        component:
          '카테고리별 추천 스펙 키 chip + 자유 입력으로 ProductSpec 1건 추가. DOMAIN-EXTENSIONS §4 D15 — 409 strict + Frontend disabled 가드.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof SpecAddModal>

const homeMultiTemplates: SpecKeyTemplate[] = [
  { specKey: '냉방성능', defaultUnit: 'kW', displayOrder: 1, isRecommended: true },
  { specKey: '난방성능', defaultUnit: 'kW', displayOrder: 2, isRecommended: true },
  { specKey: '소비전력', defaultUnit: 'kW', displayOrder: 3, isRecommended: true },
  { specKey: '전원선', defaultUnit: 'mm²', displayOrder: 4, isRecommended: true },
  { specKey: '차단기', defaultUnit: 'A', displayOrder: 5, isRecommended: true },
  { specKey: '배관경', displayOrder: 6, isRecommended: true },
  { specKey: '제품크기', defaultUnit: 'mm', displayOrder: 7, isRecommended: true },
  { specKey: '제품중량', defaultUnit: 'kg', displayOrder: 8, isRecommended: true },
]

/** 기본 — HOME_MULTI 카테고리, 일부 키 이미 등록 (disabled). */
export const HomeMultiWithExisting: Story = {
  name: '홈멀티 / 일부 키 등록됨 (chip disabled)',
  render: () => {
    const [open, setOpen] = useState(true)
    return (
      <div>
        <Button onClick={() => setOpen(true)}>스펙 추가 모달 열기</Button>
        <SpecAddModal
          open={open}
          onClose={() => setOpen(false)}
          category="HOME_MULTI"
          recommended={homeMultiTemplates}
          existingKeys={['냉방성능', '전원선']}
          onAdd={(key, val, unit) => {
            alert(`추가: ${key} = ${val}${unit ? ` ${unit}` : ''}`)
          }}
        />
      </div>
    )
  },
}

/** 빈 상태 — 등록된 키 없음. */
export const HomeMultiEmpty: Story = {
  name: '홈멀티 / 등록 키 0',
  render: () => {
    const [open, setOpen] = useState(true)
    return (
      <div>
        <Button onClick={() => setOpen(true)}>스펙 추가 모달 열기</Button>
        <SpecAddModal
          open={open}
          onClose={() => setOpen(false)}
          category="HOME_MULTI"
          recommended={homeMultiTemplates}
          existingKeys={[]}
          onAdd={(key, val, unit) => {
            alert(`추가: ${key} = ${val}${unit ? ` ${unit}` : ''}`)
          }}
        />
      </div>
    )
  },
}

/** OTHER 카테고리 — 추천 키 0 (자유 입력만). */
export const OtherCategory: Story = {
  name: '기타 카테고리 / 추천 키 0',
  render: () => {
    const [open, setOpen] = useState(true)
    return (
      <div>
        <Button onClick={() => setOpen(true)}>스펙 추가 모달 열기</Button>
        <SpecAddModal
          open={open}
          onClose={() => setOpen(false)}
          category="OTHER"
          recommended={[]}
          existingKeys={[]}
          onAdd={(key, val, unit) => {
            alert(`추가: ${key} = ${val}${unit ? ` ${unit}` : ''}`)
          }}
        />
      </div>
    )
  },
}
