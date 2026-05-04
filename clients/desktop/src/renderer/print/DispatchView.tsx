/**
 * 출고전표 작업지시서 인쇄 미리보기 — `/sales/:id/print/dispatch`.
 *
 * 사용자 제공 양식 이미지 2 의 충실 반영. 차량 기사 / 인수자 서명란은
 * 빈 박스로 출력 (종이 출력 후 직접 서명). 디지털 서명 링크는 후속 슬라이스.
 *
 * UUID 비공개: 일련번호는 `slipDate - seqNo` 만 노출.
 */
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@samhan/design-system'
import { getSlip, type SlipDetail } from '../api/slip'

function lineDateParts(slipDate: string): { month: string; day: string } {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(slipDate)
  if (!m) return { month: '', day: '' }
  return { month: m[2]!, day: m[3]! }
}

export function DispatchView() {
  const params = useParams<{ id: string }>()
  const id = params.id ?? ''
  const navigate = useNavigate()
  const detailQuery = useQuery({
    queryKey: ['slip', id],
    queryFn: () => getSlip(id),
    enabled: !!id,
  })

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

      <div className="print-page dispatch-page">
        {/* 상단 헤더 */}
        <header className="dispatch-header">
          <div className="dispatch-brand">
            <div className="dispatch-logo-placeholder">SAMSUNG</div>
            <div className="dispatch-title">출고전표 작업지시서</div>
          </div>
          <div className="dispatch-meta">
            <div>거래처명: {slip.partnerName ?? '-'}</div>
            <div>일련번호: {slip.slipDate} - {slip.seqNo}</div>
            <div>출하창고: {slip.sourceWarehouseId ? '본사창고' : '-'}</div>
          </div>
        </header>

        {/* 담당 박스 */}
        <section className="dispatch-roles">
          <div className="dispatch-role-box">
            <div className="dispatch-role-label">담당부서</div>
            <div className="dispatch-role-value">영업팀</div>
          </div>
          <div className="dispatch-role-box">
            <div className="dispatch-role-label">담당자</div>
            <div className="dispatch-role-value"></div>
          </div>
          <div className="dispatch-role-box">
            <div className="dispatch-role-label">출고인</div>
            <div className="dispatch-role-value"></div>
          </div>
          <div className="dispatch-role-box">
            <div className="dispatch-role-label">검수인</div>
            <div className="dispatch-role-value"></div>
          </div>
          <div className="dispatch-role-box">
            <div className="dispatch-role-label">결제</div>
            <div className="dispatch-role-value"></div>
          </div>
        </section>

        {/* 라인 표 */}
        <table className="dispatch-table">
          <thead>
            <tr>
              <th>월</th>
              <th>일</th>
              <th>모델명</th>
              <th>품목명</th>
              <th>규격</th>
              <th>수량</th>
            </tr>
          </thead>
          <tbody>
            {slip.lines.map((l) => (
              <tr key={l.id}>
                <td>{month}</td>
                <td>{day}</td>
                <td>{l.modelName ?? '-'}</td>
                <td>{l.productName ?? '-'}</td>
                <td>-</td>
                <td className="num">{l.quantity.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={5}>총합계</td>
              <td className="num">{totalQty.toLocaleString()}</td>
            </tr>
          </tfoot>
        </table>

        <section className="dispatch-delivery">
          <div>배송지: -</div>
          <div>연락처: -</div>
          <div>특이사항: {slip.memo ?? '-'}</div>
        </section>

        <section className="dispatch-notice">
          기사님 출발전에 수요처에 전화주세요~ 감사합니다^^
        </section>

        <section className="dispatch-signatures">
          <div className="dispatch-sign-box">
            <div className="dispatch-sign-label">용달기사 서명</div>
            <div className="dispatch-sign-area"></div>
          </div>
          <div className="dispatch-sign-box">
            <div className="dispatch-sign-label">인수자 서명</div>
            <div className="dispatch-sign-area"></div>
          </div>
        </section>
      </div>
    </div>
  )
}
