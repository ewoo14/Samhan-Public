import { describe, expect, it } from 'vitest'
import {
  buildCreateProductRequest,
  buildSpecs,
  buildUpdateProductRequest,
  composeDimensionSpecValue,
  editSeedToProductFormValues,
  initialProductFormValues,
  moveSpecRow,
  validateProductForm,
  type ProductFormValues,
} from './productFormModel'

const baseForm: ProductFormValues = {
  ...initialProductFormValues(),
  name: '천장형 실내기',
  modelName: 'AC-1000',
  categoryId: '11111111-1111-1111-1111-111111111111',
  sellingPrice: '1200000',
  purchasePrice: '900000',
  itemKind: 'GENERAL',
  goodsType: 'GOODS',
  productCategory: 'SINGLE_PART',
  unit: 'EA',
  releasePrice: '1000000',
  deliveryPrice: '30000',
  specs: [
    { specKey: '냉방능력, kW', specValue: '6.0', unit: 'kW', valueType: 'NUMBER' },
    { specKey: '전원', specValue: '220V', unit: '', valueType: 'TEXT' },
    { specKey: ' ', specValue: 'ignored', unit: '', valueType: 'TEXT' },
    { specKey: '크기', specValue: ' ', unit: 'mm', valueType: 'DIMENSION' },
  ],
}

