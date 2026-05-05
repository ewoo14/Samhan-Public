import type { Meta, StoryObj } from '@storybook/react'
import { ProductSpecList, type ProductSpec } from './ProductSpecList'

/**
 * `<ProductSpecList>` Storybook 등재 — ProductSpec 동적 목록.
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2 / DOMAIN-EXTENSIONS §4 D18
 */
const meta: Meta<typeof ProductSpecList> = {
  title: 'Components/ProductSpecList',
  component: ProductSpecList,
  parameters: {
    docs: {
      description: {
        component:
          'ProductSpec 의 key:value 동적 목록. 3 layout (inline / card / table) × 2 mode (screen / print). DOMAIN-EXTENSIONS §4 D18 정렬 정책 (screen=ProductSpec.displayOrder / print=SpecKeyTemplate.displayOrder).',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof ProductSpecList>

const homeMultiSpecs: ProductSpec[] = [
  // 사용자가 drag&drop 으로 정의한 임의 순서
  { specKey: '냉방성능', specValue: '5.6', unit: 'kW', displayOrder: 1 },
  { specKey: '배관경', specValue: 'Φ6.35×Φ12.7', displayOrder: 4 },
  { specKey: '전원선', specValue: '2.5', unit: 'mm²', displayOrder: 2 },
  { specKey: '제품중량', specValue: '12.5', unit: 'kg', displayOrder: 5 },
  { specKey: '차단기', specValue: '20', unit: 'A', displayOrder: 3 },
]

// SpecKeyTemplate.displayOrder — 카테고리 표준 (인쇄용)
const homeMultiTemplateOrder: Record<string, number> = {
  모델명: 1,
  냉방성능: 2,
  난방성능: 3,
  소비전력: 4,
  전원선: 5,
  차단기: 6,
  배관경: 7,
  제품크기: 8,
  제품중량: 9,
}

/** Table — 화면 정렬 (ProductSpec.displayOrder). */
export const TableScreen: Story = {
  name: 'Table × Screen 정렬 (사용자 순서)',
  render: () => (
    <div style={{ width: 480 }}>
      <ProductSpecList specs={homeMultiSpecs} mode="screen" layout="table" />
    </div>
  ),
}

/** Table — 인쇄 정렬 (SpecKeyTemplate.displayOrder). */
export const TablePrint: Story = {
  name: 'Table × Print 정렬 (카테고리 표준)',
  render: () => (
    <div style={{ width: 480, padding: 16, background: '#fff' }}>
      <ProductSpecList
        specs={homeMultiSpecs}
        mode="print"
        templateOrder={homeMultiTemplateOrder}
        layout="table"
      />
    </div>
  ),
}

/** Card layout. */
export const CardScreen: Story = {
  name: 'Card × Screen',
  render: () => (
    <div style={{ width: 380 }}>
      <ProductSpecList specs={homeMultiSpecs} mode="screen" layout="card" />
    </div>
  ),
}

/** Inline — 한 줄 요약 (EstimateLineRow `spec` slot 용). */
export const InlineScreen: Story = {
  name: 'Inline × Screen',
  render: () => (
    <div style={{ width: 600 }}>
      <ProductSpecList specs={homeMultiSpecs} mode="screen" layout="inline" />
    </div>
  ),
}

/** 빈 목록. */
export const Empty: Story = {
  name: '빈 목록',
  render: () => (
    <div style={{ width: 380 }}>
      <ProductSpecList specs={[]} layout="table" />
    </div>
  ),
}
