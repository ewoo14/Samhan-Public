import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import {
  WarehouseSelector,
  type Warehouse,
} from './WarehouseSelector'

/**
 * Storybook 시나리오용 mock 창고 데이터.
 * 4-tier 모델 (HEADQUARTERS / VEHICLE / CONSIGNMENT / VIRTUAL) 을 모두 포함.
 */
const sampleWarehouses: Warehouse[] = [
  { id: 'w1', code: 'HQ-001', name: '본사창고', type: 'HEADQUARTERS', active: true },
  { id: 'w2', code: 'VH-001', name: '1호차 차량재고', type: 'VEHICLE', active: true },
  { id: 'w3', code: 'CS-001', name: '○○종합건설 위탁', type: 'CONSIGNMENT', active: true },
  { id: 'w4', code: 'VR-001', name: '서비스 인보이스(가상)', type: 'VIRTUAL', active: true },
]

/** 비활성 창고가 1건 섞인 변형 (WithInactive 시나리오용). */
const warehousesWithInactive: Warehouse[] = [
  { id: 'w1', code: 'HQ-001', name: '본사창고', type: 'HEADQUARTERS', active: true },
  { id: 'w2', code: 'VH-001', name: '1호차 차량재고', type: 'VEHICLE', active: false },
  { id: 'w3', code: 'CS-001', name: '○○종합건설 위탁', type: 'CONSIGNMENT', active: true },
  { id: 'w4', code: 'VR-001', name: '서비스 인보이스(가상)', type: 'VIRTUAL', active: true },
]

const meta: Meta<typeof WarehouseSelector> = {
  title: 'Components/WarehouseSelector',
  component: WarehouseSelector,
}
export default meta

type Story = StoryObj<typeof WarehouseSelector>

/**
 * 4개 창고 모두 표시. 본사창고가 초기 선택된 controlled 예시.
 */
export const Default: Story = {
  render: () => {
    const [value, setValue] = useState<string | null>('w1')
    return (
      <WarehouseSelector
        warehouses={sampleWarehouses}
        value={value}
        onChange={(id) => setValue(id)}
      />
    )
  },
}

/**
 * `hideVirtual=true` — 가상창고(VR-001) 옵션이 숨겨진다.
 * 출고/이동 화면에서의 권장 사용 예.
 */
export const HideVirtual: Story = {
  render: () => {
    const [value, setValue] = useState<string | null>('w1')
    return (
      <WarehouseSelector
        warehouses={sampleWarehouses}
        value={value}
        onChange={(id) => setValue(id)}
        hideVirtual
        label="출고 창고"
      />
    )
  },
}

/**
 * 차량재고 1건이 비활성 상태(`active: false`).
 * 옵션이 disabled + 회색 처리되어 선택할 수 없다.
 */
export const WithInactive: Story = {
  render: () => {
    const [value, setValue] = useState<string | null>('w1')
    return (
      <WarehouseSelector
        warehouses={warehousesWithInactive}
        value={value}
        onChange={(id) => setValue(id)}
      />
    )
  },
}

/**
 * 빈 창고 목록. placeholder 만 표시.
 * (BE 응답 0건 또는 권한으로 필터링된 상태 시나리오)
 */
export const Empty: Story = {
  render: () => {
    const [value, setValue] = useState<string | null>(null)
    return (
      <WarehouseSelector
        warehouses={[]}
        value={value}
        onChange={(id) => setValue(id)}
      />
    )
  },
}

/**
 * 에러 메시지 표시. FormField 의 빨간 outline + 에러 텍스트.
 */
export const WithError: Story = {
  render: () => {
    const [value, setValue] = useState<string | null>(null)
    return (
      <WarehouseSelector
        warehouses={sampleWarehouses}
        value={value}
        onChange={(id) => setValue(id)}
        error="창고를 선택해야 합니다"
        required
      />
    )
  },
}
