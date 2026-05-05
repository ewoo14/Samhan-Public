import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { CategoryTabs, type EstimateCategory } from './CategoryTabs'

/**
 * `<CategoryTabs>` Storybook 등재 — 4+1 카테고리 탭.
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2 / DOMAIN-EXTENSIONS §3
 */
const meta: Meta<typeof CategoryTabs> = {
  title: 'Components/CategoryTabs',
  component: CategoryTabs,
  parameters: {
    docs: {
      description: {
        component:
          'estimateCategory 4 카테고리 (HOME_MULTI / SINGLE_SET / COMMERCIAL_MULTI / LEGACY) + OTHER 탭. count badge 지원.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof CategoryTabs>

/** 기본 — 5 탭, count badge 없음. */
export const Default: Story = {
  name: '기본 (5 탭)',
  render: () => {
    const [cat, setCat] = useState<EstimateCategory>('HOME_MULTI')
    return (
      <div style={{ width: 720 }}>
        <CategoryTabs value={cat} onChange={setCat} />
      </div>
    )
  },
}

/** count badge — 라인 수 표시. */
export const WithCounts: Story = {
  name: 'count badge 표시',
  render: () => {
    const [cat, setCat] = useState<EstimateCategory>('SINGLE_SET')
    return (
      <div style={{ width: 720 }}>
        <CategoryTabs
          value={cat}
          onChange={setCat}
          counts={{
            HOME_MULTI: 12,
            SINGLE_SET: 3,
            COMMERCIAL_MULTI: 7,
            LEGACY: 0,
            OTHER: 1,
          }}
        />
      </div>
    )
  },
}

/** 일부 탭 비활성 — 데이터 없는 카테고리 비활성화. */
export const SomeDisabled: Story = {
  name: '일부 비활성',
  render: () => {
    const [cat, setCat] = useState<EstimateCategory>('HOME_MULTI')
    return (
      <div style={{ width: 720 }}>
        <CategoryTabs
          value={cat}
          onChange={setCat}
          disabled={['LEGACY', 'OTHER']}
          counts={{ HOME_MULTI: 12, SINGLE_SET: 3, COMMERCIAL_MULTI: 7 }}
        />
      </div>
    )
  },
}

/** OTHER 숨김 — 4 탭만 표시. */
export const FourTabsOnly: Story = {
  name: 'OTHER 숨김 (4 탭)',
  render: () => {
    const [cat, setCat] = useState<EstimateCategory>('HOME_MULTI')
    return (
      <div style={{ width: 600 }}>
        <CategoryTabs
          value={cat}
          onChange={setCat}
          categories={['HOME_MULTI', 'SINGLE_SET', 'COMMERCIAL_MULTI', 'LEGACY']}
        />
      </div>
    )
  },
}
