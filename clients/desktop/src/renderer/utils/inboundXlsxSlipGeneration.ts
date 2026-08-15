import type { CreateSlipRequest } from '../api/slip'

export type InboundSlipGenerationRow = {
  sourceSheet: string
  sourceRow: number
  cleanModel: string
  productName: string
  productId: string | null
  status: '품목일치' | '코드불일치' | '검색실패'
  warehouseCode: string
  quantity: number
}

export type InboundSlipBatch = {
  warehouseCode: string
  chunkNumber: number
  idempotencyKey: string
  request: CreateSlipRequest
}

/** 가입고 유효행만 전표 payload로 만들고 창고별 100라인 상한을 명시적으로 분할한다. */
export function buildInboundSlipBatches(
  rows: InboundSlipGenerationRow[],
  fileHash: string,
  warehouseIdsByCode: Record<string, string>,
): InboundSlipBatch[] {
  const grouped = new Map<string, InboundSlipGenerationRow[]>()
  for (const row of rows) {
    if (row.status === '검색실패' || row.quantity <= 0 || !row.productId) continue
    const existing = grouped.get(row.warehouseCode) ?? []
    existing.push(row)
    grouped.set(row.warehouseCode, existing)
  }

  const batches: InboundSlipBatch[] = []
  for (const [warehouseCode, warehouseRows] of grouped) {
    const destinationWarehouseId = warehouseIdsByCode[warehouseCode]
    if (!destinationWarehouseId) continue
    for (let offset = 0, chunkNumber = 1; offset < warehouseRows.length; offset += 100, chunkNumber += 1) {
      const chunk = warehouseRows.slice(offset, offset + 100)
      const idempotencyKey = `inbound-xlsx:${fileHash}:${warehouseCode}:${chunkNumber}`
      batches.push({
        warehouseCode,
        chunkNumber,
        idempotencyKey,
        request: {
          slipType: 'INBOUND',
          destinationWarehouseId,
          partnerCode: '1248100998',
          partnerName: '삼성전자(주)',
          sourceType: 'INBOUND_XLSX',
          idempotencyKey,
          memo: `가입고 XLSX 원본 파일 SHA-256: ${fileHash}`,
          lines: chunk.map((row) => ({
            productId: row.productId!,
            productName: row.productName,
            modelName: row.cleanModel,
            quantity: row.quantity,
            unitPrice: '0',
            priceVatInclusive: false,
            note: `가입고 원본: ${fileHash} · ${row.sourceSheet}/${row.sourceRow}`,
          })),
        },
      })
    }
  }
  return batches
}
