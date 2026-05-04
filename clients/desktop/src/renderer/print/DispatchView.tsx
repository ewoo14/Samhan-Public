/**
 * 출고전표 작업지시서 인쇄 미리보기 — `/sales/:id/print/dispatch`.
 *
 * sales-form-polish 슬라이스 (v2): **세로 A4 (portrait)** 로 정정.
 * Designer `print-spec.md` § 2 + `wireframes.md` § 3 + `components.md` § 4 인용.
 *
 * 사용자 첨부 이미지 2 충실 반영:
 * - 좌측 SAMSUNG 로고 + 거래처명 + 일련번호 박스
 * - 우측 5칸 담당 박스 grid (담당부서/담당자/출고인/검수인/결재 full)
 * - 라인 표 — 모델명+품목명 2줄 셀 (image 2 매치)
 * - 배송지 / 연락처 / 특이사항 3 분리 섹션
 * - 안내: "기사님 출발전에 수요처에 전화주세요 ~ 감사합니다"
 * - 경고: "※ 제품 수량 및 이상 유무 확인 후 서명 必"
 * - 서명 박스 — 60mm × 40mm × 2 (용달기사 / 인수자)
 *
 * @page { size: A4 portrait; margin: 12mm } — global.css @media print 에 적용.
 *
 * UUID 비공개: 일련번호는 `slipDate - seqNo` 만 노출. 창고 코드/이름은 BE 응답
 * 에 별도 필드가 없으므로 본 슬라이스는 fallback 표기 ('-').
 */
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@samhan/design-system'
import { getSlip, type SlipDetail } from '../api/slip'

/**
 * `2026-05-04` → `{ month: '05', day: '04' }`. 표 라인 좌측 2 컬럼용.
 */
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

      <div className="dispatch-page">
        {/* 상단 헤더 — 좌측 브랜드/일련번호, 우측 5칸 담당 박스 */}
        <header className="dispatch-header">
          <div className="dispatch-brand">
            <div className="dispatch-logo-placeholder">SAMSUNG</div>
            <div className="dispatch-partner-name">{slip.partnerName ?? '-'}</div>
            <div className="dispatch-slip-no">
              {slip.slipDate} - {slip.seqNo}
            </div>
            <div className="dispatch-warehouse">
              출하창고: {slip.sourceWarehouseId ? '본사창고' : '-'}
            </div>
          </div>

          <section className="dispatch-roles" aria-label="담당자 및 결재">
            <div className="dispatch-role-box">
              <div className="dispatch-role-label">담당부서</div>
              <div className="dispatch-role-value">영업1팀</div>
            </div>
            <div className="dispatch-role-box">
              <div className="dispatch-role-label">담당자</div>
              <div className="dispatch-role-value">오병승</div>
            </div>
            <div className="dispatch-role-box">
              <div className="dispatch-role-label">출고인</div>
              <div className="dispatch-role-value"></div>
            </div>
            <div className="dispatch-role-box">
              <div className="dispatch-role-label">검수인</div>
              <div className="dispatch-role-value"></div>
            </div>
            <div className="dispatch-role-box full">
              <div className="dispatch-role-label">결재</div>
              <div className="dispatch-role-value"></div>
            </div>
          </section>
        </header>

        {/* 라인 표 — 모델명/품목명 2줄 셀 */}
        <table className="dispatch-table">
          <thead>
            <tr>
              <th className="col-month">월</th>
              <th className="col-day">일</th>
              <th>모델명 / 품목명</th>
              <th className="col-spec">규격</th>
              <th className="col-qty">수량</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {slip.lines.map((l) => (
              <tr key={l.id}>
                <td className="col-month">{month}</td>
                <td className="col-day">{day}</td>
                <td>
                  <div className="product-cell">
                    <span className="model-name">{l.modelName ?? '-'}</span>
                    <span className="product-name">{l.productName ?? ''}</span>
                  </div>
                </td>
                <td className="col-spec">-</td>
                <td className="col-qty num">{l.quantity.toLocaleString()}</td>
                <td></td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={4}>총합계</td>
              <td className="col-qty num">{totalQty.toLocaleString()}</td>
              <td></td>
            </tr>
          </tfoot>
        </table>

        {/* 배송지 / 연락처 / 특이사항 3 분리 섹션 */}
        <section className="dispatch-section">
          <div className="label">배송지</div>
          <div className="content">-</div>
        </section>
        <section className="dispatch-section">
          <div className="label">연락처</div>
          <div className="content">-</div>
        </section>
        <section className="dispatch-section">
          <div className="label">특이사항</div>
          <div className="content">{slip.memo ?? '-'}</div>
        </section>

        {/* 안내 / 경고 문구 */}
        <div className="dispatch-notice">
          기사님 출발전에 수요처에 전화주세요
          <br />~ 감사합니다 ^^
        </div>
        <div className="dispatch-warning">
          ※ 제품 수량 및 이상 유무 확인 후 서명 必
        </div>

        {/* 서명 박스 — 60mm × 40mm × 2 */}
        <section className="dispatch-signatures" aria-label="서명">
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
