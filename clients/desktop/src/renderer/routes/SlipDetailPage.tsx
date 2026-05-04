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
  ProgressBar,
  SlipNumberDisplay,
} from '@samhan/design-system'
import axios from 'axios'
import {
  duplicateSlip,
  getSlip,
  removeLine,
  transitionSlip,
  type SlipDetail,
  type SlipTransitionAction,
  type SlipType,
} from '../api/slip'
import { fetchStockBalanceBatch } from '../api/inventory'
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
  /** 좌측 넘버링 클릭으로 선택된 라인 ID — 선택 시 상단 툴바 표시. */
  const [selectedLineId, setSelectedLineId] = useState<string | null>(null)

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

  /** 라인 제거 (BE: DELETE /slips/{id}/lines/{lineId}). DRAFT/SAVED 만 허용. */
  const removeLineMutation = useMutation({
    mutationFn: (lineId: string) => removeLine(id, lineId),
    onSuccess: () => {
      setSelectedLineId(null)
      void queryClient.invalidateQueries({ queryKey: ['slip', id] })
    },
  })

  /** 전표 복사 (DRAFT 신규 생성). 성공 시 신규 전표 상세로 이동. */
  const duplicateMutation = useMutation({
    mutationFn: () => {
      if (!detailQuery.data) throw new Error('전표 데이터 없음')
      return duplicateSlip(detailQuery.data)
    },
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ['slips'] })
      const target = created.slipType === 'OUTBOUND' ? 'sales' : 'purchases'
      navigate(`/${target}/${created.id}`)
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

  /** 라인 편집은 DRAFT/SAVED 만 허용 (BE 가드와 동일). */
  const linesEditable = slip.status === 'DRAFT' || slip.status === 'SAVED'
  const selectedLine = selectedLineId
    ? slip.lines.find((l) => l.id === selectedLineId) ?? null
    : null

  /** 선택된 라인의 product 재고를 batch endpoint 로 조회 후 alert. */
  const handleStockQuery = async () => {
    if (!selectedLine) return
    try {
      const res = await fetchStockBalanceBatch([selectedLine.productId])
      const row = res.rows[0]
      if (!row) {
        alert(`${selectedLine.modelName ?? '-'} 재고 정보 없음`)
        return
      }
      const perWh = Object.entries(row.perWarehouse)
        .map(([code, qty]) => `${code}: ${qty == null ? '가상' : qty.toLocaleString()}`)
        .join('\n')
      alert(`[재고 조회] ${row.modelName}\n총합: ${row.total.toLocaleString()}\n\n${perWh}`)
    } catch (err) {
      alert('재고 조회 실패')
    }
  }

  /** 행 삭제 — 경고창 후 BE DELETE. */
  const handleRemoveLine = () => {
    if (!selectedLine) return
    if (!linesEditable) {
      alert(`라인 편집은 작성 중/저장 단계에서만 가능합니다. (현재: ${slip.status})`)
      return
    }
    if (!window.confirm(`[${selectedLine.modelName ?? '-'}] 라인을 삭제하시겠습니까?`)) {
      return
    }
    removeLineMutation.mutate(selectedLine.id)
  }

  /** 첫 가능한 정상 transition (reject/cancel 제외) — 하단 "완료" 버튼이 호출. */
  const nextPrimaryAction
    = possibleActions.find((a) => a !== 'reject' && a !== 'cancel') ?? null

  /** 하단 "전표 복사" — 사용자 확인 후 신규 DRAFT 생성. */
  const handleDuplicate = () => {
    if (!window.confirm('현재 전표를 복사하여 새 작성중 전표를 생성합니다. 진행할까요?')) {
      return
    }
    duplicateMutation.mutate()
  }

  /** 하단 "삭제" — 경고창 후 cancel transition (BE soft-delete). */
  const handleDeleteSlip = () => {
    if (!possibleActions.includes('cancel')) {
      alert(`현재 단계(${slip.status})에서는 삭제(취소)할 수 없습니다.`)
      return
    }
    if (!window.confirm('정말로 이 전표를 삭제하시겠습니까?\n\n이 작업은 되돌릴 수 없으며, 전표가 취소 상태로 변경됩니다.')) {
      return
    }
    transitionMutation.mutate({ action: 'cancel' })
  }

  /** 하단 "완료" — 다음 정상 단계 transition 실행. */
  const handleAdvanceStage = () => {
    if (!nextPrimaryAction) return
    transitionMutation.mutate({ action: nextPrimaryAction })
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

      {/*
        선택된 라인 액션 툴바 — 좌측 넘버링 클릭으로 행 선택 시 표시.
        재고조회 (모든 단계) / 행 추가·삭제·순서수정 (DRAFT/SAVED 만, BE 가드와 동일).
      */}
      {selectedLine ? (
        <div className="slip-line-toolbar" role="toolbar" aria-label="선택 라인 액션">
          <span className="slip-line-toolbar-label">
            선택: <strong>#{slip.lines.findIndex((l) => l.id === selectedLine.id) + 1}</strong>{' '}
            {selectedLine.modelName ?? '-'}
          </span>
          <Button size="sm" variant="secondary" onClick={() => void handleStockQuery()}>
            재고 조회
          </Button>
          <Button
            size="sm"
            variant="secondary"
            disabled={!linesEditable}
            onClick={() => alert('행 추가 — SlipFormPage 에서 편집해주세요 (DRAFT/SAVED 만 BE 허용).')}
            title={linesEditable ? undefined : '작성 중/저장 단계에서만 가능'}
          >
            행 추가
          </Button>
          <Button
            size="sm"
            variant="secondary"
            disabled={!linesEditable}
            onClick={() => alert('행 순서 수정 — SlipFormPage 의 drag-and-drop 사용해주세요.')}
            title={linesEditable ? undefined : '작성 중/저장 단계에서만 가능'}
          >
            순서 수정
          </Button>
          <Button
            size="sm"
            variant="ghost"
            disabled={!linesEditable || removeLineMutation.isPending}
            onClick={handleRemoveLine}
            title={linesEditable ? undefined : '작성 중/저장 단계에서만 가능'}
          >
            행 삭제
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setSelectedLineId(null)}>
            선택 해제
          </Button>
        </div>
      ) : (
        <p className="slip-line-hint">
          좌측 번호를 클릭하면 해당 라인을 선택할 수 있습니다 (재고 조회 / 순서 수정 / 추가 / 삭제).
        </p>
      )}

      <table className="slip-line-table">
        <thead>
          <tr>
            <th className="col-no">#</th>
            <th className="col-model">모델명</th>
            <th className="col-product">품목명</th>
            <th className="col-spec">규격</th>
            <th className="col-qty">수량</th>
            <th className="col-price">단가</th>
            <th className="col-total">합계</th>
          </tr>
        </thead>
        <tbody>
          {slip.lines.length === 0 ? (
            <tr>
              <td colSpan={7} className="slip-line-empty">라인이 없습니다.</td>
            </tr>
          ) : (
            slip.lines.map((l, idx) => {
              const selected = selectedLineId === l.id
              return (
                <tr key={l.id} className={selected ? 'is-selected' : undefined}>
                  <td className="col-no">
                    <button
                      type="button"
                      className={`slip-line-no-btn${selected ? ' is-selected' : ''}`}
                      aria-pressed={selected}
                      aria-label={`라인 ${idx + 1} 선택`}
                      onClick={() => setSelectedLineId(selected ? null : l.id)}
                    >
                      {idx + 1}
                    </button>
                  </td>
                  <td className="col-model">{l.modelName ?? '-'}</td>
                  <td className="col-product">{l.productName ?? '-'}</td>
                  <td className="col-spec">{l.specification ?? '-'}</td>
                  <td className="col-qty">{l.quantity.toLocaleString()}</td>
                  <td className="col-price">{Number(l.unitPrice).toLocaleString()}</td>
                  <td className="col-total">{Number(l.lineTotal).toLocaleString()}</td>
                </tr>
              )
            })
          )}
        </tbody>
      </table>

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
        반려 사유 입력 (필요 시) — 반려 가능 단계 (SENT/ACCEPTED) 에서 표시.
      */}
      {possibleActions.includes('reject') ? (
        <Card padding={4} shadow="sm" style={{ marginTop: 24 }}>
          <h4 style={{ marginTop: 0 }}>반려 사유</h4>
          <input
            type="text"
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            placeholder="반려 사유 (반려 시 필수, 최대 500자)"
            maxLength={500}
            style={{
              padding: '8px 12px',
              borderRadius: 6,
              border: '1px solid var(--color-neutral-300)',
              fontSize: 14,
              width: '100%',
            }}
          />
          <div style={{ marginTop: 8 }}>
            <Button
              variant="ghost"
              size="sm"
              disabled={!canTransitionSlip('reject', role) || transitionMutation.isPending}
              onClick={() => handleTransition('reject')}
            >
              {ACTION_LABEL['reject']}
              {!canTransitionSlip('reject', role) ? ' (권한 부족)' : ''}
            </Button>
          </div>
        </Card>
      ) : null}

      {/*
        하단 액션 버튼 (사용자 명시) — 전표 복사 / 삭제 (경고창 필수) / 완료 (다음 단계).
      */}
      <div className="slip-detail-footer-actions" role="toolbar" aria-label="전표 액션">
        <Button
          variant="secondary"
          disabled={duplicateMutation.isPending}
          onClick={handleDuplicate}
        >
          전표 복사
        </Button>
        <Button
          variant="ghost"
          disabled={!possibleActions.includes('cancel') || transitionMutation.isPending}
          onClick={handleDeleteSlip}
          title={possibleActions.includes('cancel') ? undefined : '현재 단계에서는 삭제(취소) 불가'}
        >
          삭제
        </Button>
        <Button
          variant="primary"
          disabled={
            !nextPrimaryAction
            || !canTransitionSlip(nextPrimaryAction, role)
            || transitionMutation.isPending
          }
          onClick={handleAdvanceStage}
          title={
            nextPrimaryAction
              ? `다음 단계: ${ACTION_LABEL[nextPrimaryAction]}`
              : '현재 단계에서 진행 가능한 다음 단계가 없습니다'
          }
        >
          {nextPrimaryAction ? `완료 (${ACTION_LABEL[nextPrimaryAction]})` : '완료'}
        </Button>
      </div>

      {errorMessage ? (
        <div className="error-banner" role="alert" style={{ marginTop: 12 }}>
          {errorMessage}
        </div>
      ) : null}
    </>
  )
}
