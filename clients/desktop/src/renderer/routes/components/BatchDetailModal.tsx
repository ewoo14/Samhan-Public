/**
 * BatchDetailModal — LinkDispatchListPage 행 클릭 시 열리는 배치 상세 모달.
 *
 * notification-slice-B Designer wireframes.md § 2 + ux-flow.md § 1.
 *
 * 표시:
 * - 모달 헤더: 배송일자 + 기사명 + 기사 연락처 (PII 노출 — 사내 화면)
 * - signUrl + CopyButton (복사됨 토스트 3초)
 * - [토큰 재발행] ghost 버튼 — 클릭 시 confirm + regenerateBatchToken
 * - 슬립 리스트 표 (slipNo / 거래처 / 배송지 / 라인수 / [제거])
 *   - SMS 미발송 상태에서만 [제거] 버튼 활성 (BE 가드와 동일)
 * - 하단 [슬립 추가] — slipId 입력 폼 (UUID 입력은 사내 사용 한정)
 *
 * UUID 비공개 가드: batch.id / slipId 는 화면 표시 X — path 만 사용.
 * 슬립 추가 폼은 slipId 직접 입력 대신 slipNo 검색을 권장하나, 본 슬라이스는
 * MVP 로 slipNo 입력 → BE 가 slipNo → slipId 변환을 담당 (또는 추후 UI 보강).
 */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Button,
  CopyButton,
  Modal,
} from '@samhan/design-system'
import {
  addSlipToBatch,
  getBatch,
  regenerateBatchToken,
  removeSlipFromBatch,
} from '../../api/delivery'

export interface BatchDetailModalProps {
  /** 모달 표시 여부. */
  open: boolean
  /** 모달 닫기 핸들러. */
  onClose: () => void
  /** 대상 배치 UUID — null 이면 모달 미표시. */
  batchId: string | null
}

/**
 * "2026-05-04" → "2026-05-04 (월)" 등 — 미세 라벨링.
 * 본 슬라이스는 단순 일자 표기.
 */
function formatDate(date: string): string {
  return date
}

export function BatchDetailModal({ open, onClose, batchId }: BatchDetailModalProps) {
  const queryClient = useQueryClient()
  const [newSlipId, setNewSlipId] = useState('')

  const detailQuery = useQuery({
    queryKey: ['delivery-batch', batchId],
    queryFn: () => getBatch(batchId!),
    enabled: open && !!batchId,
  })

  const regenerateMutation = useMutation({
    mutationFn: () => regenerateBatchToken(batchId!),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['delivery-batch', batchId] })
      void queryClient.invalidateQueries({ queryKey: ['delivery-batches'] })
    },
  })

  const removeMutation = useMutation({
    mutationFn: (slipId: string) => removeSlipFromBatch(batchId!, slipId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['delivery-batch', batchId] })
      void queryClient.invalidateQueries({ queryKey: ['delivery-batches'] })
    },
  })

  const addMutation = useMutation({
    mutationFn: (slipId: string) => addSlipToBatch(batchId!, slipId),
    onSuccess: () => {
      setNewSlipId('')
      void queryClient.invalidateQueries({ queryKey: ['delivery-batch', batchId] })
      void queryClient.invalidateQueries({ queryKey: ['delivery-batches'] })
    },
  })

  const handleRegenerate = () => {
    if (!window.confirm('현재 e-sign URL 을 만료하고 새 토큰으로 발행합니다. 진행할까요?')) return
    regenerateMutation.mutate()
  }

  const handleRemoveSlip = (slipId: string, slipNo: string) => {
    if (!window.confirm(`슬립 [${slipNo}] 을 배치에서 제거하시겠습니까?`)) return
    removeMutation.mutate(slipId)
  }

  const handleAddSlip = () => {
    const trimmed = newSlipId.trim()
    if (!trimmed) return
    addMutation.mutate(trimmed)
  }

  const batch = detailQuery.data
  const isSent = !!batch?.smsSentAt
  const removable = !isSent

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title={
        batch
          ? `배송 묶음 — ${formatDate(batch.deliveryDate)} · ${batch.driverName}`
          : '배송 묶음'
      }
    >
      {detailQuery.isLoading ? (
        <p>불러오는 중...</p>
      ) : detailQuery.isError || !batch ? (
        <div className="error-banner" role="alert">
          배치 정보를 불러오지 못했습니다.
        </div>
      ) : (
        <div className="batch-detail">
          <div className="batch-detail-meta">
            <div>
              <span className="detail-label">기사 연락처</span>
              <span className="detail-value">{batch.driverPhone}</span>
            </div>
            <div>
              <span className="detail-label">슬립 수</span>
              <span className="detail-value">{batch.slipCount}건</span>
            </div>
            <div>
              <span className="detail-label">SMS</span>
              <span className="detail-value">
                {batch.smsSentAt
                  ? `발송 완료 (${batch.smsSentAt.slice(11, 16)})`
                  : '미발송'}
              </span>
            </div>
          </div>

          <div className="batch-detail-url-row">
            <span className="detail-label">e-sign URL</span>
            <span className="batch-detail-url">{batch.signUrl}</span>
            <CopyButton text={batch.signUrl} label="복사" />
            <Button
              variant="ghost"
              size="sm"
              onClick={handleRegenerate}
              loading={regenerateMutation.isPending}
            >
              토큰 재발행
            </Button>
          </div>

          <h4 style={{ marginTop: 24, marginBottom: 8 }}>묶인 슬립</h4>
          <table className="batch-slip-table">
            <thead>
              <tr>
                <th>슬립번호</th>
                <th>거래처</th>
                <th>배송지</th>
                <th>라인수</th>
                <th>액션</th>
              </tr>
            </thead>
            <tbody>
              {batch.slips.length === 0 ? (
                <tr>
                  <td colSpan={5} className="batch-slip-empty">
                    묶인 슬립이 없습니다.
                  </td>
                </tr>
              ) : (
                batch.slips.map((s) => (
                  <tr key={s.slipId}>
                    <td>{s.slipNo}</td>
                    <td>{s.partnerName ?? '-'}</td>
                    <td>{s.shippingAddress ?? '-'}</td>
                    <td className="num">{s.lineCount}</td>
                    <td>
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={!removable || removeMutation.isPending}
                        onClick={() => handleRemoveSlip(s.slipId, s.slipNo)}
                        title={removable ? undefined : 'SMS 발송 후에는 제거 불가'}
                      >
                        제거
                      </Button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>

          <div className="batch-add-slip-row">
            <input
              type="text"
              value={newSlipId}
              onChange={(e) => setNewSlipId(e.target.value)}
              placeholder="추가할 슬립 ID (사내 운영자 전용)"
              className="batch-add-slip-input"
            />
            <Button
              variant="secondary"
              size="sm"
              onClick={handleAddSlip}
              loading={addMutation.isPending}
              disabled={!newSlipId.trim()}
            >
              슬립 추가
            </Button>
          </div>

          {addMutation.isError ? (
            <div className="error-banner" role="alert" style={{ marginTop: 8 }}>
              슬립 추가에 실패했습니다.
            </div>
          ) : null}
          {removeMutation.isError ? (
            <div className="error-banner" role="alert" style={{ marginTop: 8 }}>
              슬립 제거에 실패했습니다.
            </div>
          ) : null}
        </div>
      )}
    </Modal>
  )
}
