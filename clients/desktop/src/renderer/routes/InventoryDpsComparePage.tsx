/**
 * DPS 입고 비교 페이지 (`/warehouse/dps-compare`) — PR-E1 FE-1.
 *
 * <p>Samhan Public 자동화 — legacy GAS 1번 (DPS 입고기록 비교) + 16번 (품목별
 * DPS 입고내역 비교) 의 native 이식. BE-2 (commit 4b14084) endpoint 호출.
 *
 * <h2>UX</h2>
 * <ol>
 *   <li>날짜 범위 from/to 입력 (출고전표 자동 조회 기간)</li>
 *   <li>매칭 단위 토글 (SLIP / ITEM)</li>
 *   <li>DPS 엑셀 .xlsx 업로드 — 사용자 명시 "자동 조회 X"</li>
 *   <li>"양식 다운로드" link — 헤더만 있는 .xlsx 받기</li>
 *   <li>"비교 실행" → 결과 통계 카드 + mismatch 표</li>
 *   <li>mismatch 표 reason 별 색상 (QUANTITY=주황, PARTNER=빨강, NOT_FOUND=회색)</li>
 *   <li>"결과 CSV 다운로드" — mismatch 보고서 BOM 포함 UTF-8 CSV</li>
 * </ol>
 *
 * <h2>UUID 비공개</h2>
 * <p>화면 노출 식별자 = slipNo / productCode / partnerCode 만. UUID 미사용.
 *
 * <h2>data-testid</h2>
 * <ul>
 *   <li>{@code dps-compare-from} / {@code dps-compare-to} — 날짜 input</li>
 *   <li>{@code dps-compare-groupby-slip} / {@code dps-compare-groupby-item}</li>
 *   <li>{@code dps-compare-file-input} — 숨김 input + 트리거 버튼</li>
 *   <li>{@code dps-compare-template-link} — 양식 다운로드</li>
 *   <li>{@code dps-compare-run-button} — 비교 실행</li>
 *   <li>{@code dps-compare-result-table} — mismatch 표</li>
 *   <li>{@code dps-compare-row-{slipNo|index}} — 각 mismatch 행</li>
 * </ul>
 */
import {
  useCallback,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type CSSProperties,
} from 'react'
import { useMutation } from '@tanstack/react-query'
import { Button } from '@samhan/design-system'
import {
  compareDps,
  downloadDpsTemplate,
  DPS_MISMATCH_COLOR,
  DPS_MISMATCH_LABEL,
  type DpsCompareGroupBy,
  type DpsCompareResponse,
  type DpsRowMismatch,
} from '../api/dpsCompareApi'
import { usePageTitle } from '../hooks/usePageTitle'

