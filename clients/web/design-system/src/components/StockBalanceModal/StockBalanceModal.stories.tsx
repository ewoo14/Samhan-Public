import type { Meta, StoryObj } from '@storybook/react'
import {
  StockBalanceModal,
  type StockBalanceRow,
  type WarehouseColumn,
} from './StockBalanceModal'

const SAMPLE_LINES = [
  { productId: 'p-aj040', modelName: 'AJ040RXH4BC1', productName: '시스템에어컨 4Way 4HP' },
  { productId: 'p-mwr10', modelName: 'MWR-WE10N', productName: '유선 리모컨' },
  { productId: 'p-pc1',   modelName: 'PC1NWSK3NW', productName: 'WIFI판넬' },
]

const SAMPLE_COLUMNS: WarehouseColumn[] = [
  { code: 'HQ-001', label: '본사' },
  { code: 'VH-001', label: '차량1' },
  { code: 'CS-001', label: '위탁' },
  { code: 'VR-001', label: '가상', virtual: true },
]

const SAMPLE_ROWS: StockBalanceRow[] = [
  {
    productId: 'p-aj040',
    modelName: 'AJ040RXH4BC1',
    productName: '시스템에어컨 4Way 4HP',
    perWarehouse: { 'HQ-001': 12, 'VH-001': 3, 'CS-001': 0, 'VR-001': null },
    total: 15,
  },
  {
    productId: 'p-mwr10',
    modelName: 'MWR-WE10N',
    productName: '유선 리모컨',
    perWarehouse: { 'HQ-001': 45, 'VH-001': 10, 'CS-001': 2, 'VR-001': null },
    total: 57,
  },
  {
    productId: 'p-pc1',
    modelName: 'PC1NWSK3NW',
    productName: 'WIFI판넬',
    perWarehouse: { 'HQ-001': 8, 'VH-001': null, 'CS-001': null, 'VR-001': null },
    total: 8,
  },
]

const meta: Meta<typeof StockBalanceModal> = {
  title: 'Sales-Form-Polish/StockBalanceModal',
  component: StockBalanceModal,
  args: {
    open: true,
    onClose: () => undefined,
    selectedLines: SAMPLE_LINES,
    warehouseColumns: SAMPLE_COLUMNS,
    rows: SAMPLE_ROWS,
    error: null,
  },
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'sales-form-polish 슬라이스 신규 모달. 선택 라인의 productId 들을 batch 조회한 결과를 모델명 × 창고 matrix 로 표시. 0 dim, null `-` dim, 가상창고 italic dim, 합계 bold.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof StockBalanceModal>

export const Loaded: Story = {}

export const Loading: Story = {
  args: { rows: null },
}

export const Empty: Story = {
  args: { rows: [] },
}

export const Error: Story = {
  args: { error: '재고 조회에 실패했습니다. 다시 시도해 주세요.', rows: null },
}

export const SingleLine: Story = {
  args: {
    selectedLines: [SAMPLE_LINES[0]!],
    rows: [SAMPLE_ROWS[0]!],
  },
}
