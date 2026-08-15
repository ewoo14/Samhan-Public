import { Badge, Card, OrderStatusBadge } from '@samhan/design-system'
import type { ReactNode } from 'react'
import type { PartnerOrderDetail } from '../../api/sales'
import styles from '../../components/sales/sales.module.css'

const krw = (value: number) => new Intl.NumberFormat('ko-KR').format(value)
const empty = (value: string | null | undefined) => value || '-'

/**
 * 주문서 상세의 표시 본문. 라우트의 편집/전환 제어와 분리해 병합 승인 전에도
 * 평소 상세 화면과 같은 헤더·상세 필드·라인 표를 사용한다.
 */
type PartnerOrderDetailReadOnlyProps = {
  order: PartnerOrderDetail
  statusBadge?: ReactNode
  selectedLineIds?: ReadonlySet<string>
  onToggleLine?: (lineId: string) => void
  onToggleAllLines?: (selected: boolean) => void
  onInventoryLookup?: () => void
  onLineLookup?: () => void
  onClearSelection?: () => void
  canViewProductLookups?: boolean
}

export function PartnerOrderDetailReadOnly({
  order,
  statusBadge,
  selectedLineIds,
  onToggleLine,
  onToggleAllLines,
  onInventoryLookup,
  onLineLookup,
  onClearSelection,
  canViewProductLookups = false,
}: PartnerOrderDetailReadOnlyProps) {
  const interactive = selectedLineIds !== undefined && onToggleLine && onToggleAllLines
  const selectedCount = selectedLineIds?.size ?? 0
  return (
    <div data-testid="partner-order-detail-read-only">
      <Card padding={4} shadow="sm">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <h4 style={{ margin: 0 }}>거래처 · {order.partnerName ?? order.partnerCode}</h4>
            <OrderStatusBadge status={order.status} />
            {statusBadge}
          </div>
          <strong style={{ fontVariantNumeric: 'tabular-nums' }}>합계 {krw(order.totalAmount)}원</strong>
        </div>
        <div className="detail-grid">
          {[
            ['거래처 코드', order.partnerCode],
            ['연결 전표', order.linkedSlipNo],
            ['배송지', order.deliveryAddress],
            ['현장', order.siteAddress],
            ['연락처', order.contactPhone],
            ['납기', order.dueDate],
            ['요청사항', order.memo],
          ].map(([label, value]) => (
            <div key={label}>
              <span className="detail-label">{label}</span>
              <span className="detail-value">{empty(value)}</span>
            </div>
          ))}
        </div>
      </Card>

      <Card padding={4} shadow="sm" style={{ marginTop: 24 }}>
        <div className="detail-mobile-hide" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <h4 style={{ margin: 0 }}>라인 ({order.lines.length}건)</h4>
          {interactive ? (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <button type="button" disabled={selectedCount === 0} onClick={onInventoryLookup} data-testid="partner-order-inventory-lookup-btn">
                선택 품목 재고조회{selectedCount > 0 ? ` (${selectedCount})` : ''}
              </button>
              {canViewProductLookups ? <button type="button" onClick={onLineLookup} data-testid="partner-order-line-lookup-btn">참조 조회</button> : null}
              {selectedCount > 0 ? <button type="button" onClick={onClearSelection}>선택 해제</button> : null}
            </div>
          ) : null}
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table className={styles['estTable']}>
            <thead>
              <tr>
                <th style={{ width: 28, textAlign: 'center' }}>
                  {interactive ? (
                    <input
                      type="checkbox"
                      aria-label="전체 선택"
                      disabled={order.lines.length === 0}
                      checked={order.lines.length > 0 && order.lines.every((line) => selectedLineIds?.has(line.lineId))}
                      onChange={(event) => onToggleAllLines?.(event.target.checked)}
                    />
                  ) : '선택'}
                </th>
                <th>품목명</th>
                <th>모델명</th>
                <th>수량</th>
                <th>납품가</th>
                <th>소계</th>
                <th>전환됨</th>
                <th>잔여</th>
                <th>묶음 처리</th>
                <th>구성품 펼침</th>
              </tr>
            </thead>
            <tbody>
              {order.lines.map((line, index) => {
                const converted = line.convertedQuantity ?? 0
                const remaining = line.quantity - converted
                return (
                  <tr key={`${line.lineId}-${index}`}>
                    <td style={{ textAlign: 'center', paddingLeft: 4 }}>
                    <input
                      type="checkbox"
                      aria-label={`${line.modelCode} 재고조회 선택`}
                      disabled={!interactive}
                      checked={interactive ? selectedLineIds?.has(line.lineId) ?? false : false}
                      onChange={() => onToggleLine?.(line.lineId)}
                    />
                    </td>
                    <td className={styles['tdLeft']}>{line.productName}</td>
                    <td>{line.modelCode}</td>
                    <td className={styles['numericCol']}>{line.quantity}</td>
                    <td className={styles['numericCol']}>{krw(line.deliveryPrice)}</td>
                    <td className={styles['numericCol']}>{krw(line.subtotal)}</td>
                    <td className={styles['numericCol']}>{converted > 0 ? <Badge variant="neutral">{converted}</Badge> : '-'}</td>
                    <td className={styles['numericCol']}>{converted > 0 ? remaining : '-'}</td>
                    <td>{line.bundleMode ? <span className={styles['badge']}>{line.bundleMode === 'EXPAND' ? '구성품 전개' : '세트 유지'}</span> : '-'}</td>
                    <td className={styles['expandedComponentText']}>
                      {(line.expandedComponents ?? []).length === 0 ? '-' : (line.expandedComponents ?? []).map((component) => (
                        <div key={component.modelCode}>{component.productName} ({component.modelCode}) × {component.quantity}</div>
                      ))}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}