/** 오늘 날짜 (YYYY-MM-DD) — date input 기본값. */
function todayIso(): string {
  const d = new Date()
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

/**
 * mismatch[] → CSV 문자열 (BOM 포함, Excel 한글 호환).
 *
 * <p>컬럼: 카테고리 / 전표번호 / 품번 / 거래처코드 / 출고수량 / DPS수량 / 사유.
 */
function mismatchesToCsv(mismatches: DpsRowMismatch[]): string {
  const header = [
    '카테고리',
    '전표번호',
    '품번',
    '거래처코드',
    '출고수량',
    'DPS수량',
    '사유',
  ]
  const escape = (v: string): string => {
    if (v.includes('"') || v.includes(',') || v.includes('\n')) {
      return `"${v.replace(/"/g, '""')}"`
    }
    return v
  }
  const lines = [header.map(escape).join(',')]
  for (const m of mismatches) {
    const cells = [
      DPS_MISMATCH_LABEL[m.rowType],
      m.slipNo ?? '',
      m.productCode ?? '',
      m.partnerCode ?? '',
      String(m.expectedQty),
      String(m.actualQty),
      m.reason,
    ]
    lines.push(cells.map(escape).join(','))
  }
  return '﻿' + lines.join('\n')
}

/** Blob 을 사용자 다운로드로 트리거 (filename 지정). */
function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** 한국어 fallback error 메시지. */
function errorMessage(err: unknown): string {
  if (err instanceof Error) return err.message
  return '비교 실행 중 오류가 발생했습니다. 다시 시도해 주세요.'
}

export function InventoryDpsComparePage() {
  usePageTitle('DPS 입고 비교')

  // ── 폼 상태 ────────────────────────────────────────────────
  const today = useMemo(todayIso, [])
  const [from, setFrom] = useState(today)
  const [to, setTo] = useState(today)
  const [groupBy, setGroupBy] = useState<DpsCompareGroupBy>('SLIP')
  const [file, setFile] = useState<File | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  // ── BE 호출 mutation ───────────────────────────────────────
  const compareMutation = useMutation({
    mutationFn: () => {
      if (!file) throw new Error('DPS 엑셀 파일을 먼저 선택해 주세요.')
      return compareDps(file, from, to, groupBy)
    },
  })
  const result: DpsCompareResponse | undefined = compareMutation.data

  // ── 파일 선택 ─────────────────────────────────────────────
  const handleFileChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0]
    if (!selected) return
    if (!selected.name.toLowerCase().endsWith('.xlsx')) {
      setValidationError(
        `.xlsx 파일만 업로드 가능합니다 (선택한 파일: ${selected.name})`,
      )
      setFile(null)
    } else {
      setValidationError(null)
      setFile(selected)
    }
    // 동일 파일 재선택 허용
    e.target.value = ''
  }, [])

  // ── 양식 다운로드 ─────────────────────────────────────────
  const handleTemplateDownload = useCallback(async (e: React.MouseEvent) => {
    e.preventDefault()
    try {
      const blob = await downloadDpsTemplate()
      triggerDownload(blob, 'dps-compare-template.xlsx')
    } catch (err) {
      setValidationError(`양식 다운로드 실패: ${errorMessage(err)}`)
    }
  }, [])

  // ── 비교 실행 ─────────────────────────────────────────────
  const handleRun = useCallback(() => {
    if (!file) {
      setValidationError('DPS 엑셀 파일을 먼저 선택해 주세요.')
      return
    }
    if (!from || !to) {
      setValidationError('조회 기간을 입력해 주세요.')
      return
    }
    if (from > to) {
      setValidationError('시작일이 종료일보다 늦을 수 없습니다.')
      return
    }
    setValidationError(null)
    compareMutation.mutate()
  }, [compareMutation, file, from, to])

  // ── 결과 CSV 다운로드 ─────────────────────────────────────
  const handleCsvDownload = useCallback(() => {
    if (!result || result.mismatches.length === 0) return
    const csv = mismatchesToCsv(result.mismatches)
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const stamp = new Date()
      .toISOString()
      .replace(/[:.]/g, '-')
      .substring(0, 19)
    triggerDownload(blob, `dps-compare-${stamp}.csv`)
  }, [result])

  // ── 비교 실행 활성 조건 ────────────────────────────────────
  const canRun
    = !!file && !!from && !!to && !compareMutation.isPending

  return (
    <>
      <div style={headerRowStyle}>
        <h3 style={{ margin: 0 }}>DPS 입고 비교</h3>
        <span style={subtitleStyle}>
          출고전표 자동 조회 + DPS 엑셀 업로드 → SLIP/ITEM 단위 매칭
        </span>
      </div>

      {/* ── 폼 영역 ─────────────────────────────────────────── */}
      <section style={formCardStyle}>
        <div style={formRowStyle}>
          <label style={fieldLabelStyle}>
            <span>조회 기간 시작</span>
            <input
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              data-testid="dps-compare-from"
              style={inputStyle}
            />
          </label>
          <label style={fieldLabelStyle}>
            <span>조회 기간 종료</span>
            <input
              type="date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              data-testid="dps-compare-to"
              style={inputStyle}
            />
          </label>
          <div style={fieldLabelStyle}>
            <span>매칭 단위</span>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                type="button"
                data-testid="dps-compare-groupby-slip"
                onClick={() => setGroupBy('SLIP')}
                style={toggleButtonStyle(groupBy === 'SLIP')}
              >
                전표 단위 (SLIP)
              </button>
              <button
                type="button"
                data-testid="dps-compare-groupby-item"
                onClick={() => setGroupBy('ITEM')}
                style={toggleButtonStyle(groupBy === 'ITEM')}
              >
                품목 단위 (ITEM)
              </button>
            </div>
          </div>
        </div>

        <div style={formRowStyle}>
          <div style={fieldLabelStyle}>
            <span>DPS 엑셀 (.xlsx)</span>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx"
                onChange={handleFileChange}
                data-testid="dps-compare-file-input"
                style={{ display: 'none' }}
              />
              <Button
                variant="secondary"
                onClick={() => fileInputRef.current?.click()}
              >
                DPS 엑셀 업로드
              </Button>
              <span style={{ fontSize: 13, color: '#374151' }}>
                {file ? file.name : '선택된 파일 없음'}
              </span>
              {file ? (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setFile(null)
                    setValidationError(null)
                  }}
                >
                  제거
                </Button>
              ) : null}
            </div>
          </div>
          <div style={fieldLabelStyle}>
            <span>양식</span>
            <a
              href="#"
              onClick={handleTemplateDownload}
              data-testid="dps-compare-template-link"
              style={linkStyle}
            >
              DPS 엑셀 양식 다운로드
            </a>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <Button
            variant="primary"
            onClick={handleRun}
            disabled={!canRun}
            loading={compareMutation.isPending}
            data-testid="dps-compare-run-button"
          >
            비교 실행
          </Button>
          {validationError ? (
            <span role="alert" style={errorBannerStyle}>
              ⚠ {validationError}
            </span>
          ) : null}
          {compareMutation.isError ? (
            <span role="alert" style={errorBannerStyle}>
              ⚠ {errorMessage(compareMutation.error)}
            </span>
          ) : null}
        </div>
      </section>

      {/* ── 결과 통계 카드 + mismatch 표 ─────────────────────── */}
      {result ? (
        <section style={resultSectionStyle}>
          <div style={statsRowStyle}>
            <StatCard label="조회 기간" value={`${result.from} ~ ${result.to}`} />
            <StatCard label="매칭 단위" value={result.groupBy} />
            <StatCard label="출고전표 라인" value={result.outboundCount.toLocaleString()} />
            <StatCard label="DPS 행" value={result.dpsRowCount.toLocaleString()} />
            <StatCard
              label="정상 일치"
              value={result.matchedCount.toLocaleString()}
              tone="success"
            />
            <StatCard
              label="불일치"
              value={result.mismatchCount.toLocaleString()}
              tone={result.mismatchCount > 0 ? 'danger' : 'neutral'}
            />
          </div>

          <div style={resultActionRowStyle}>
            <h4 style={{ margin: 0 }}>
              불일치 상세 ({result.mismatches.length.toLocaleString()}건)
            </h4>
            <Button
              variant="secondary"
              onClick={handleCsvDownload}
              disabled={result.mismatches.length === 0}
            >
              결과 CSV 다운로드
            </Button>
          </div>

          {result.mismatches.length === 0 ? (
            <div style={successBannerStyle} role="status">
              ✓ 모든 라인이 정상 일치합니다
            </div>
          ) : (
            <div style={tableWrapStyle} data-testid="dps-compare-result-table">
              <table style={tableStyle}>
                <thead>
                  <tr>
                    <th style={thStyle}>카테고리</th>
                    <th style={thStyle}>전표번호</th>
                    <th style={thStyle}>품번</th>
                    <th style={thStyle}>거래처코드</th>
                    <th style={{ ...thStyle, textAlign: 'right' }}>출고수량</th>
                    <th style={{ ...thStyle, textAlign: 'right' }}>DPS수량</th>
                    <th style={thStyle}>사유</th>
                  </tr>
                </thead>
                <tbody>
                  {result.mismatches.map((m, idx) => {
                    const colors = DPS_MISMATCH_COLOR[m.rowType]
                    const testId = `dps-compare-row-${m.slipNo ?? `idx-${idx}`}`
                    return (
                      <tr
                        key={`${m.rowType}-${m.slipNo ?? ''}-${m.productCode ?? ''}-${idx}`}
                        data-testid={testId}
                        style={{ background: colors.background }}
                      >
                        <td style={tdStyle}>
                          <span
                            style={{
                              display: 'inline-block',
                              padding: '2px 8px',
                              borderRadius: 4,
                              border: `1px solid ${colors.border}`,
                              color: colors.text,
                              fontSize: 12,
                              fontWeight: 600,
                              background: '#fff',
                            }}
                          >
                            {DPS_MISMATCH_LABEL[m.rowType]}
                          </span>
                        </td>
                        <td style={tdStyle}>{m.slipNo ?? '—'}</td>
                        <td style={tdStyle}>{m.productCode ?? '—'}</td>
                        <td style={tdStyle}>{m.partnerCode ?? '—'}</td>
                        <td style={{ ...tdStyle, textAlign: 'right' }}>
                          {m.expectedQty.toLocaleString()}
                        </td>
                        <td style={{ ...tdStyle, textAlign: 'right' }}>
                          {m.actualQty.toLocaleString()}
                        </td>
                        <td style={{ ...tdStyle, color: colors.text }}>
                          {m.reason}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : null}
    </>
  )
}

// ---------------------------------------------------------------------------
// 보조 컴포넌트 / 스타일
// ---------------------------------------------------------------------------

interface StatCardProps {
  label: string
  value: string
  tone?: 'neutral' | 'success' | 'danger'
}

function StatCard({ label, value, tone = 'neutral' }: StatCardProps) {
  const valueColor
    = tone === 'success' ? '#047857' : tone === 'danger' ? '#B91C1C' : '#111827'
  return (
    <div style={statCardStyle}>
      <div style={{ fontSize: 11, color: '#6B7280', marginBottom: 4 }}>
        {label}
      </div>
      <div style={{ fontSize: 18, fontWeight: 700, color: valueColor }}>
        {value}
      </div>
    </div>
  )
}

const headerRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 12,
  marginBottom: 16,
  flexWrap: 'wrap',
}

const subtitleStyle: CSSProperties = {
  fontSize: 12,
  color: '#6B7280',
}

const formCardStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 16,
  padding: 16,
  border: '1px solid #E5E7EB',
  borderRadius: 8,
  background: '#FFFFFF',
  marginBottom: 16,
}

const formRowStyle: CSSProperties = {
  display: 'flex',
  gap: 16,
  flexWrap: 'wrap',
  alignItems: 'flex-end',
}

const fieldLabelStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
  fontSize: 12,
  color: '#374151',
  minWidth: 200,
}

const inputStyle: CSSProperties = {
  height: 32,
  padding: '0 10px',
  border: '1px solid #D1D5DB',
  borderRadius: 6,
  fontSize: 13,
}

function toggleButtonStyle(active: boolean): CSSProperties {
  return {
    height: 32,
    padding: '0 12px',
    border: `1px solid ${active ? '#2563EB' : '#D1D5DB'}`,
    borderRadius: 6,
    background: active ? '#EFF6FF' : '#FFFFFF',
    color: active ? '#1D4ED8' : '#374151',
    fontSize: 13,
    fontWeight: active ? 600 : 400,
    cursor: 'pointer',
  }
}

const linkStyle: CSSProperties = {
  fontSize: 13,
  color: '#2563EB',
  textDecoration: 'underline',
  cursor: 'pointer',
  height: 32,
  display: 'inline-flex',
  alignItems: 'center',
}

const errorBannerStyle: CSSProperties = {
  fontSize: 12,
  color: '#B91C1C',
  background: '#FEF2F2',
  border: '1px solid #FECACA',
  borderRadius: 4,
  padding: '4px 8px',
}

const successBannerStyle: CSSProperties = {
  fontSize: 13,
  color: '#047857',
  background: '#ECFDF5',
  border: '1px solid #A7F3D0',
  borderRadius: 6,
  padding: 12,
}

const resultSectionStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 12,
}

const statsRowStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
  gap: 12,
}

const statCardStyle: CSSProperties = {
  padding: 12,
  border: '1px solid #E5E7EB',
  borderRadius: 6,
  background: '#FFFFFF',
}

const resultActionRowStyle: CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  marginTop: 8,
}

const tableWrapStyle: CSSProperties = {
  border: '1px solid #E5E7EB',
  borderRadius: 6,
  overflow: 'auto',
  background: '#FFFFFF',
}

const tableStyle: CSSProperties = {
  width: '100%',
  borderCollapse: 'collapse',
  fontSize: 13,
}

const thStyle: CSSProperties = {
  textAlign: 'left',
  padding: '8px 10px',
  borderBottom: '1px solid #E5E7EB',
  background: '#F9FAFB',
  fontSize: 12,
  fontWeight: 600,
  color: '#374151',
  whiteSpace: 'nowrap',
}

const tdStyle: CSSProperties = {
  padding: '8px 10px',
  borderBottom: '1px solid #F3F4F6',
}