describe('productFormModel', () => {
  it('CreateProductRequest 필드명과 값 매핑을 BE DTO 와 동일하게 만든다', () => {
    expect(buildCreateProductRequest(baseForm)).toEqual({
      name: '천장형 실내기',
      modelName: 'AC-1000',
      categoryId: '11111111-1111-1111-1111-111111111111',
      sellingPrice: '1200000',
      purchasePrice: '900000',
      currency: 'KRW',
      tags: {},
      description: null,
      itemKind: 'GENERAL',
      productCategory: 'SINGLE_PART',
      bundleMode: null,
      parentSetModelCode: null,
      componentKind: null,
      unit: 'EA',
      releasePrice: '1000000',
      deliveryPrice: '30000',
      goodsType: 'GOODS',
      specs: [
        { specKey: '냉방능력, kW', specValue: '6.0', unit: 'kW' },
        { specKey: '전원', specValue: '220V', unit: null },
      ],
    })
  })

  it('템플릿에 없는 커스텀 사양명을 그대로 보존한다', () => {
    const request = buildCreateProductRequest({
      ...baseForm,
      specs: [
        { specKey: '커스텀특수사양', specValue: '현장별 별도 협의', unit: '', valueType: 'TEXT' },
      ],
    })

    expect(request.specs).toEqual([
      { specKey: '커스텀특수사양', specValue: '현장별 별도 협의', unit: null },
    ])
  })

  it('DIMENSION 사양은 숫자 3분할 값을 x 조인 문자열과 단위로 저장한다', () => {
    expect(composeDimensionSpecValue('947', '365', '947')).toBe('947x365x947')
    expect(buildSpecs({
      ...baseForm,
      specs: [
        { specKey: '제품크기, mm', specValue: '947 x 365 x 947', unit: 'mm', valueType: 'DIMENSION' },
      ],
    })).toEqual([
      { specKey: '제품크기, mm', specValue: '947x365x947', unit: 'mm' },
    ])
  })

  it('사양 배열 순서 변경은 저장 요청 순서로 보존된다', () => {
    const moved = moveSpecRow([
      { specKey: '배관경', specValue: '6/12', unit: '', valueType: 'TEXT' },
      { specKey: '제품크기, mm', specValue: '947x365x947', unit: 'mm', valueType: 'DIMENSION' },
      { specKey: '냉방능력, kW', specValue: '6.0', unit: 'kW', valueType: 'NUMBER' },
    ], 2, 0)

    expect(buildSpecs({ ...baseForm, specs: moved }).map((spec) => spec.specKey)).toEqual([
      '냉방능력, kW',
      '배관경',
      '제품크기, mm',
    ])
  })

  it('SET 선택 시 bundleMode 를 포함하고 parentSetModelCode 는 보내지 않는다', () => {
    const request = buildCreateProductRequest({
      ...baseForm,
      itemKind: 'SET',
      productCategory: 'SINGLE_SET',
      bundleMode: 'EXPAND',
      parentSetModelCode: 'IGNORED-PARENT',
    })

    expect(request.itemKind).toBe('SET')
    expect(request.bundleMode).toBe('EXPAND')
    expect(request.parentSetModelCode).toBeNull()
  })

  it('SET_COMPONENT 선택 시 부모 세트 modelCode 와 componentKind 를 포함한다', () => {
    const request = buildCreateProductRequest({
      ...baseForm,
      itemKind: 'SET_COMPONENT',
      parentSetModelCode: 'SET-HM2WAY',
      componentKind: 'INDOOR',
    })

    expect(request.itemKind).toBe('SET_COMPONENT')
    expect(request.parentSetModelCode).toBe('SET-HM2WAY')
    expect(request.componentKind).toBe('INDOOR')
  })

  it('세트구성품은 부모 세트 선택 없이는 저장할 수 없다', () => {
    const errors = validateProductForm({
      ...baseForm,
      itemKind: 'SET_COMPONENT',
      parentSetModelCode: '',
    })

    expect(errors.parentSetModelCode).toBe('부모 세트를 선택해 주세요.')
  })

  it('UpdateProductRequest 는 수정 가능한 필드만 포함하고 가격 필드는 제외한다', () => {
    expect(buildUpdateProductRequest(baseForm)).toEqual({
      name: '천장형 실내기',
      modelName: 'AC-1000',
      categoryId: '11111111-1111-1111-1111-111111111111',
      description: null,
      itemKind: 'GENERAL',
      productCategory: 'SINGLE_PART',
      bundleMode: null,
      parentSetModelCode: null,
      componentKind: null,
      unit: 'EA',
      releasePrice: '1000000',
      deliveryPrice: '30000',
      goodsType: 'GOODS',
      specs: [
        { specKey: '냉방능력, kW', specValue: '6.0', unit: 'kW' },
        { specKey: '전원', specValue: '220V', unit: null },
      ],
    })
  })

  it('edit seed 는 SET_COMPONENT 상세 응답의 부모 링크와 실제 분류/단위를 보존한다', () => {
    const values = editSeedToProductFormValues({
      summary: {
        id: 'product-id',
        name: '실내기',
        modelName: 'IDU-001',
        productCode: null,
        categoryId: baseForm.categoryId,
        sellingPrice: '1200000',
        status: 'ACTIVE',
        goods: true,
        modelCode: 'IDU-001',
        productType: 'SINGLE',
      },
      detail: {
        id: 'product-id',
        name: '실내기',
        modelName: 'IDU-001',
        modelCode: 'IDU-001',
        categoryId: baseForm.categoryId,
        categoryName: '벽걸이형',
        sellingPrice: '1200000',
        purchasePrice: '900000',
        currency: 'KRW',
        tags: null,
        description: null,
        itemKind: 'SET_COMPONENT',
        productCategory: 'COMMERCIAL_PART',
        bundleMode: null,
        parentSetModelCode: 'SET-001',
        componentKind: 'INDOOR',
        unit: 'SET',
        releasePrice: '1000000',
        deliveryPrice: '30000',
        goodsType: 'NON_GOODS',
        specs: [
          { id: 'spec-1', specKey: '냉방능력, kW', specValue: '5.2', unit: 'kW', displayOrder: 1 },
          { id: 'spec-2', specKey: '전원', specValue: '220V', unit: null, displayOrder: 2 },
        ],
      },
      catalog: {
        modelCode: 'IDU-001',
        name: '실내기',
        usageScope: 'NONE',
        estimateCategory: null,
        usageScopeManual: false,
        displayOrder: null,
        releasePrice: 1,
        deliveryPrice: 2,
        productType: 'SINGLE',
        componentCount: 0,
      },
    })

    expect(values.itemKind).toBe('SET_COMPONENT')
    expect(values.productCategory).toBe('COMMERCIAL_PART')
    expect(values.parentSetModelCode).toBe('SET-001')
    expect(values.componentKind).toBe('INDOOR')
    expect(values.unit).toBe('SET')
    expect(values.goodsType).toBe('NON_GOODS')
    expect(values.specs).toEqual([
      { specKey: '냉방능력, kW', specValue: '5.2', unit: 'kW', valueType: 'TEXT' },
      { specKey: '전원', specValue: '220V', unit: '', valueType: 'TEXT' },
    ])
  })
})
