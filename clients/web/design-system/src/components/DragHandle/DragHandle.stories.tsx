import type { Meta, StoryObj } from '@storybook/react'
import { DragHandle } from './DragHandle'

const meta: Meta<typeof DragHandle> = {
  title: 'Sales-Form-Polish/DragHandle',
  component: DragHandle,
  args: {
    label: '라인 1 드래그',
    dragging: false,
  },
  argTypes: {
    dragging: { control: 'boolean' },
  },
  parameters: {
    docs: {
      description: {
        component:
          'sales-form-polish 슬라이스 신규 컴포넌트. `<LineRow>` 좌측에 부착되어 행 순서 변경. `@dnd-kit/sortable` 의 listeners + attributes 를 외부에서 주입받는 dump 컴포넌트. ⠿ Braille glyph 사용.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof DragHandle>

export const Default: Story = {}

export const Dragging: Story = {
  args: { dragging: true },
}

export const Hovered: Story = {
  parameters: { pseudo: { hover: true } },
}

export const InRow: Story = {
  render: (args) => (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '40px 24px 24px 200px 80px',
        alignItems: 'center',
        height: 40,
        padding: '0 12px',
        background: 'var(--surface-card)',
        border: '1px solid var(--line-default)',
        borderRadius: 8,
      }}
    >
      <input type="checkbox" />
      <DragHandle {...args} />
      <span style={{ color: 'var(--ink-tertiary)', fontSize: 12, textAlign: 'center' }}>1</span>
      <span style={{ fontSize: 14, color: 'var(--ink-primary)' }}>AJ040RXH4BC1</span>
      <span style={{ fontSize: 14, color: 'var(--ink-secondary)', textAlign: 'right' }}>2</span>
    </div>
  ),
}

export const KeyboardFocus: Story = {
  args: { label: '라인 2 드래그' },
  parameters: {
    docs: {
      description: {
        story:
          'Tab 으로 focus 진입 시 outline 표시 (키보드 sensor 활성화). dnd-kit `setActivatorNodeRef` 가 attach 되면 Space/Enter 로 drag 시작 가능.',
      },
    },
  },
}
