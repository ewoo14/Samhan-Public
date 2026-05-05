import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { EstimateLineRow } from './EstimateLineRow'

/**
 * `<EstimateLineRow>` Storybook 등재 — 견적/주문 라인 grid row.
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2
 */
const meta: Meta<typeof EstimateLineRow> = {
  title: 'Components/EstimateLineRow',
  component: EstimateLineRow,
  parameters: {
    docs: {
      description: {
        component:
          '견적서/주문서의 라인 1행. 12-column grid 로 모델/품목/규격/수량/가격/할인/소계/액션 표시. UUID 비공개 가드 — 모델명만 사용자 노출.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof EstimateLineRow>

/** Default — 편집 가능 라인 1행 (할인 5%). */
export const Default: Story = {
  name: '기본 (편집 가능)',
  render: () => {
    const [qty, setQty] = useState(2)
    const unitDelivery = 2700000
    const discount = 5
    const lineAmount = Math.round(qty * unitDelivery * (1 - discount / 100))
    return (
      <div style={{ width: 1100, border: '1px solid #ddd' }}>
        <EstimateLineRow
          lineNumber={1}
          model="AC180RXADKG"
          productName="시스템 에어컨 4-way 18평"
          spec="냉방 5.6kW / 220V"
          qty={qty}
          releasePrice={2890000}
          deliveryPrice={unitDelivery}
          discountRate={discount}
          lineAmount={lineAmount}
          onQtyChange={setQty}
          onDelete={() => alert('삭제')}
          onSpecClick={() => alert('스펙 모달 열기')}
        />
      </div>
    )
  },
}

/** 읽기 전용 — 견적 confirmed 후 편집 차단 상태. */
export const ReadOnly: Story = {
  name: '읽기 전용',
  render: () => (
    <div style={{ width: 1100, border: '1px solid #ddd' }}>
      <EstimateLineRow
        lineNumber={1}
        model="AC180RXADKG"
        productName="시스템 에어컨 4-way 18평"
        spec="냉방 5.6kW / 220V"
        qty={2}
        releasePrice={2890000}
        deliveryPrice={2700000}
        discountRate={5}
        lineAmount={5130000}
        readOnly
        onSpecClick={() => alert('스펙 모달 열기')}
      />
    </div>
  ),
}

/** 할인 없음 + 액션 일부만 (스펙 버튼 X). */
export const NoDiscountNoSpecBtn: Story = {
  name: '할인 없음 / 스펙 버튼 X',
  render: () => {
    const [qty, setQty] = useState(1)
    return (
      <div style={{ width: 1100, border: '1px solid #ddd' }}>
        <EstimateLineRow
          lineNumber={3}
          model="AF18BX878"
          productName="스탠드형 에어컨 18평"
          qty={qty}
          releasePrice={1990000}
          deliveryPrice={1850000}
          lineAmount={qty * 1850000}
          onQtyChange={setQty}
          onDelete={() => alert('삭제')}
        />
      </div>
    )
  },
}
