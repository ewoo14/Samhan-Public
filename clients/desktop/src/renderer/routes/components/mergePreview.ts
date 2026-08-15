import type { PartnerOrderDetail, PartnerOrderLine } from '../../api/sales'

export interface MergedPreviewLine extends PartnerOrderLine {
  sourceOrderNumbers: string[]
}

/**
 * 병합 미리보기 규칙: 품목 외 헤더는 첫 주문을 따르고, 모델과 단가가
 * 모두 같은 라인은 뒤(나중) 행 위치에서 수량만 합산한다.
 */
export function buildMergedPreview(orders: PartnerOrderDetail[]): {
  header: PartnerOrderDetail | undefined
  lines: MergedPreviewLine[]
} {
  const lines: MergedPreviewLine[] = []
  const keyToIndex = new Map<string, number>()

  orders.forEach((order) => {
    order.lines.forEach((line) => {
      const sourceLine = line as PartnerOrderLine
      const key = `${sourceLine.modelCode}\u0000${sourceLine.deliveryPrice}`
      const existingIndex = keyToIndex.get(key)
      if (existingIndex === undefined) {
        keyToIndex.set(key, lines.length)
        lines.push({ ...sourceLine, sourceOrderNumbers: [order.orderNumber] })
        return
      }
      const existing = lines[existingIndex]!
      lines[existingIndex] = {
        ...existing,
        quantity: existing.quantity + sourceLine.quantity,
        subtotal: existing.subtotal + sourceLine.subtotal,
        sourceOrderNumbers: [...existing.sourceOrderNumbers, order.orderNumber],
      }
    })
  })

  return { header: orders[0], lines }
}
