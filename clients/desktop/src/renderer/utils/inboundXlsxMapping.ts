export type InboundCatalogItem = { productCode: string | null; productName: string }

export type InboundProductMapping = {
  productCode: string
  productName: string
  status: '품목일치' | '코드불일치' | '검색실패'
}

/** 레거시 가입고처리 품목 매핑 순서를 그대로 적용한다. */
export function mapInboundProduct(rawModel: string, cleanModel: string, catalog: InboundCatalogItem[]): InboundProductMapping {
  const exact = catalog.find((item) => item.productName === cleanModel || item.productCode === cleanModel)
  if (exact) {
    return { productCode: exact.productCode ?? cleanModel, productName: exact.productName, status: exact.productCode === cleanModel ? '품목일치' : '코드불일치' }
  }
  const partial = catalog.find((item) => item.productName.includes(cleanModel) || cleanModel.includes(item.productName))
  if (partial) return { productCode: partial.productCode ?? cleanModel, productName: partial.productName, status: '코드불일치' }
  const token = rawModel.split(' ')[0]
  const tokenMatch = catalog.find((item) => item.productCode === token)
  if (tokenMatch) return { productCode: tokenMatch.productCode ?? token, productName: tokenMatch.productName, status: '코드불일치' }
  return { productCode: cleanModel, productName: cleanModel, status: '검색실패' }
}
