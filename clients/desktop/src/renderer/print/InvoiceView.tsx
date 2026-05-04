/**
 * 거래명세서 인쇄 미리보기 — `/sales/:id/print/invoice`.
 *
 * 사용자 제공 양식 이미지 1 의 충실 반영. 본 컴포넌트는 디자인 시스템 외부
 * (별도 `src/renderer/print/`) 에서 자체 스타일로 관리한다 — 인쇄 양식이
 * 단순 정적 레이아웃이라 디자인 시스템 컴포넌트화는 권장하지 않음.
 *
 * 인쇄 동작:
 * - 화면 상단 "인쇄" 버튼 클릭 → `window.print()`
 * - @media print 시 사이드바/헤더/버튼 자동 숨김 (`global.css`)
 *
 * UUID 비공개: 일련번호는 `slipDate - seqNo` 형식만 노출 (전표 UUID 미사용).
 */
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@samhan/design-system'
import { getSlip, type SlipDetail } from '../api/slip'
import { usePageTitle } from '../hooks/usePageTitle'

/** 숫자를 한글 금액으로 변환 (간단판 — 천/만/억 단위만). */
function numberToKorean(n: number): string {
  if (n === 0) return '영원'
  const units = ['', '만', '억', '조']
  const digits = ['', '일', '이', '삼', '사', '오', '육', '칠', '팔', '구']
  const positions = ['', '십', '백', '천']
  let result = ''
  let unitIndex = 0
  let value = n
  while (value > 0) {
    const chunk = value % 10000
    if (chunk > 0) {
      let chunkStr = ''
      const chunkDigits = String(chunk).split('').reverse()
      for (let i = 0; i < chunkDigits.length; i += 1) {
        const d = Number(chunkDigits[i])
        if (d > 0) {
          const digitChar = digits[d] ?? ''
          const positionChar = positions[i] ?? ''
          chunkStr = digitChar + positionChar + chunkStr
        }
      }
      const unitChar = units[unitIndex] ?? ''
      result = chunkStr + unitChar + result
    }
    value = Math.floor(value / 10000)
    unitIndex += 1
  }
  return result + '원'
}

function lineDateParts(slipDate: string): { month: string; day: string } {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(slipDate)
  if (!m) return { month: '', day: '' }
  return { month: m[2]!, day: m[3]! }
}

export function InvoiceView() {
  const params = useParams<{ id: string }>()
  const id = params.id ?? ''
  const navigate = useNavigate()
  const detailQuery = useQuery({
    queryKey: ['slip', id],
    queryFn: () => getSlip(id),
    enabled: !!id,
  })

  // Slice A: AppHeader 동적 화면명 (Designer wireframes.md § 1.3)
  usePageTitle('거래명세서', detailQuery.data?.slipNo)

  if (!id) return null
  if (detailQuery.isLoading) return <p>불러오는 중...</p>
  if (detailQuery.isError || !detailQuery.data) {
    return (
      <div className="error-banner" role="alert">
        전표를 불러오지 못했습니다.
      </div>
    )
  }

  const slip: SlipDetail = detailQuery.data
  const { month, day } = lineDateParts(slip.slipDate)
  const totalQty = slip.lines.reduce((sum, l) => sum + l.quantity, 0)
  const totalSupply = slip.lines.reduce((sum, l) => sum + Number(l.lineTotal), 0)
  const totalVat = Math.floor(totalSupply * 0.1)
  const grandTotal = totalSupply + totalVat

  return (
    <div>
      <div className="no-print" style={{ marginBottom: 16, display: 'flex', gap: 8 }}>
        <Button variant="ghost" onClick={() => navigate(`/sales/${id}`)}>
          상세로 돌아가기
        </Button>
        <Button variant="primary" onClick={() => window.print()}>
          인쇄
        </Button>
      </div>

      <div className="print-page invoice-page">
        {/* 상단 헤더 */}
        <header className="invoice-header">
          <div className="invoice-brand">
            <div className="invoice-logo-placeholder">SAMSUNG</div>
            <div className="invoice-title">거래명세서</div>
          </div>
          <div className="invoice-meta">
            <div>일련번호: {slip.slipDate} - {slip.seqNo}</div>
            <div>TEL: 02-000-0000</div>
            <div>사업자등록번호: 000-00-00000</div>
            <div>성명: (주)삼한공조시스템</div>
            <div className="invoice-seal">[인]</div>
          </div>
        </header>

        {/* 공급자 / 공급받는자 */}
        <section className="invoice-parties">
          <div className="invoice-party">
            <h4>공급자</h4>
            <p>{slip.partnerName ?? '-'}</p>
            <p>주소: 경기도 ○○시</p>
            <p>연락처: 010-0000-0000</p>
          </div>
          <div className="invoice-party">
            <h4>공급받는자</h4>
            <p>(주)삼한공조시스템</p>
            <p>주소: 서울시 강남구 본사</p>
            <p>배송지: 본사 창고</p>
            <p>연락처: 02-000-0000</p>
          </div>
        </section>

        {/* 합계 금액 */}
        <section className="invoice-amount">
          <div className="invoice-amount-korean">합계 (한글): {numberToKorean(grandTotal)}</div>
          <div className="invoice-amount-number">합계 (숫자): ₩ {grandTotal.toLocaleString()}</div>
        </section>

        {/* 라인 표 */}
        <table className="invoice-table">
          <thead>
            <tr>
              <th>월</th>
              <th>일</th>
              <th>모델명</th>
              <th>품목명</th>
              <th>수량</th>
              <th>단가</th>
              <th>공급가액</th>
              <th>부가세</th>
            </tr>
          </thead>
          <tbody>
            {slip.lines.map((l) => {
              const supply = Number(l.lineTotal)
              const vat = Math.floor(supply * 0.1)
              return (
                <tr key={l.id}>
                  <td>{month}</td>
                  <td>{day}</td>
                  <td>{l.modelName ?? '-'}</td>
                  <td>{l.productName ?? '-'}</td>
                  <td className="num">{l.quantity.toLocaleString()}</td>
                  <td className="num">{Number(l.unitPrice).toLocaleString()}</td>
                  <td className="num">{supply.toLocaleString()}</td>
                  <td className="num">{vat.toLocaleString()}</td>
                </tr>
              )
            })}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={4}>합계</td>
              <td className="num">{totalQty.toLocaleString()}</td>
              <td></td>
              <td className="num">{totalSupply.toLocaleString()}</td>
              <td className="num">{totalVat.toLocaleString()}</td>
            </tr>
            <tr>
              <td colSpan={6}>합계 (공급가액 + 부가세)</td>
              <td className="num" colSpan={2}>{grandTotal.toLocaleString()}</td>
            </tr>
            <tr>
              <td colSpan={6}>인수자</td>
              <td colSpan={2}>(서명)</td>
            </tr>
          </tfoot>
        </table>

        <section className="invoice-footer">
          <div>입금주: (주)삼한공조시스템</div>
          <div>계좌: 국민은행 000-000000-00-000</div>
          <div>합계금액: ₩ {grandTotal.toLocaleString()}</div>
        </section>
      </div>
    </div>
  )
}
