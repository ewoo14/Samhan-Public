/**
 * 종합견적서 전역 가격 설정 — `/sales/estimate-config`.
 *
 * <p>거래처 무관 전역 파라미터만 편집한다. 거래처별 DC는 기존
 * `SalesPartnerDcConfigPage` 가 담당한다.
 */
import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  type EstimateConfig,
  getEstimateConfig,
  updateEstimateConfig,
} from '../api/sales'
import { SalesSubNav } from '../components/sales/SalesSubNav'
import { usePermissions } from '../hooks/usePermissions'
import { usePageTitleStore } from '../stores/pageTitle'
import styles from '../components/sales/sales.module.css'

type FormState = Record<keyof EstimateConfig, string>

const RATE_FIELDS: Array<{ key: keyof EstimateConfig; label: string; help: string }> = [
  { key: 'commonHomeDiscountRate', label: '홈멀티 공통 DC율', help: '기본 0.45' },
  { key: 'commonCommercialDiscountRate', label: '상업멀티 공통 DC율', help: '기본 0.45' },
  { key: 'oldProductDiscountRate', label: '구형 제품 DC율', help: '기본 0.5' },
  { key: 'vatRate', label: '부가세율', help: '기본 0.1' },
  { key: 'cardFeeRate', label: '카드수수료율', help: '기본 0.03' },
  { key: 'advanceDiscountRate', label: '선금할인율', help: '기본 0' },
  { key: 'comboWarnRate', label: '조합비 경고 임계율', help: '0이면 off' },
]

function toForm(config: EstimateConfig): FormState {
  return {
    commonHomeDiscountRate: String(config.commonHomeDiscountRate ?? 0),
    commonCommercialDiscountRate: String(config.commonCommercialDiscountRate ?? 0),
    oldProductDiscountRate: String(config.oldProductDiscountRate ?? 0),
    vatRate: String(config.vatRate ?? 0),
    cardFeeRate: String(config.cardFeeRate ?? 0),
    advanceDiscountRate: String(config.advanceDiscountRate ?? 0),
    comboWarnRate: String(config.comboWarnRate ?? 0),
    footerNotice: config.footerNotice ?? '',
  }
}

function toRequest(form: FormState): EstimateConfig {
  const numberValue = (key: keyof EstimateConfig) => {
    const parsed = Number(form[key])
    return Number.isFinite(parsed) ? parsed : 0
  }
  return {
    commonHomeDiscountRate: numberValue('commonHomeDiscountRate'),
    commonCommercialDiscountRate: numberValue('commonCommercialDiscountRate'),
    oldProductDiscountRate: numberValue('oldProductDiscountRate'),
    vatRate: numberValue('vatRate'),
    cardFeeRate: numberValue('cardFeeRate'),
    advanceDiscountRate: numberValue('advanceDiscountRate'),
    comboWarnRate: numberValue('comboWarnRate'),
    footerNotice: form.footerNotice,
  }
}

