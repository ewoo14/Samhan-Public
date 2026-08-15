import { apiClient, type ApiEnvelope } from './client'

export type InboundXlsxRow = {
  sourceSheet: string
  sourceRow: number
  no: string
  customerName: string
  rawModel: string
  cleanModel: string
  orderQuantityRaw: string
  deliveryExpected: string
  outboundQuantityRaw: string
  progressStatus: string
  vehicleNumber: string
  driverName: string
  orderDate: string
  orderNumber: string
  warehouseCode: string
  quantity: number
}

export type InboundXlsxPreview = {
  rows: InboundXlsxRow[]
  skippedShortSheets: string[]
  skippedHeaderSheets: string[]
  keywordFilteredRows: number
  deduplicatedRows: number
}

export async function previewInboundXlsx(file: File): Promise<InboundXlsxPreview> {
  const form = new FormData()
  form.append('file', file)
  const response = await apiClient.post<ApiEnvelope<InboundXlsxPreview>>('/warehouse/inbound-xlsx/preview', form)
  return response.data.data
}
