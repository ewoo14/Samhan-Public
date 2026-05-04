/**
 * 출고전표 작업지시서 인쇄 미리보기 — `/sales/:id/print/dispatch`.
 *
 * PR #21 hotfix v2 — 개발책임자 첨부 이미지 기준 큰 재디자인.
 *
 * 변경 요점:
 * - 라인 표 4-col (모델명/품목명/규격/수량) — 월/일 열 제거 (사용자 명시)
 * - 헤더: SAMSUNG 로고 풀 스트립 + 큰 거래처명 박스 (좌) + 결재란 5칸 (우)
 * - 일련번호 박스 (좌) + 출하창고 (우, 빨강) — 창고명만 (코드 X)
 * - 배송지/연락처/특이사항 큰 박스
 * - "기사님 출발전에 수요처에 전화주세요~ 감사합니다^^" 가운데 안내
 * - "※ 제품수량 및 이상유무 확인 후 서명 必"
 * - 용달기사 서명 / 인수자 서명 — 박스 X, 라벨만
 * - 하단 안내문 "제품 인수시 ... 책임지지 않습니다."
 *
 * @page A4 portrait 12mm 여백 — global.css @media print 에 적용.
 *
 * UUID 비공개: 일련번호 `slipDate - seqNo` 만 노출. dispatcher.userId / inspector.userId
 * 는 부모로부터 받지만 화면 표시 X (이름만 표시). 출하창고 코드 미노출 (사용자 명시).
 */
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@samhan/design-system'
import { getSlip, type SlipDetail } from '../api/slip'
import { listWarehouses, type Warehouse } from '../api/inventory'
import { usePageTitle } from '../hooks/usePageTitle'

/**
 * "2026-05-04T14:32:18+09:00" → "14:32" (Designer print-spec.md § 3.4).
 * 빈 ISO 시 빈 문자열.
 */
function formatHHmm(iso: string | null | undefined): string {
  if (!iso) return ''
  return iso.slice(11, 16)
}

/**
 * `<RoleCell>` — 결재란 5칸 셀 (Designer components.md § 4.4).
 *
 * 출고인/검수인 셀은 value (이름) + time (HH:mm) 둘 다 표시.
 * 그 외 (담당부서/담당자/결재) 는 value 만.
 */
function RoleCell({
  label,
  value,
  time,
}: {
  label: string
  value?: string | null
  time?: string | null
}) {
  return (
    <div className="dispatch-role-cell">
      <div className="dispatch-role-label">{label}</div>
      <div className="dispatch-role-value">
        {value ? <span className="name">{value}</span> : null}
        {time ? <span className="time">{formatHHmm(time)}</span> : null}
      </div>
    </div>
  )
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
  const warehousesQuery = useQuery<Warehouse[]>({
    queryKey: ['warehouses'],
    queryFn: listWarehouses,
  })

  usePageTitle('출고전표 작업지시서', detailQuery.data?.slipNo)

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
  const totalQty = slip.lines.reduce((sum, l) => sum + l.quantity, 0)
  const sourceWarehouseName =
    warehousesQuery.data?.find((w) => w.id === slip.sourceWarehouseId)?.name ?? '-'

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
        <div className="dispatch-logo-strip">
          <span className="dispatch-logo-placeholder">SAMSUNG</span>
        </div>

        <header className="dispatch-header-row">
          <div className="dispatch-partner-name-box">
            {slip.partnerName ?? '-'}
          </div>
          <div className="dispatch-roles" aria-label="담당자 및 결재">
            <RoleCell label="담당부서" value={slip.ownerDepartment ?? null} />
            <RoleCell label="담당자" value={slip.ownerFullName ?? null} />
            <RoleCell
              label="출고인"
              value={slip.dispatcher?.fullName ?? null}
              time={slip.dispatcher?.signedAt ?? null}
            />
            <RoleCell
              label="검수인"
              value={slip.inspector?.fullName ?? null}
              time={slip.inspector?.signedAt ?? null}
            />
            <RoleCell label="결재" value="*" />
          </div>
        </header>

        <div className="dispatch-meta-row">
          <div className="dispatch-slip-no-box">
            {slip.slipDate} -{slip.seqNo}
          </div>
          <div className="dispatch-warehouse-emphasis">
            {sourceWarehouseName}
          </div>
        </div>

        <table className="dispatch-table">
          <thead>
            <tr>
              <th className="col-model">모델명</th>
              <th className="col-product">품목명</th>
              <th className="col-spec">규격</th>
              <th className="col-qty">수량</th>
            </tr>
          </thead>
          <tbody>
            {slip.lines.map((l) => (
              <tr key={l.id}>
                <td className="col-model">{l.modelName ?? '-'}</td>
                <td className="col-product">{l.productName ?? '-'}</td>
                <td className="col-spec">{l.specification || '-'}</td>
                <td className="col-qty">{l.quantity.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={3} className="total-label">총합계</td>
              <td className="col-qty total-qty">{totalQty.toLocaleString()}</td>
            </tr>
          </tfoot>
        </table>

        <div className="dispatch-bottom-group">
          <div className="dispatch-address-box">
            {slip.shippingAddress ?? '-'}
          </div>
          <div className="dispatch-info-box">
            <span className="label">연락처:</span>
            <span className="content">{slip.contactPhone ?? '-'}</span>
          </div>
          <div className="dispatch-info-box">
            <span className="label">특이사항:</span>
            <span className="content">{slip.memo ?? '-'}</span>
          </div>

          <p className="dispatch-driver-call-notice">
            기사님 출발전에 수요처에 전화주세요~ 감사합니다^^
          </p>
          <p className="dispatch-confirm-notice">
            ※ 제품수량 및 이상유무 확인 후 서명 必
          </p>

          <div className="dispatch-signatures" aria-label="서명">
            <div className="dispatch-sign-label-only">용달기사 서명</div>
            <div className="dispatch-sign-label-only">인수자 서명</div>
          </div>

          <p className="dispatch-liability-notice">
            제품 인수시 수량 제품상태 이상 유무 확인 후 서명 부탁드립니다.<br />
            서명 후 생긴 문제는 당사가 책임지지 않습니다.
          </p>
        </div>
      </div>
    </div>
  )
}