export function EstimatePricingConfigPage() {
  const setPageTitle = usePageTitleStore((s) => s.setPageTitle)
  const { canAccess } = usePermissions()
  const canEdit = canAccess('sales.estimate-config', 'update')
  const queryClient = useQueryClient()
  const [form, setForm] = useState<FormState | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    setPageTitle({ title: '견적 가격 설정', meta: '영업' })
    return () => setPageTitle({ title: '' })
  }, [setPageTitle])

  const query = useQuery({
    queryKey: ['estimate-config'],
    queryFn: getEstimateConfig,
    retry: 1,
  })

  useEffect(() => {
    if (query.data) setForm(toForm(query.data))
  }, [query.data])

  const saveMutation = useMutation({
    mutationFn: updateEstimateConfig,
    onSuccess: (data) => {
      setForm(toForm(data))
      setMessage('저장되었습니다.')
      void queryClient.invalidateQueries({ queryKey: ['estimate-config'] })
    },
    onError: () => setMessage('저장에 실패했습니다. 입력값과 권한을 확인하세요.'),
  })

  const isDirty = useMemo(() => {
    if (!form || !query.data) return false
    return JSON.stringify(form) !== JSON.stringify(toForm(query.data))
  }, [form, query.data])

  const setField = (key: keyof EstimateConfig, value: string) => {
    if (!canEdit) return
    setMessage('')
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev))
  }

  const save = () => {
    if (!form || !canEdit) return
    saveMutation.mutate(toRequest(form))
  }

  return (
    <div className={styles['salesScope']}>
      <SalesSubNav />
      <div className={styles['wrap']}>
        <div className={styles['top']}>
          <div className={styles['title']}>
            견적 가격 설정
            <span className={styles['badge']}>전역</span>
            {isDirty ? (
              <span className={styles['badge']} style={{ background: '#fef3c7', color: '#92400e' }}>
                미저장
              </span>
            ) : null}
          </div>
          <div className={styles['topActions']}>
            <button
              type="button"
              className={styles['btnMini']}
              onClick={() => query.data && setForm(toForm(query.data))}
              disabled={!form || !isDirty}
            >
              되돌리기
            </button>
            <button
              type="button"
              className={styles['btnMini']}
              onClick={save}
              disabled={!canEdit || !form || !isDirty || saveMutation.isPending}
              style={{
                background: isDirty ? '#059669' : '#11182710',
                color: isDirty ? '#fff' : '#9ca3af',
              }}
            >
              {saveMutation.isPending ? '저장 중...' : canEdit ? '저장' : '조회 전용'}
            </button>
          </div>
        </div>

        {!canEdit ? (
          <p style={{ margin: '8px 0', fontSize: 12, color: '#b45309' }}>
            현재 권한은 조회 전용입니다. MASTER 또는 MANAGER 권한에서 변경할 수 있습니다.
          </p>
        ) : null}

        {query.isError ? (
          <div className={styles['emptyState']}>
            <h3>견적 가격 설정을 불러오지 못했습니다</h3>
            <p style={{ fontSize: 11 }}>endpoint: GET /api/v1/estimate-config</p>
          </div>
        ) : query.isLoading || !form ? (
          <div className={styles['emptyState']}>설정을 불러오는 중...</div>
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, minmax(260px, 1fr))',
              gap: 12,
              maxWidth: 920,
            }}
          >
            {RATE_FIELDS.map((field) => (
              <label key={field.key} style={{ display: 'grid', gap: 6, fontSize: 13 }}>
                <span style={{ fontWeight: 700 }}>{field.label}</span>
                <input
                  type="number"
                  min="0"
                  max="0.9999"
                  step="0.0001"
                  value={form[field.key]}
                  disabled={!canEdit}
                  onChange={(e) => setField(field.key, e.target.value)}
                  style={{
                    border: '1px solid #cbd5e1',
                    borderRadius: 6,
                    padding: '8px 10px',
                    fontSize: 13,
                  }}
                  aria-label={field.label}
                />
                <span style={{ fontSize: 11, color: '#6b7280' }}>{field.help}</span>
              </label>
            ))}
            <label style={{ display: 'grid', gap: 6, fontSize: 13, gridColumn: '1 / -1' }}>
              <span style={{ fontWeight: 700 }}>견적서 하단 안내문구</span>
              <textarea
                value={form.footerNotice}
                disabled={!canEdit}
                onChange={(e) => setField('footerNotice', e.target.value)}
                rows={5}
                style={{
                  border: '1px solid #cbd5e1',
                  borderRadius: 6,
                  padding: '8px 10px',
                  fontSize: 13,
                  resize: 'vertical',
                }}
                aria-label="견적서 하단 안내문구"
              />
            </label>
            {message ? (
              <p style={{ margin: 0, fontSize: 12, color: message.includes('실패') ? '#b91c1c' : '#047857' }}>
                {message}
              </p>
            ) : null}
          </div>
        )}
      </div>
    </div>
  )
}
