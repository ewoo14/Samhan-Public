/**
 * 전표 작성 화면 (출고/입고 공용) — slip-output-format 슬라이스 v2.
 *
 * 변경사항 (PR #18 → 본 슬라이스):
 * - "제품 ID UUID" 입력 필드 ❌ 제거 (Q6=A — `feedback_uuid_no_user_visibility.md`)
 * - "모델명" 입력 필드 + onBlur lookup (Q3=B — `GET /slips/lookup-product`)
 *   → 200 시 productName / sellingPrice 자동 fill (사용자가 단가 수정 가능)
 *   → 404 시 빨간 경고 메시지
 * - 화면 어디에도 UUID 노출 X (창고 코드 + 모델명 + 품목명 등 비즈니스 식별자만)
 *
 * 본 컴포넌트는 `mode` prop 으로 OUTBOUND / INBOUND 양쪽 화면에서 재사용된다.
 *
 * 사용 컴포넌트:
 * - `WarehouseSelector` (출발/도착) — id 가 옵션 라벨에 노출되지 않음 (코드+이름만)
 * - `DeliveryTagSelector` (OUTBOUND 만)
 * - `FormField` + native input (모델명 / 거래처명 / 메모)
 * - `PriceField` (라인 단가 — lookup 후 자동 fill)
 * - `Button` (라인 추가/삭제/저장)
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
  lookupProductByModelName,
  type SlipLineInput,
  type SlipType,
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

/**
 * 라인 입력 폼 상태.
 *
 * - `productId` 는 lookup 성공 시 내부적으로 채워지는 UUID — 화면 미노출
 * - `modelName` 이 사용자 입력 / 표시 식별자
 * - `lookupError` onBlur lookup 실패 메시지 (라인별)
 */
interface LineDraft {
  productId: string | null
  modelName: string
  productName: string
  quantity: string
  unitPrice: string
  lookupError: string | null
  lookupLoading: boolean
}

const emptyLine = (): LineDraft => ({
  productId: null,
  modelName: '',
  productName: '',
  quantity: '1',
  unitPrice: '0',
  lookupError: null,
  lookupLoading: false,
})

export interface SlipFormPageProps {
  /** OUTBOUND (판매/출고) 또는 INBOUND (구매/입고). */
  mode: SlipType
}

/**
 * 출고/입고 공용 작성 화면.
 *
 * mode 별 차이:
 * - OUTBOUND: 출발/도착 창고 + 배송태그, 저장 후 `/sales` 로 이동
 * - INBOUND: 도착 창고 (출발은 거래처 측), 배송태그 미노출, 저장 후 `/purchases` 로 이동
 */
export function SlipFormPage({ mode }: SlipFormPageProps) {
  const navigate = useNavigate()
  const isOutbound = mode === 'OUTBOUND'
  const listPath = isOutbound ? '/sales' : '/purchases'
  const titleLabel = isOutbound ? '새 출고전표' : '새 입고전표'

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
        slipType: mode,
        slipDate: today,
        sourceWarehouseId: sourceWh ?? undefined,
        destinationWarehouseId: destWh ?? undefined,
        partnerName: partnerName.trim() || undefined,
        deliveryTag: isOutbound ? tag ?? undefined : undefined,
        memo: memo.trim() || undefined,
        lines: lines
          .filter((l) => l.productId && Number(l.quantity) > 0)
          .map<SlipLineInput>((l) => ({
            productId: l.productId!,
            productName: l.productName.trim() || undefined,
            modelName: l.modelName.trim() || undefined,
            quantity: Number(l.quantity),
            unitPrice: l.unitPrice || '0',
          })),
      }
      return createSlip(payload)
    },
    onSuccess: () => navigate(listPath),
  })

  const addLine = () => setLines((ls) => [...ls, emptyLine()])
  const removeLine = (idx: number) =>
    setLines((ls) => (ls.length === 1 ? ls : ls.filter((_, i) => i !== idx)))
  const updateLine = (idx: number, patch: Partial<LineDraft>) =>
    setLines((ls) =>
      ls.map((l, i) => (i === idx ? { ...l, ...patch } : l)),
    )

  /**
   * 모델명 onBlur lookup 핸들러 — `GET /slips/lookup-product?modelName=...` 호출.
   *
   * - 빈 값이면 lookup 생략
   * - 200 시 productId/productName/sellingPrice fill
   * - 404 등 실패 시 lookupError 메시지 + productId null 유지
   */
  const handleModelNameBlur = async (idx: number, modelName: string) => {
    const trimmed = modelName.trim()
    if (!trimmed) {
      updateLine(idx, { productId: null, lookupError: null, productName: '' })
      return
    }
    updateLine(idx, { lookupLoading: true, lookupError: null })
    try {
      const product = await lookupProductByModelName(trimmed)
      updateLine(idx, {
        productId: product.productId,
        productName: product.productName,
        unitPrice: product.sellingPrice,
        lookupError: null,
        lookupLoading: false,
      })
    } catch (err) {
      const msg = axios.isAxiosError(err) && err.response?.status === 404
        ? '해당 모델명을 찾을 수 없습니다'
        : '모델명 조회에 실패했습니다'
      updateLine(idx, {
        productId: null,
        productName: '',
        lookupError: msg,
        lookupLoading: false,
      })
    }
  }

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
    (l) => l.productId && Number(l.quantity) > 0,
  )
  const requiredWh = isOutbound ? sourceWh : destWh
  const canSubmit = !!requiredWh && validLines.length > 0 && !mutation.isPending

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
        <h3 style={{ margin: 0 }}>{titleLabel}</h3>
        <Button variant="ghost" onClick={() => navigate(listPath)}>
          목록으로
        </Button>
      </div>

      <Card padding={5} shadow="sm">
        <div className="form-section">
          <div className="form-row">
            <WarehouseSelector
              label={isOutbound ? '출발 창고' : '입고 창고'}
              required={isOutbound}
              warehouses={warehousesQuery.data ?? []}
              value={sourceWh}
              onChange={(id) => setSourceWh(id)}
              hideVirtual
            />
            <WarehouseSelector
              label={isOutbound ? '도착 창고' : '출발 창고 (옵션)'}
              required={!isOutbound}
              warehouses={warehousesQuery.data ?? []}
              value={destWh}
              onChange={(id) => setDestWh(id)}
              hideVirtual
            />
          </div>

          {isOutbound ? (
            <DeliveryTagSelector
              options={OUTBOUND_TAG_OPTIONS}
              value={tag}
              onChange={(code) => setTag(code)}
              direction="OUTBOUND"
              slipDate={today}
            />
          ) : null}

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
          <div className="line-row line-row-v2" key={idx}>
            <FormField
              label={`라인 ${idx + 1} - 모델명`}
              required
              error={line.lookupError ?? undefined}
              render={({ id }) => (
                <input
                  id={id}
                  value={line.modelName}
                  onChange={(e) =>
                    updateLine(idx, { modelName: e.target.value })
                  }
                  onBlur={(e) => void handleModelNameBlur(idx, e.target.value)}
                  placeholder="예: AJ040RXH4BC1"
                  style={inputStyle}
                />
              )}
            />
            <FormField
              label="품목명"
              render={({ id }) => (
                <input
                  id={id}
                  value={line.productName}
                  readOnly
                  placeholder={line.lookupLoading ? '조회중...' : '모델명 조회 후 자동입력'}
                  style={{ ...inputStyle, background: 'var(--color-neutral-50)' }}
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
          <Button variant="ghost" onClick={() => navigate(listPath)}>
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
