/**
 * 전표 상세 + 라이프사이클 transition 화면 (출고/입고 공용).
 *
 * Slice A (sales-polish-2-slice) 갱신 — Designer `wireframes.md` § 5 충실 반영:
 * - 사용자 피드백 #1 ("라이프사이클" 모호) 해결 → `<ProgressBar>` 신규 컴포넌트로 대체
 *   ProgressBar 헤더 정보 위에 위치 (사용자 진입 시 즉시 단계 확인)
 *   기존 transition 버튼 영역은 "다음 단계 액션" 으로 ProgressBar 아래 유지
 * - 사용자 피드백 #9 — 결재 정보 카드 (출고인/검수인 자동 채움) 신규 표시
 * - INSPECTING 신규 단계 transition (`PROCESSING → INSPECTING → COMPLETED`) 지원
 * - usePageTitle 로 AppHeader 동적 화면명 ("출고전표 상세 [2026/05/04-1]")
 *
 * status 별 transition (Slice A 갱신 — INSPECTING 신규):
 * - DRAFT      → save / cancel
 * - SAVED      → send / cancel
 * - SENT       → accept / reject / cancel
 * - ACCEPTED   → process / reject
 * - PROCESSING → inspect (Slice A 신규 — 기존 complete 대신)
 * - INSPECTING → complete (Slice A 신규)
 * - COMPLETED  → ship (OUTBOUND) / confirm (INBOUND 즉시)
 * - SHIPPING   → deliver
 * - DELIVERED  → confirm (OUTBOUND)
 *
 * UUID 비공개 가드: id 는 path param 으로만 사용. 화면 표시 영역에는 노출 X.
 * dispatcher.userId / inspector.userId 도 화면 미노출 (이름만 표시).
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
  ProgressBar,
  SlipNumberDisplay,
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
import { usePageTitle } from '../hooks/usePageTitle'

export interface SlipDetailPageProps {
  /** OUTBOUND 또는 INBOUND — 라우트별 listPath 결정 + ship/deliver 노출 여부. */
  mode: SlipType
}

/**
 * status 별 가능 transition 액션 목록 (Slice A 갱신 — INSPECTING 신규).
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
      return ['inspect'] // Slice A: complete → inspect (검수 단계 거침)
    case 'INSPECTING':
      return ['complete'] // Slice A 신규
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
  inspect: '검수 시작', // Slice A 신규
  complete: '처리 완료',
  ship: '배송 시작',
  deliver: '배송 완료',
  confirm: '확정',
  reject: '반려',
  cancel: '취소',
}

/**
 * "2026-05-04T14:32:18+09:00" → "14:32" — Designer print-spec.md § 3.4.
 */
function formatHHmm(iso: string | null | undefined): string {
  if (!iso) return ''
  return iso.slice(11, 16)
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

  // Slice A: AppHeader 동적 화면명 — slipNo bracket meta (Designer wireframes.md § 1.3)
  usePageTitle(
    isOutbound ? '출고전표 상세' : '입고전표 상세',
    detailQuery.data?.slipNo,
  )

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
      key: 'specification',
      header: '규격',
      width: '100px',
      render: (l) => l.specification ?? '-',
    },
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

  // 분기 사유 (REJECTED 시 BE 가 응답에 reason 을 별도 필드로 줄 수 있음 — Slice A 는 memo 사용)
  const branchReason
    = slip.status === 'REJECTED' || slip.status === 'CANCELED'
      ? slip.memo ?? undefined
      : undefined

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
          <SlipNumberDisplay slipDate={slip.slipDate} seqNo={slip.seqNo} size="lg" />
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

      {/*
        Slice A: 전표 진행 단계 ProgressBar (Designer wireframes.md § 2 + 5)
        피드백 #1 ("라이프사이클" 모호) 해결.
      */}
      <div style={{ marginBottom: 16 }}>
        <ProgressBar currentStatus={slip.status} branchReason={branchReason} />
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

      {/*
        Slice A: 결재 정보 카드 — 출고인/검수인 자동 채움 (Designer wireframes.md § 5 + ux-flow.md § 2)
        피드백 #9 해결.
      */}
      <Card padding={4} shadow="sm" style={{ marginTop: 24 }}>
        <h4 style={{ marginTop: 0 }}>결재 정보</h4>
        <div className="detail-grid">
          <div>
            <span className="detail-label">출고인</span>
            <span className="detail-value">
              {slip.dispatcher?.fullName
                ? `${slip.dispatcher.fullName} · ${formatHHmm(slip.dispatcher.signedAt)}`
                : '미수락'}
            </span>
          </div>
          <div>
            <span className="detail-label">검수인</span>
            <span className="detail-value">
              {slip.inspector?.fullName
                ? `${slip.inspector.fullName} · ${formatHHmm(slip.inspector.signedAt)}`
                : '미검수'}
            </span>
          </div>
          <div>
            <span className="detail-label">담당부서</span>
            <span className="detail-value">{slip.ownerDepartment ?? '-'}</span>
          </div>
          <div>
            <span className="detail-label">담당자</span>
            <span className="detail-value">{slip.ownerFullName ?? '-'}</span>
          </div>
        </div>
      </Card>

      {/*
        다음 단계 액션 — ProgressBar 아래 (피드백 #1 — "전표 진행 단계" 헤더와 분리)
      */}
      <Card padding={4} shadow="sm" style={{ marginTop: 24 }}>
        <h4 style={{ marginTop: 0 }}>다음 단계 액션</h4>
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
