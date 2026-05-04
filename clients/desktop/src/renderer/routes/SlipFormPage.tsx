/**
 * 출고전표 작성 화면 — 디자인 시스템 6 종 컴포넌트 통합 시연.
 *
 * 사용 컴포넌트:
 * - `WarehouseSelector` × 2 (출발/도착)
 * - `DeliveryTagSelector` (direction=OUTBOUND, hideVirtual)
 * - `FormField` + native input (거래처명/메모)
 * - `PriceField` (라인 단가)
 * - `Button` (라인 추가/삭제/저장)
 *
 * 본 슬라이스에서는 product 검색 미구현 — 사용자가 productId(UUID) 와
 * 표시명을 수동 입력한다. 후속 슬라이스에서 product autocomplete 추가 예정.
 */
import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  Button,
  Card,
  DeliveryTagSelector,
  FormField,
  PriceField,
  WarehouseSelector,
  type DeliveryTagOption,
} from '@samhan/design-system'
import axios from 'axios'
import { listWarehouses } from '../api/inventory'
import {
  createSlip,
  type SlipLineInput,
} from '../api/slip'

/**
 * 본 슬라이스용 OUTBOUND 배송태그 옵션 — BE `DeliveryTag` enum 의 OUTBOUND 8종.
 *
 * 후속 슬라이스에서 `GET /slips/delivery-tags` 같은 메타데이터 endpoint 가
 * 추가되면 그 응답으로 대체할 예정.
 */
const OUTBOUND_TAG_OPTIONS: DeliveryTagOption[] = [
  { code: 'DAY', displayName: '당일', direction: 'OUTBOUND', autoMemo: false },
  { code: 'STACK', displayName: '야적', direction: 'OUTBOUND', autoMemo: true },
  { code: 'REGION', displayName: '지방', direction: 'OUTBOUND', autoMemo: true },
  { code: 'LOGEN', displayName: '로젠택배', direction: 'OUTBOUND', autoMemo: false },
  { code: 'GYEONGDONG_PARCEL', displayName: '경동택배', direction: 'OUTBOUND', autoMemo: false },
  { code: 'GYEONGDONG_FREIGHT', displayName: '경동화물', direction: 'OUTBOUND', autoMemo: false },
  { code: 'RENTAL', displayName: '대여', direction: 'OUTBOUND', autoMemo: false },
  { code: 'RETURN_RENTAL', displayName: '반납', direction: 'OUTBOUND', autoMemo: false },
]

/** 라인 입력 폼 상태 — onChange 마다 부분 갱신. */
interface LineDraft {
  productId: string
  productName: string
  quantity: string
  unitPrice: string
}

const emptyLine = (): LineDraft => ({
  productId: '',
  productName: '',
  quantity: '1',
  unitPrice: '0',
})

