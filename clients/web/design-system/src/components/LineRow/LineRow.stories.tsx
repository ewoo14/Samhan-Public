import type { Meta, StoryObj } from '@storybook/react'
import { useState } from 'react'
import { LineRow, type LineDraft } from './LineRow'
import { LineTableHeader } from './LineTableHeader'

const baseLine: LineDraft = {
  id: 'line-1',
  productId: 'p-aj040',
  modelName: 'AJ040RXH4BC1',
  productName: '시스템에어컨 4Way 4HP',
  quantity: '2',
  unitPrice: '1850000',
  lookupError: null,
  lookupLoading: false,
}

const noopHandlers = {
  onSelect: () => undefined,
  onModelNameChange: () => undefined,
  onModelNameBlur: () => undefined,
  onQuantityChange: () => undefined,
  onUnitPriceChange: () => undefined,
  onDelete: () => undefined,
  dragHandleProps: {},
}

const meta: Meta<typeof LineRow> = {
  title: 'Sales-Form-Polish/LineRow',
  component: LineRow,
  args: {
    lineNumber: 1,
    line: baseLine,
    selected: false,
    isDragging: false,
    canDelete: true,
    ...noopHandlers,
  },
  decorators: [
    (Story) => (
      <div style={{ width: 1024, padding: 24, background: 'var(--surface-app)' }}>
        <div
          style={{
            border: '1px solid var(--line-default)',
            borderRadius: 8,
            background: 'var(--surface-card)',
            overflow: 'hidden',
          }}
        >
          <Story />
        </div>
      </div>
    ),
  ],
  parameters: {
    docs: {
      description: {
        component:
          'sales-form-polish 슬라이스 핵심 컴포넌트. 9-column grid, 5 states (default/hover/selected/dragging/error), 자동 라인 번호, 모델명 lookup spinner, 수량×단가 합계 자동 계산.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof LineRow>

export const Default: Story = {}

export const Selected: Story = {
  args: { selected: true },
}

export const Loading: Story = {
  args: {
    line: { ...baseLine, lookupLoading: true, productName: '' },
  },
}

export const Error: Story = {
  args: {
    line: {
      ...baseLine,
      productId: null,
      productName: '',
      lookupError: '해당 모델명을 찾을 수 없습니다',
    },
  },
}

export const Empty: Story = {
  args: {
    line: {
      id: 'line-empty',
      productId: null,
      modelName: '',
      productName: '',
      quantity: '1',
      unitPrice: '0',
      lookupError: null,
      lookupLoading: false,
    },
  },
}

export const Dragging: Story = {
  args: { isDragging: true },
}

export const FullTable: Story = {
  render: () => {
    const Demo = () => {
      const [lines, setLines] = useState<LineDraft[]>([
        { ...baseLine, id: 'l1' },
        {
          id: 'l2',
          productId: 'p-mwr10',
          modelName: 'MWR-WE10N',
          productName: '유선 리모컨',
          quantity: '2',
          unitPrice: '85000',
          lookupError: null,
          lookupLoading: false,
        },
        {
          id: 'l3',
          productId: 'p-pc1',
          modelName: 'PC1NWSK3NW',
          productName: 'WIFI판넬',
          quantity: '1',
          unitPrice: '120000',
          lookupError: null,
          lookupLoading: false,
        },
      ])
      const [selected, setSelected] = useState<Set<string>>(new Set(['l3']))

      const allSelected = selected.size === lines.length && lines.length > 0
      const someSelected = selected.size > 0 && selected.size < lines.length

      return (
        <div>
          <LineTableHeader
            allSelected={allSelected}
            someSelected={someSelected}
            onToggleAll={(checked) => {
              if (checked) setSelected(new Set(lines.map((l) => l.id)))
              else setSelected(new Set())
            }}
          />
          {lines.map((line, idx) => (
            <LineRow
              key={line.id}
              lineNumber={idx + 1}
              line={line}
              selected={selected.has(line.id)}
              onSelect={(s) => {
                const next = new Set(selected)
                if (s) next.add(line.id)
                else next.delete(line.id)
                setSelected(next)
              }}
              onModelNameChange={(v) =>
                setLines((ls) =>
                  ls.map((l, i) => (i === idx ? { ...l, modelName: v } : l)),
                )
              }
              onModelNameBlur={() => undefined}
              onQuantityChange={(v) =>
                setLines((ls) =>
                  ls.map((l, i) => (i === idx ? { ...l, quantity: v } : l)),
                )
              }
              onUnitPriceChange={(v) =>
                setLines((ls) =>
                  ls.map((l, i) => (i === idx ? { ...l, unitPrice: v } : l)),
                )
              }
              onDelete={() => setLines((ls) => ls.filter((_, i) => i !== idx))}
              dragHandleProps={{}}
              canDelete={lines.length > 1}
            />
          ))}
        </div>
      )
    }
    return <Demo />
  },
  parameters: {
    docs: {
      description: {
        story: '실제 사용 패턴 — header + 3개 라인 + state 관리 (3번 라인 선택됨).',
      },
    },
  },
}
