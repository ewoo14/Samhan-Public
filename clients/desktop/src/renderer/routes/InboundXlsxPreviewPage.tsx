import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { previewInboundXlsx, type InboundXlsxPreview, type InboundXlsxRow } from '../api/inboundXlsxApi'
import { listWarehouses } from '../api/inventory'
import { searchProducts } from '../api/productApi'
import { mapInboundProduct, type InboundProductMapping } from '../utils/inboundXlsxMapping'

type PreviewRow = InboundXlsxRow & InboundProductMapping

/** 가입고 XLSX → 정제 결과표. 이 라운드에서는 전표 생성 API를 호출하지 않는다. */
export function InboundXlsxPreviewPage() {
  const navigate = useNavigate()
  const [preview, setPreview] = useState<InboundXlsxPreview | null>(null)
  const [rows, setRows] = useState<PreviewRow[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [warehouseLabels, setWarehouseLabels] = useState<Record<string, string>>({})
  const [failedAcknowledged, setFailedAcknowledged] = useState(false)

  async function onFileChange(file?: File) {
    if (!file) return
    setLoading(true)
    setError('')
    try {
      const parsed = await previewInboundXlsx(file)
      const warehouses = await listWarehouses().catch(() => [])
      setWarehouseLabels(Object.fromEntries(warehouses.map((warehouse) => [warehouse.code, `${warehouse.code} · ${warehouse.name}`])))
      const cache = new Map<string, Awaited<ReturnType<typeof searchProducts>>>()
      const catalogFor = async (query: string) => {
        if (!query || cache.has(query)) return cache.get(query) ?? []
        const result = await searchProducts(query, { size: 100 })
        cache.set(query, result)
        return result
      }
      const mapped = await Promise.all(parsed.rows.map(async (row) => {
        const first = await catalogFor(row.cleanModel)
        const token = row.rawModel.split(' ')[0]
        const candidates = token && token !== row.cleanModel
          ? [...first, ...(await catalogFor(token))]
          : first
        const mapping = mapInboundProduct(row.rawModel, row.cleanModel, candidates.map((item) => ({
          productCode: item.productCode ?? null,
          productName: item.productName,
        })))
        return { ...row, ...mapping }
      }))
      setPreview(parsed)
      setRows(mapped)
      setFailedAcknowledged(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '가입고 XLSX 미리보기에 실패했습니다')
      setPreview(null)
      setRows([])
    } finally {
      setLoading(false)
    }
  }

  const failed = rows.filter((row) => row.status === '검색실패').length
  return (
    <main style={{ padding: 24, maxWidth: 1500, margin: '0 auto' }} data-testid="inbound-xlsx-preview-page">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16 }}>
        <div>
          <h1>가입고 XLSX 미리보기</h1>
          <p>레거시 규칙으로 파싱한 결과를 확인합니다. 이 화면에서는 입고전표를 생성하지 않습니다.</p>
        </div>
        <button type="button" onClick={() => navigate('/purchases/new')}>입고전표 작성으로 돌아가기</button>
      </div>
      <section style={{ padding: 16, border: '1px solid #ddd', borderRadius: 8, margin: '16px 0' }}>
        <label htmlFor="inbound-xlsx-file">가입고 XLSX 업로드 (.xlsx)</label>
        <input id="inbound-xlsx-file" data-testid="inbound-xlsx-file" type="file" accept=".xlsx" onChange={(event) => void onFileChange(event.target.files?.[0])} />
        {loading && <p role="status">파일을 읽고 품목을 검색하는 중입니다…</p>}
        {error && <p role="alert" style={{ color: '#b42318' }}>{error}</p>}
      </section>
      {preview && (
        <>
          <section aria-label="가입고 누락 요약" style={{ padding: 16, background: '#f7f8fa', borderRadius: 8 }}>
            <strong>결과 {rows.length}건</strong> · 검색실패 {failed}건 · 키워드 불일치 {preview.keywordFilteredRows}건 · 중복 상쇄 {preview.deduplicatedRows}건
            <div>짧아 건너뛴 시트: {preview.skippedShortSheets.join(', ') || '없음'} · 헤더 없어 건너뛴 시트: {preview.skippedHeaderSheets.join(', ') || '없음'}</div>
            <div>고정 거래처: 1248100998 (삼성전자(주)) · 단가: 0 · 전표 생성: 다음 라운드</div>
            <div>창고 연결: staging 별칭은 0건이므로 활성 warehouses.code(00003/2)를 직접 사용</div>
            <label style={{ display: 'block', marginTop: 8 }}><input type="checkbox" checked={failedAcknowledged} onChange={(event) => setFailedAcknowledged(event.target.checked)} /> 검색실패 {failed}건을 확인했으며 다음 라운드에서 제외할 행으로 표시했습니다.</label>
          </section>
          <div style={{ overflowX: 'auto', marginTop: 16 }}>
            <table style={{ borderCollapse: 'collapse', width: '100%', minWidth: 1250 }}>
              <thead><tr>{['상태', '시트/행', '고객명', '원본 모델', '품목명', '품목코드', '창고', '수량', '주문번호', '차량번호', '기사명', '주문일자'].map((header) => <th key={header} style={th}>{header}</th>)}</tr></thead>
              <tbody>{rows.map((row) => <tr key={`${row.sourceSheet}-${row.sourceRow}`} data-testid="inbound-xlsx-row" style={row.status === '검색실패' ? { background: '#fff1f0' } : undefined}>
                <td style={td} data-testid="inbound-xlsx-status">{row.status}</td><td style={td}>{row.sourceSheet}/{row.sourceRow}</td><td style={td}>{row.customerName}</td><td style={td}>{row.rawModel}</td><td style={td}>{row.productName}</td><td style={td}>{row.productCode}</td><td style={td} data-testid="inbound-xlsx-warehouse">{warehouseLabels[row.warehouseCode] ?? row.warehouseCode}</td><td style={td}>{row.quantity}</td><td style={td}>{row.orderNumber}</td><td style={td}>{row.vehicleNumber}</td><td style={td}>{row.driverName}</td><td style={td}>{row.orderDate}</td>
              </tr>)}</tbody>
            </table>
          </div>
        </>
      )}
    </main>
  )
}

const th = { textAlign: 'left' as const, padding: '8px 10px', borderBottom: '2px solid #bbb', whiteSpace: 'nowrap' as const }
const td = { padding: '8px 10px', borderBottom: '1px solid #e5e7eb', whiteSpace: 'nowrap' as const }
