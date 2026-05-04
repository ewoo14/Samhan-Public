/**
 * 거래명세서 인쇄 미리보기 — `/sales/:id/print/invoice`.
 *
 * PR #21 hotfix v2 — 개발책임자 첨부 이미지 기준 큰 재디자인.
 *
 * 변경 요점:
 * - 상단 좌: SAMSUNG 로고 + "거래명세서" 타이틀 + 공급받는자 박스 (파트너 정보)
 * - 상단 우: 공급자 5행 그리드 (일련번호/TEL/사업자등록번호/성명+직인/상호/주소)
 * - 중간: 배송지 + 금액 (한글 + 숫자)
 * - 라인 표 6-col (월/일|품목명|수량|단가|공급가액|부가세) — 작업지시서와 다르게 월/일 유지
 * - 푸터 합계 행: 수량 / 공급가액 / VAT / 합계 / 인수
 * - 예금주 + 은행 계좌 + 합계 한 줄
 *
 * @page A4 landscape 12mm 여백 — global.css @media print 에 적용.
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
  return result + '원 정'
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

      <div className="invoice-page">
        {/* 상단: 좌(로고+타이틀+공급받는자) | 우(공급자 5행) */}
        <header className="invoice-top">
          <div className="invoice-top-left">
            <span className="invoice-logo-placeholder">SAMSUNG</span>
            <div className="invoice-title">거래명세서</div>
            <div className="invoice-receiver-box">
              <div className="invoice-receiver-name">{slip.partnerName ?? '-'}님 中</div>
              <div className="invoice-receiver-address">
                서울특별시 강서구 마곡중앙로 161-8 (마곡동) 4층 403호, 404호
              </div>
              <div className="invoice-receiver-phone">☎ 010-9920-3468</div>
            </div>
          </div>

          <table className="invoice-supplier">
            <tbody>
              <tr>
                <td className="invoice-supplier-side" rowSpan={4}>공<br />급<br />자</td>
                <td className="invoice-supplier-label">일련번호</td>
                <td className="invoice-supplier-value">{slip.slipDate} - {slip.seqNo}</td>
                <td className="invoice-supplier-label">TEL</td>
                <td className="invoice-supplier-value">02-3461-XXXX</td>
              </tr>
              <tr>
                <td className="invoice-supplier-label">사업자등록번호</td>
                <td className="invoice-supplier-value">214-87-20659</td>
                <td className="invoice-supplier-label">성명</td>
                <td className="invoice-supplier-value seal-cell">
                  김미선
                  <span className="invoice-seal">[인]</span>
                </td>
              </tr>
              <tr>
                <td className="invoice-supplier-label">상호</td>
                <td className="invoice-supplier-value" colSpan={3}>(주)삼한공조시스템</td>
              </tr>
              <tr>
                <td className="invoice-supplier-label">주소</td>
                <td className="invoice-supplier-value" colSpan={3}>
                  서울특별시 서초구 마방로2길 9 (양재동) 삼한빌딩 4층
                </td>
              </tr>
            </tbody>
          </table>
        </header>

        {/* 배송지 / 금액 */}
        <div className="invoice-mid-row">
          <div className="invoice-shipping">
            <span className="label">배송지:</span>
            <span className="content">{slip.contactPhone ?? '010-0000-0000'} / {slip.shippingAddress ?? '-'}</span>
          </div>
          <div className="invoice-amount-row">
            <span className="label">금액:</span>
            <span className="korean">{numberToKorean(grandTotal)}</span>
            <span className="number">(₩ {grandTotal.toLocaleString()})</span>
          </div>
        </div>

        {/* 라인 표 6-col */}
        <table className="invoice-table">
          <thead>
            <tr>
              <th className="col-date">월/일</th>
              <th className="col-product">품목명</th>
              <th className="col-qty">수량</th>
              <th className="col-price">단가</th>
              <th className="col-supply">공급가액</th>
              <th className="col-vat">부가세</th>
            </tr>
          </thead>
          <tbody>
            {slip.lines.map((l) => {
              const supply = Number(l.lineTotal)
              const vat = Math.floor(supply * 0.1)
              const productLabel = l.modelName
                ? `${l.modelName}${l.productName ? ` (${l.productName})` : ''}`
                : (l.productName ?? '-')
              return (
                <tr key={l.id}>
                  <td className="col-date">{month}/{day}</td>
                  <td className="col-product">{productLabel}</td>
                  <td className="col-qty num">{l.quantity.toLocaleString()}</td>
                  <td className="col-price num">{Number(l.unitPrice).toLocaleString()}</td>
                  <td className="col-supply num">{supply.toLocaleString()}</td>
                  <td className="col-vat num">{vat.toLocaleString()}</td>
                </tr>
              )
            })}
          </tbody>
        </table>

        {/* 합계 행 (수량 / 공급가액 / VAT / 합계 / 인수) */}
        <div className="invoice-totals">
          <div className="invoice-total-cell">
            <span className="label">수량</span>
            <span className="value">{totalQty.toLocaleString()}</span>
          </div>
          <div className="invoice-total-cell">
            <span className="label">공급가액</span>
            <span className="value">{totalSupply.toLocaleString()}</span>
          </div>
          <div className="invoice-total-cell">
            <span className="label">VAT</span>
            <span className="value">{totalVat.toLocaleString()}</span>
          </div>
          <div className="invoice-total-cell strong">
            <span className="label">합계</span>
            <span className="value">{grandTotal.toLocaleString()}</span>
          </div>
          <div className="invoice-total-cell">
            <span className="label">인수</span>
            <span className="value">인</span>
          </div>
        </div>

        {/* 예금주 + 은행 계좌 + 합계 */}
        <div className="invoice-bank-row">
          <span>예금주: (주)삼한공조시스템 / 국민은행 750627-01-002557 &nbsp; 기업은행 010-3748-9937</span>
          <span className="invoice-bank-amount">{grandTotal.toLocaleString()}원</span>
        </div>
      </div>
    </div>
  )
}
