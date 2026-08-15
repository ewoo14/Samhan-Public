import { describe, expect, it } from 'vitest'
import { mapInboundProduct, type InboundCatalogItem } from './inboundXlsxMapping'

const catalog: InboundCatalogItem[] = [
  { productCode: 'P-EXACT', productName: '가스히트펌프' },
  { productCode: 'P-PART', productName: 'ABC 실외기' },
  { productCode: 'TOKEN-01', productName: '다른 이름' },
]

describe('가입고 품목 매핑 레거시 4단계', () => {
  it('정제 품목명 또는 코드 완전일치를 먼저 선택한다', () => {
    expect(mapInboundProduct('원본', '가스히트펌프', catalog)).toMatchObject({ productCode: 'P-EXACT', status: '코드불일치' })
    expect(mapInboundProduct('원본', 'P-EXACT', catalog)).toMatchObject({ productCode: 'P-EXACT', status: '품목일치' })
  })

  it('완전일치가 없으면 품목명 상호 부분포함을 선택한다', () => {
    expect(mapInboundProduct('원본', 'ABC', catalog)).toMatchObject({ productCode: 'P-PART', status: '코드불일치' })
  })

  it('부분포함도 없으면 원본 모델 첫 token과 품목코드를 비교한다', () => {
    expect(mapInboundProduct('TOKEN-01 옵션', 'TOKEN-01 옵션', catalog)).toMatchObject({ productCode: 'TOKEN-01', status: '코드불일치' })
  })

  it('네 단계 모두 실패해도 검색실패 행을 버리지 않는다', () => {
    expect(mapInboundProduct('UNKNOWN-X 옵션', 'UNKNOWN-X 옵션', catalog)).toMatchObject({ productCode: 'UNKNOWN-X 옵션', status: '검색실패' })
  })
})
