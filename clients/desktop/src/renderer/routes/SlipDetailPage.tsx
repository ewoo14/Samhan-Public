/**
 * 전표 상세 + 라이프사이클 transition 화면 (출고/입고 공용).
 *
 * 본 슬라이스 (slip-output-format) Q8=A 결정에 따라 status 별 transition 버튼을
 * 모두 노출 (총 10 액션):
 * - DRAFT      → save / cancel
 * - SAVED      → send / cancel
 * - SENT       → accept / reject / cancel
 * - ACCEPTED   → process / reject
 * - PROCESSING → complete
 * - COMPLETED  → ship (OUTBOUND) / confirm (INBOUND 즉시)
 * - SHIPPING   → deliver
 * - DELIVERED  → confirm (OUTBOUND)
 *
 * 권한 부족 액션은 button disable. 성공 시 React Query invalidate.
 *
 * UUID 비공개 가드: id 는 path param 으로만 사용. 화면 표시 영역에는 노출 X.
 * lineId / productId 도 화면 미노출 (모델명 + 품목명만).
 */
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  Button,
  Card,
  DataTable,
  SlipNumberDisplay,
  SlipStatusBadge,
  type DataTableColumn,
} from '@samhan/design-system'
import axios from 'axios'
import {
  getSlip,
  transitionSlip,
  type SlipDetail,
  type SlipLineDetail,
  type SlipTransitionAction,
  type SlipType,
} from '../api/slip'
import { useSessionStore, canTransitionSlip } from '../stores/session'

export interface SlipDetailPageProps {
  /** OUTBOUND 또는 INBOUND — 라우트별 listPath 결정 + ship/deliver 노출 여부. */
  mode: SlipType
}

/**
 * status 별 가능 transition 액션 목록.
 * OUTBOUND/INBOUND 차이 (ship/deliver 는 출고전표 한정) 는 mode 로 필터.
 */
function actionsForStatus(
  status: SlipDetail['status'],
  mode: SlipType,
): SlipTransitionAction[] {
  switch (status) {
    case 'DRAFT':
      return ['save', 'cancel']
    case 'SAVED':
      return ['send', 'cancel']
    case 'SENT':
      return ['accept', 'reject', 'cancel']
    case 'ACCEPTED':
      return ['process', 'reject']
    case 'PROCESSING':
      return ['complete']
    case 'COMPLETED':
      return mode === 'OUTBOUND' ? ['ship'] : ['confirm']
    case 'SHIPPING':
      return mode === 'OUTBOUND' ? ['deliver'] : []
    case 'DELIVERED':
      return mode === 'OUTBOUND' ? ['confirm'] : []
    default:
      return []
  }
}

const ACTION_LABEL: Record<SlipTransitionAction, string> = {
  save: '저장',
  send: '전송',
  accept: '수락',
  process: '처리 시작',
  complete: '처리 완료',
  ship: '배송 시작',
  deliver: '배송 완료',
  confirm: '확정',
  reject: '반려',
  cancel: '취소',
}

