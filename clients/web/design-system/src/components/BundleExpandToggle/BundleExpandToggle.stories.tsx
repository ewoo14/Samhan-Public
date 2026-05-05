import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { BundleExpandToggle, type BundleExpandMode } from './BundleExpandToggle'

/**
 * `<BundleExpandToggle>` Storybook 등재 — BUNDLE EXPAND/KEEP 모드 segmented toggle.
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2 / §1.2.5
 */
const meta: Meta<typeof BundleExpandToggle> = {
  title: 'Components/BundleExpandToggle',
  component: BundleExpandToggle,
  parameters: {
    docs: {
      description: {
        component:
          'BUNDLE 품목의 EXPAND (펼침) / KEEP (유지) 모드를 전환. DOMAIN-EXTENSIONS §2 bundleMode 와 1:1 매핑.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof BundleExpandToggle>

/** 기본 — EXPAND 활성. */
export const ExpandActive: Story = {
  name: 'EXPAND 모드 (기본)',
  render: () => {
    const [mode, setMode] = useState<BundleExpandMode>('EXPAND')
    return <BundleExpandToggle mode={mode} onChange={setMode} />
  },
}

/** KEEP 모드 활성. */
export const KeepActive: Story = {
  name: 'KEEP 모드',
  render: () => {
    const [mode, setMode] = useState<BundleExpandMode>('KEEP')
    return <BundleExpandToggle mode={mode} onChange={setMode} />
  },
}

/** Disabled (read-only 라인 등). */
export const Disabled: Story = {
  name: '비활성',
  render: () => (
    <BundleExpandToggle mode="EXPAND" onChange={() => {}} disabled />
  ),
}

/** EstimateLineRow 옆 인라인 배치 가정. */
export const InlineExample: Story = {
  name: '라인 옆 인라인 예시',
  render: () => {
    const [mode, setMode] = useState<BundleExpandMode>('KEEP')
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span style={{ fontFamily: 'monospace', fontSize: 13 }}>
          [BUNDLE] AC-COMBO-7P
        </span>
        <BundleExpandToggle
          mode={mode}
          onChange={setMode}
          ariaLabel="라인 4 BUNDLE 모드"
        />
      </div>
    )
  },
}