export function SlipFormPage() {
  const navigate = useNavigate()
  const [sourceWh, setSourceWh] = useState<string | null>(null)
  const [destWh, setDestWh] = useState<string | null>(null)
  const [partnerName, setPartnerName] = useState('')
  const [memo, setMemo] = useState('')
  const [tag, setTag] = useState<DeliveryTagOption['code'] | null>(null)
  const [lines, setLines] = useState<LineDraft[]>([emptyLine()])

  const warehousesQuery = useQuery({
    queryKey: ['warehouses'],
    queryFn: listWarehouses,
  })

  const today = useMemo(() => new Date().toISOString().slice(0, 10), [])

  const mutation = useMutation({
    mutationFn: () => {
      const payload: Parameters<typeof createSlip>[0] = {
        slipType: 'OUTBOUND',
        slipDate: today,
        sourceWarehouseId: sourceWh ?? undefined,
        destinationWarehouseId: destWh ?? undefined,
        partnerName: partnerName.trim() || undefined,
        deliveryTag: tag ?? undefined,
        memo: memo.trim() || undefined,
        lines: lines
          .filter((l) => l.productId.trim() && Number(l.quantity) > 0)
          .map<SlipLineInput>((l) => ({
            productId: l.productId.trim(),
            productName: l.productName.trim() || undefined,
            quantity: Number(l.quantity),
            unitPrice: l.unitPrice || '0',
          })),
      }
      return createSlip(payload)
    },
    onSuccess: () => navigate('/slips'),
  })

  const addLine = () => setLines((ls) => [...ls, emptyLine()])
  const removeLine = (idx: number) =>
    setLines((ls) => (ls.length === 1 ? ls : ls.filter((_, i) => i !== idx)))
  const updateLine = (idx: number, patch: Partial<LineDraft>) =>
    setLines((ls) =>
      ls.map((l, i) => (i === idx ? { ...l, ...patch } : l)),
    )

  const errorMessage = (() => {
    if (!mutation.isError) return null
    const err = mutation.error
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string } | undefined
      return data?.message ?? '전표 생성에 실패했습니다.'
    }
    return '알 수 없는 오류'
  })()

  const validLines = lines.filter(
    (l) => l.productId.trim() && Number(l.quantity) > 0,
  )
  const canSubmit = sourceWh && validLines.length > 0 && !mutation.isPending

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
        <h3 style={{ margin: 0 }}>새 출고전표</h3>
        <Button variant="ghost" onClick={() => navigate('/slips')}>
          목록으로
        </Button>
      </div>

      <Card padding={5} shadow="sm">
        <div className="form-section">
          <div className="form-row">
            <WarehouseSelector
              label="출발 창고"
              required
              warehouses={warehousesQuery.data ?? []}
              value={sourceWh}
              onChange={(id) => setSourceWh(id)}
              hideVirtual
            />
            <WarehouseSelector
              label="도착 창고"
              warehouses={warehousesQuery.data ?? []}
              value={destWh}
              onChange={(id) => setDestWh(id)}
              hideVirtual
            />
          </div>

          <DeliveryTagSelector
            options={OUTBOUND_TAG_OPTIONS}
            value={tag}
            onChange={(code) => setTag(code)}
            direction="OUTBOUND"
            slipDate={today}
          />

          <div className="form-row">
            <FormField
              label="거래처명"
              render={({ id }) => (
                <input
                  id={id}
                  value={partnerName}
                  onChange={(e) => setPartnerName(e.target.value)}
                  maxLength={100}
                  style={inputStyle}
                />
              )}
            />
            <FormField
              label="메모"
              render={({ id }) => (
                <input
                  id={id}
                  value={memo}
                  onChange={(e) => setMemo(e.target.value)}
                  maxLength={1000}
                  style={inputStyle}
                />
              )}
            />
          </div>
        </div>

        <h4 style={{ marginTop: 0 }}>전표 라인</h4>
        {lines.map((line, idx) => (
          <div className="line-row" key={idx}>
            <FormField
              label={`라인 ${idx + 1} - 제품 ID (UUID)`}
              required
              render={({ id }) => (
                <input
                  id={id}
                  value={line.productId}
                  onChange={(e) =>
                    updateLine(idx, { productId: e.target.value })
                  }
                  placeholder="UUID"
                  style={inputStyle}
                />
              )}
            />
            <FormField
              label="수량"
              required
              render={({ id }) => (
                <input
                  id={id}
                  type="number"
                  min={1}
                  value={line.quantity}
                  onChange={(e) =>
                    updateLine(idx, { quantity: e.target.value })
                  }
                  style={inputStyle}
                />
              )}
            />
            <FormField
              label="단가"
              render={() => (
                <PriceField
                  value={line.unitPrice}
                  onChange={(next) => updateLine(idx, { unitPrice: next })}
                  currency="KRW"
                />
              )}
            />
            <Button
              variant="ghost"
              size="sm"
              onClick={() => removeLine(idx)}
              disabled={lines.length === 1}
            >
              삭제
            </Button>
          </div>
        ))}
        <Button variant="secondary" size="sm" onClick={addLine}>
          + 라인 추가
        </Button>

        {errorMessage ? (
          <div className="error-banner" role="alert" style={{ marginTop: 16 }}>
            {errorMessage}
          </div>
        ) : null}

        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            gap: 8,
            marginTop: 24,
          }}
        >
          <Button variant="ghost" onClick={() => navigate('/slips')}>
            취소
          </Button>
          <Button
            variant="primary"
            onClick={() => mutation.mutate()}
            loading={mutation.isPending}
            disabled={!canSubmit}
          >
            저장
          </Button>
        </div>
      </Card>
    </>
  )
}

const inputStyle = {
  padding: '8px 12px',
  borderRadius: 6,
  border: '1px solid var(--color-neutral-300)',
  fontSize: 14,
  width: '100%',
} as const