export function SlipDetailPage({ mode }: SlipDetailPageProps) {
  const params = useParams<{ id: string }>()
  const id = params.id ?? ''
  const navigate = useNavigate()
  const role = useSessionStore((s) => s.auth?.role)
  const queryClient = useQueryClient()
  const isOutbound = mode === 'OUTBOUND'
  const listPath = isOutbound ? '/sales' : '/purchases'

  const [rejectReason, setRejectReason] = useState('')

  const detailQuery = useQuery({
    queryKey: ['slip', id],
    queryFn: () => getSlip(id),
    enabled: !!id,
  })

  const transitionMutation = useMutation({
    mutationFn: (vars: { action: SlipTransitionAction; reason?: string }) =>
      transitionSlip(id, vars.action, vars.reason ? { reason: vars.reason } : undefined),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['slip', id] })
      void queryClient.invalidateQueries({ queryKey: ['slips'] })
      setRejectReason('')
    },
  })

  if (!id) return null

  if (detailQuery.isLoading) {
    return <p>불러오는 중...</p>
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <div className="error-banner" role="alert">
        전표를 불러오지 못했습니다.
      </div>
    )
  }

  const slip = detailQuery.data
  const possibleActions = actionsForStatus(slip.status, mode)

  const lineColumns: DataTableColumn<SlipLineDetail>[] = [
    { key: 'modelName', header: '모델명', width: '180px', render: (l) => l.modelName ?? '-' },
    { key: 'productName', header: '품목명', render: (l) => l.productName ?? '-' },
    {
      key: 'quantity',
      header: '수량',
      width: '80px',
      align: 'right',
      render: (l) => l.quantity.toLocaleString(),
    },
    {
      key: 'unitPrice',
      header: '단가',
      width: '120px',
      align: 'right',
      render: (l) => Number(l.unitPrice).toLocaleString(),
    },
    {
      key: 'lineTotal',
      header: '합계',
      width: '140px',
      align: 'right',
      render: (l) => Number(l.lineTotal).toLocaleString(),
    },
  ]

  const errorMessage = (() => {
    if (!transitionMutation.isError) return null
    const err = transitionMutation.error
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string } | undefined
      return data?.message ?? '전이에 실패했습니다.'
    }
    return '알 수 없는 오류'
  })()

  const handleTransition = (action: SlipTransitionAction) => {
    if (action === 'reject') {
      const reason = rejectReason.trim()
      if (!reason) {
        alert('반려 사유를 입력하세요.')
        return
      }
      transitionMutation.mutate({ action, reason })
    } else {
      transitionMutation.mutate({ action })
    }
  }

  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
          <h3 style={{ margin: 0 }}>{isOutbound ? '출고전표 상세' : '입고전표 상세'}</h3>
          <SlipNumberDisplay slipDate={slip.slipDate} seqNo={slip.seqNo} size="lg" />
          <SlipStatusBadge status={slip.status} showStep />
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {isOutbound ? (
            <>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => navigate(`/sales/${id}/print/invoice`)}
              >
                거래명세서
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => navigate(`/sales/${id}/print/dispatch`)}
              >
                작업지시서
              </Button>
            </>
          ) : null}
          <Button variant="ghost" onClick={() => navigate(listPath)}>
            목록으로
          </Button>
        </div>
      </div>

      <Card padding={4} shadow="sm">
        <div className="detail-grid">
          <div>
            <span className="detail-label">거래처</span>
            <span className="detail-value">{slip.partnerName ?? '-'}</span>
          </div>
          <div>
            <span className="detail-label">일자</span>
            <span className="detail-value">{slip.slipDate}</span>
          </div>
          <div>
            <span className="detail-label">배송 태그</span>
            <span className="detail-value">{slip.deliveryTag ?? '-'}</span>
          </div>
          <div>
            <span className="detail-label">메모</span>
            <span className="detail-value">{slip.memo ?? '-'}</span>
          </div>
        </div>
      </Card>

      <h4 style={{ marginTop: 24 }}>전표 라인</h4>
      <DataTable
        columns={lineColumns}
        rows={slip.lines}
        rowKey={(l) => l.id}
        emptyMessage="라인이 없습니다."
      />

      <Card padding={4} shadow="sm" style={{ marginTop: 24 }}>
        <h4 style={{ marginTop: 0 }}>라이프사이클</h4>
        {possibleActions.length === 0 ? (
          <p style={{ color: 'var(--color-neutral-500)', margin: 0 }}>
            현재 상태에서 가능한 전이가 없습니다.
          </p>
        ) : (
          <>
            {possibleActions.includes('reject') ? (
              <div style={{ marginBottom: 12 }}>
                <input
                  type="text"
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="반려 사유 (반려 시 필수)"
                  maxLength={500}
                  style={{
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid var(--color-neutral-300)',
                    fontSize: 14,
                    width: '100%',
                  }}
                />
              </div>
            ) : null}
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {possibleActions.map((action) => {
                const allowed = canTransitionSlip(action, role)
                const variant
                  = action === 'reject' || action === 'cancel'
                    ? 'ghost'
                    : action === 'confirm'
                      ? 'primary'
                      : 'secondary'
                return (
                  <Button
                    key={action}
                    variant={variant}
                    size="sm"
                    disabled={!allowed || transitionMutation.isPending}
                    onClick={() => handleTransition(action)}
                  >
                    {ACTION_LABEL[action]}
                    {!allowed ? ' (권한 부족)' : ''}
                  </Button>
                )
              })}
            </div>
          </>
        )}
        {errorMessage ? (
          <div className="error-banner" role="alert" style={{ marginTop: 12 }}>
            {errorMessage}
          </div>
        ) : null}
      </Card>
    </>
  )
}
