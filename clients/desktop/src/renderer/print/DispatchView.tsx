/**
 * 출고전표 작업지시서 인쇄 미리보기 — `/sales/:id/print/dispatch`.
 *
 * sales-polish-2-slice (Slice A) 큰 정정.
 * Designer `print-spec.md` § 3 + `wireframes.md` § 4 + `components.md` § 4 충실 반영.
 *
 * Slice A 정정 내용 (사용자 피드백 #3, #5, #6, #7, #8, #9):
 * - 결재란 1×5 horizontal grid (담당부서/담당자/출고인/검수인/결재) — 피드백 #7
 * - 결재란 출고인/검수인 셀 안에 BE 응답 dispatcher/inspector 자동 채움
 *   (이름 12pt + HH:mm 9pt) — 피드백 #9
 * - 라인 표 7-col (월/일/모델명/품목명/규격/수량) — 마지막 빈 열 제거 (#5)
 * - 모델명/품목명 한 행 좌우 분리 (1차 슬라이스 2줄 셀 X) — 피드백 #3
 * - 배송지/연락처/특이사항 14pt 본문 (라벨 12pt 700) — 피드백 #6
 * - 용달기사/인수자 서명 박스 80mm × 35mm 가로 나란히 + page-break-inside: avoid — 피드백 #8
 * - A4 portrait 273mm 본문 안에 모든 섹션 (잘리지 않음)
 *
 * @page A4 portrait 12mm 여백 — global.css @media print 에 적용.
 *
 * UUID 비공개: 일련번호 `slipDate - seqNo` 만 노출. dispatcher.userId / inspector.userId
 * 는 부모로부터 받지만 화면 표시 X (이름만 표시).
 */
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@samhan/design-system'
import { getSlip, type SlipDetail } from '../api/slip'
import { listWarehouses, type Warehouse } from '../api/inventory'
import { usePageTitle } from '../hooks/usePageTitle'

/**
 * `2026-05-04` → `{ month: '05', day: '04' }`. 표 라인 좌측 2 컬럼용.
 */
function lineDateParts(slipDate: string): { month: string; day: string } {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(slipDate)
  if (!m) return { month: '', day: '' }
  return { month: m[2]!, day: m[3]! }
}

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
 *
 * 미도달 단계 (예: ACCEPTED 미도달) — value/time 모두 빈 셀.
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
  // 출고창고 라벨 (피드백 #6: 붉은색 강조) — UUID 노출 안 하고 "코드 + 이름" 으로 (memory feedback_uuid_no_user_visibility)
  const warehousesQuery = useQuery<Warehouse[]>({
    queryKey: ['warehouses'],
    queryFn: listWarehouses,
  })

  // Slice A: AppHeader 동적 화면명 (Designer wireframes.md § 1.3)
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
        {/*
          상단 헤더 — 브랜드 영역. SAMSUNG 로고 + 가운데 거래처명 박스 (피드백 #4) +
          일련번호 우측. 결재란은 헤더 하단에 1×5 (피드백 #7 — 컴팩트 폭).
        */}
        <header className="dispatch-header">
          <div className="dispatch-brand">
            <span className="dispatch-logo-placeholder">SAMSUNG</span>
            <span className="dispatch-partner-name-box">
              {slip.partnerName ?? '-'}
            </span>
            <span className="dispatch-slip-no">
              {slip.slipDate} - {slip.seqNo}
            </span>
          </div>

          {/*
            결재란 1×5 horizontal grid — 컴팩트 폭 (피드백 #7).
            출고인/검수인 셀은 BE 응답 dispatcher/inspector 자동 채움 (피드백 #9).
          */}
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

        {/*
          라인 표 4-col (모델명/품목명/규격/수량) — 월/일 열 제거 (피드백 #3),
          규격 열 폭 확대 (피드백 #2/#3). 수량/합계 가운데 정렬 (피드백 #9/#10).
        */}
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
                <td className="col-model model-cell">{l.modelName ?? '-'}</td>
                <td className="col-product product-cell">{l.productName ?? '-'}</td>
                <td className="col-spec">{l.specification || '-'}</td>
                <td className="col-qty">{l.quantity.toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={3} className="total-label">합계</td>
              <td className="col-qty total-qty">{totalQty.toLocaleString()}</td>
            </tr>
          </tfoot>
        </table>

        {/*
          출고창고 (붉은색, 피드백 #6) → 배송지/연락처/특이사항 (14pt 본문, 피드백 #6) →
          출발 전 안내 (피드백 #5: "출발 전 반드시 인수자 연락 필수!") →
          서명 박스 (피드백 #7: 작게) — 푸터 삭제 (피드백 #8).
          page-break-inside: avoid 로 묶음.
        */}
        <div className="dispatch-bottom-group">
          <section className="dispatch-section">
            <p>
              <span className="label">배송지:</span>
              <span className="content">{slip.shippingAddress ?? '-'}</span>
            </p>
            <p>
              <span className="label">연락처:</span>
              <span className="content">{slip.contactPhone ?? '-'}</span>
            </p>
            <p>
              <span className="label">특이사항:</span>
              <span className="content">{slip.memo ?? '-'}</span>
            </p>
            <p className="dispatch-warehouse-emphasis">
              출하창고: {(() => {
                const w = warehousesQuery.data?.find((x) => x.id === slip.sourceWarehouseId)
                return w ? `${w.code} ${w.name}` : '-'
              })()}
            </p>
            <p className="depart-notice">출발 전 반드시 인수자 연락 필수!</p>
          </section>

          {/*
            서명 박스 (피드백 #7: 작게) — 가로 나란히. Slice A: 빈 박스 + placeholder.
            Slice C 에서 모바일 서명 PNG 자동 삽입.
          */}
          <div className="dispatch-signatures" aria-label="서명">
            <div className="dispatch-sign-box">
              <div className="dispatch-sign-label">용달기사 서명</div>
              <div className="dispatch-sign-area">
                <span className="placeholder">(서명 대기 — Slice C)</span>
              </div>
            </div>
            <div className="dispatch-sign-box">
              <div className="dispatch-sign-label">인수자 서명</div>
              <div className="dispatch-sign-area">
                <span className="placeholder">(서명 대기 — Slice C)</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
