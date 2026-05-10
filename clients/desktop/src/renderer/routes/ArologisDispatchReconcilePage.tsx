/**
 * arologis 운송사 실배차 비교 (`/arologis/dispatch-reconcile`).
 *
 * Phase 10 step-12 PR-F1 Designer mock — legacy GAS 11번 ("운송사-실배차 비교") 이식 1차 mock.
 *
 * <h2>용도</h2>
 * 운송사가 발행한 vendor 엑셀 (CJ대한통운 / 롯데 / 한진 등 다중 vendor) 과
 * 우리 시스템 내부 dispatch 기록을 비교하여 누락 / 시간 오차를 식별.
 *
 * <h2>UX 흐름</h2>
 * <pre>
 *   1) 다중 drag-drop 업로드 영역 (.xlsx 다수 — vendor 별 1 파일)
 *   2) from / to 날짜 선택 + "비교 실행" 버튼
 *   3) 결과 비교 테이블 — 상태 색상 cell:
 *      - TRUE         (초록) — 양쪽 모두 일치
 *      - FALSE_LEFT   (주황) — 우리 측엔 있으나 운송사엔 없음
 *      - FALSE_RIGHT  (빨강) — 운송사 측엔 있으나 우리에겐 없음
 *   4) 컬럼 필터 popup (상태 별 필터)
 *   5) "결과 CSV 다운로드" 버튼 (BOM 포함, Excel 한글 호환)
 * </pre>
 *
 * <h2>BE 의존</h2>
 * FE-2 슬라이스 (BE-2 endpoint) 의존. 본 단계는 mock data UI 만,
 * FE 단계에서 `POST /api/v1/arologis/dispatch/reconcile` (multipart) 실 API 연결 예정.
 *
 * <h2>설계 노트</h2>
 * <ul>
 *   <li>UUID 비공개 (feedback_uuid_no_user_visibility) — 사용자 노출 = slipNo / vendorName / partnerName.</li>
 *   <li>풀네임 ROLE (feedback_role_naming_full) — DISPATCH / MANAGER / MASTER 가드 (route 정의 측).</li>
 *   <li>한국어 라벨 100% — 영문 라벨 금지.</li>
 *   <li>PR-D CsvUploadDialog (commit fa42fdf) drag-drop UX 일관 + 다중 파일 확장.</li>
 *   <li>5MB / .xlsx 가드 (vendor 별).</li>
 *   <li>외부 의존 0 — 우리 desktop UI 내부에서 모든 reconcile 진행.</li>
 * </ul>
 *
 * <h2>data-testid</h2>
 * <ul>
 *   <li>{@code reconcile-upload-area}</li>
 *   <li>{@code reconcile-file-input}</li>
 *   <li>{@code reconcile-from / reconcile-to}</li>
 *   <li>{@code reconcile-run-btn}</li>
 *   <li>{@code reconcile-status-filter}</li>
 *   <li>{@code reconcile-csv-btn}</li>
 *   <li>{@code reconcile-result-table}</li>
 *   <li>{@code reconcile-row-{slipNo}}</li>
 * </ul>
 */
import {
  useCallback,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent as ReactDragEvent,
} from 'react'
import { Badge, Button } from '@samhan/design-system'
import { usePageTitle } from '../hooks/usePageTitle'

// ---------------------------------------------------------------------------
// 도메인 타입 (FE 단계에 실 BE 타입으로 교체)
// ---------------------------------------------------------------------------

/** 비교 결과 1행의 상태. legacy GAS 11번 의 TRUE/FALSE_LEFT/FALSE_RIGHT 그대로 이식. */
type ReconcileStatus = 'TRUE' | 'FALSE_LEFT' | 'FALSE_RIGHT'

const STATUS_LABEL: Record<ReconcileStatus, string> = {
  TRUE: '일치',
  FALSE_LEFT: '운송사 누락',
  FALSE_RIGHT: '우리 누락',
}

const STATUS_VARIANT: Record<ReconcileStatus, 'success' | 'warning' | 'danger'> = {
  TRUE: 'success',
  FALSE_LEFT: 'warning',
  FALSE_RIGHT: 'danger',
}

/** 비교 결과 1행 (mock). */
interface ReconcileRow {
  slipNo: string
  dispatchDate: string // YYYY-MM-DD
  vendorName: string
  ourTime: string | null // HH:mm
  vendorTime: string | null // HH:mm
  status: ReconcileStatus
  remark: string
}

/** 업로드 파일 1건. */
interface UploadedFile {
  file: File
  vendorGuess: string // 파일명 prefix 추정 vendor 명
}

// ---------------------------------------------------------------------------
// Mock 데이터
// ---------------------------------------------------------------------------

const MAX_FILE_SIZE_MB = 5
const ACCEPT_EXT = ['.xlsx']

/** TODO(FE-2): BE 연결 시점에 실 reconcile response rows 로 교체. */
const MOCK_ROWS: ReconcileRow[] = [
  {
    slipNo: 'S20260510-0001',
    dispatchDate: '2026-05-10',
    vendorName: 'CJ대한통운',
    ourTime: '09:30',
    vendorTime: '09:32',
    status: 'TRUE',
    remark: '시각 차이 2분 (허용 범위)',
  },
  {
    slipNo: 'S20260510-0002',
    dispatchDate: '2026-05-10',
    vendorName: '롯데택배',
    ourTime: '10:15',
    vendorTime: '10:15',
    status: 'TRUE',
    remark: '—',
  },
  {
    slipNo: 'S20260510-0003',
    dispatchDate: '2026-05-10',
    vendorName: 'CJ대한통운',
    ourTime: '11:00',
    vendorTime: null,
    status: 'FALSE_LEFT',
    remark: '운송사 엑셀에 누락 — 발송 확인 필요',
  },
  {
    slipNo: 'S20260510-0004',
    dispatchDate: '2026-05-10',
    vendorName: '한진택배',
    ourTime: null,
    vendorTime: '13:45',
    status: 'FALSE_RIGHT',
    remark: '우리 시스템에 dispatch 기록 누락 — 수기 추가 필요',
  },
  {
    slipNo: 'S20260510-0005',
    dispatchDate: '2026-05-10',
    vendorName: '롯데택배',
    ourTime: '14:20',
    vendorTime: '14:22',
    status: 'TRUE',
    remark: '—',
  },
  {
    slipNo: 'S20260510-0006',
    dispatchDate: '2026-05-10',
    vendorName: 'CJ대한통운',
    ourTime: '15:00',
    vendorTime: null,
    status: 'FALSE_LEFT',
    remark: '운송사 엑셀에 누락',
  },
]

// ---------------------------------------------------------------------------
// CSV 다운로드 헬퍼 (UTF-8 BOM, Excel 호환)
// ---------------------------------------------------------------------------

function csvCell(value: string | null | undefined): string {
  const s = value ?? ''
  if (/[",\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`
  return s
}

function downloadCsv(filename: string, rows: string[][]): void {
  const csv = rows.map((r) => r.map(csvCell).join(',')).join('\r\n')
  const bom = '﻿'
  const blob = new Blob([bom + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

// ---------------------------------------------------------------------------
// 파일 검증
// ---------------------------------------------------------------------------

function getExtension(name: string): string {
  const idx = name.lastIndexOf('.')
  return idx === -1 ? '' : name.substring(idx).toLowerCase()
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10)
}

// ---------------------------------------------------------------------------
// 컴포넌트
// ---------------------------------------------------------------------------

export function ArologisDispatchReconcilePage() {
  usePageTitle('운송사 실배차 비교')

  const today = todayIso()
  const [from, setFrom] = useState<string>(today)
  const [to, setTo] = useState<string>(today)
  const [files, setFiles] = useState<UploadedFile[]>([])
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<ReconcileStatus | ''>('')
  const [running, setRunning] = useState(false)
  const [rows, setRows] = useState<ReconcileRow[] | null>(null)
  const [dragOver, setDragOver] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // ----- 파일 추가 -----

  const addFiles = useCallback((incoming: File[]) => {
    setError(null)
    const accepted: UploadedFile[] = []
    for (const f of incoming) {
      const ext = getExtension(f.name)
      if (!ACCEPT_EXT.includes(ext)) {
        setError(
          `지원하지 않는 파일 형식입니다 (${f.name}). ${ACCEPT_EXT.join(', ')} 만 허용.`,
        )
        return
      }
      if (f.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
        setError(
          `${f.name} 파일이 ${MAX_FILE_SIZE_MB}MB 를 초과합니다 (${formatSize(f.size)}).`,
        )
        return
      }
      // vendor 명 추정 — 파일명에서 첫 단어 (예: "CJ대한통운_20260510.xlsx" → "CJ대한통운")
      const base = f.name.replace(/\.xlsx$/i, '')
      const vendorGuess = base.split(/[_\-\s]/)[0] || base
      accepted.push({ file: f, vendorGuess })
    }
    setFiles((prev) => [...prev, ...accepted])
  }, [])

  const handleFileInput = (e: ChangeEvent<HTMLInputElement>) => {
    const list = e.target.files
    if (!list) return
    addFiles(Array.from(list))
    // input reset — 같은 파일 재선택 허용
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const handleDrop = (e: ReactDragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragOver(false)
    const list = e.dataTransfer.files
    if (!list || list.length === 0) return
    addFiles(Array.from(list))
  }

  const handleDragOver = (e: ReactDragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragOver(true)
  }

  const handleDragLeave = () => {
    setDragOver(false)
  }

  const removeFile = (idx: number) => {
    setFiles((prev) => prev.filter((_, i) => i !== idx))
  }

  // ----- 비교 실행 (mock) -----

  const handleRun = () => {
    if (files.length === 0) {
      setError('vendor 엑셀 파일을 1개 이상 업로드하세요.')
      return
    }
    setError(null)
    setRunning(true)
    setRows(null)
    // TODO(FE-2): BE 연결 시점에 실 API 호출로 교체
    //   const formData = new FormData()
    //   files.forEach((f) => formData.append('files', f.file))
    //   formData.append('from', from); formData.append('to', to)
    //   const result = await api.post('/api/v1/arologis/dispatch/reconcile', formData)
    setTimeout(() => {
      setRows(MOCK_ROWS)
      setRunning(false)
    }, 800)
  }

  // ----- 결과 필터링 -----

  const filteredRows = useMemo<ReconcileRow[]>(() => {
    if (!rows) return []
    if (!statusFilter) return rows
    return rows.filter((r) => r.status === statusFilter)
  }, [rows, statusFilter])

  const counts = useMemo(() => {
    if (!rows) return { TRUE: 0, FALSE_LEFT: 0, FALSE_RIGHT: 0 }
    const c = { TRUE: 0, FALSE_LEFT: 0, FALSE_RIGHT: 0 } as Record<
      ReconcileStatus,
      number
    >
    for (const r of rows) c[r.status] += 1
    return c
  }, [rows])

  // ----- CSV 다운로드 -----

  const handleCsv = () => {
    if (!rows) return
    const out: string[][] = [
      ['상태', 'slipNo', '일자', 'vendor', '우리 시간', '운송사 시간', '비고'],
    ]
    for (const r of filteredRows) {
      out.push([
        STATUS_LABEL[r.status],
        r.slipNo,
        r.dispatchDate,
        r.vendorName,
        r.ourTime ?? '',
        r.vendorTime ?? '',
        r.remark,
      ])
    }
    downloadCsv(`reconcile_${from}_${to}.csv`, out)
  }

  return (
    <div
      style={{
        padding: 16,
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
      }}
    >
      <header>
        <h3 style={{ margin: '0 0 4px' }}>운송사 실배차 비교</h3>
        <div style={{ fontSize: 12, color: 'var(--color-neutral-600)' }}>
          vendor 별 엑셀 (.xlsx) 다중 업로드 → 우리 dispatch 기록과 비교 →
          누락 / 시각 오차 식별. 외부 vendor 콘솔 접속 불요.
        </div>
      </header>

      <p
        style={{
          margin: 0,
          padding: '8px 12px',
          fontSize: 12,
          color: 'var(--color-warning-700, #b45309)',
          background: 'var(--color-warning-50, #fffbeb)',
          border: '1px solid var(--color-warning-200, #fde68a)',
          borderRadius: 4,
        }}
      >
        본 화면은 PR-F1 1차 mock 입니다. BE 연결 시점에 실 API 호출로 교체
        예정입니다 (TODO FE-2).
      </p>

      {/* ───── 다중 drag-drop 업로드 영역 ───── */}
      <div
        data-testid="reconcile-upload-area"
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onClick={() => fileInputRef.current?.click()}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            fileInputRef.current?.click()
          }
        }}
        style={{
          padding: 24,
          border: `2px dashed ${
            dragOver
              ? 'var(--color-brand-500)'
              : 'var(--color-neutral-300, #D1D5DB)'
          }`,
          borderRadius: 8,
          background: dragOver
            ? 'var(--color-brand-50)'
            : 'var(--color-neutral-50, #F9FAFB)',
          textAlign: 'center',
          cursor: 'pointer',
          transition: 'border-color 0.15s, background 0.15s',
        }}
      >
        <div
          style={{
            fontSize: 14,
            fontWeight: 600,
            color: 'var(--color-neutral-700, #374151)',
            marginBottom: 4,
          }}
        >
          vendor 엑셀 파일을 끌어다 놓거나 클릭하여 선택
        </div>
        <div
          style={{ fontSize: 12, color: 'var(--color-neutral-500, #6B7280)' }}
        >
          .xlsx 만 허용 · 파일당 최대 {MAX_FILE_SIZE_MB}MB · 다중 업로드 지원
        </div>
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept=".xlsx"
          onChange={handleFileInput}
          data-testid="reconcile-file-input"
          style={{ display: 'none' }}
        />
      </div>

      {error ? (
        <div
          role="alert"
          style={{
            padding: '8px 12px',
            border: '1px solid var(--color-danger-300, #fca5a5)',
            background: 'var(--color-danger-50, #fef2f2)',
            color: 'var(--color-danger-700, #b91c1c)',
            borderRadius: 6,
            fontSize: 13,
          }}
        >
          {error}
        </div>
      ) : null}

      {/* ───── 업로드 파일 목록 ───── */}
      {files.length > 0 ? (
        <div
          style={{
            border: '1px solid var(--color-neutral-200, #E5E7EB)',
            borderRadius: 6,
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              padding: '8px 12px',
              fontSize: 12,
              fontWeight: 600,
              color: 'var(--color-neutral-700, #374151)',
              background: 'var(--color-neutral-50, #F9FAFB)',
              borderBottom: '1px solid var(--color-neutral-200, #E5E7EB)',
            }}
          >
            업로드 대기 ({files.length}건)
          </div>
          <ul style={{ margin: 0, padding: 0, listStyle: 'none' }}>
            {files.map((f, idx) => (
              <li
                key={`${f.file.name}-${idx}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '8px 12px',
                  borderTop:
                    idx === 0
                      ? 'none'
                      : '1px solid var(--color-neutral-100, #F3F4F6)',
                  fontSize: 13,
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 2,
                  }}
                >
                  <div
                    style={{
                      fontWeight: 500,
                      color: 'var(--color-neutral-800, #1F2937)',
                    }}
                  >
                    {f.file.name}
                  </div>
                  <div
                    style={{
                      fontSize: 11,
                      color: 'var(--color-neutral-500, #6B7280)',
                    }}
                  >
                    추정 vendor: <strong>{f.vendorGuess}</strong> ·{' '}
                    {formatSize(f.file.size)}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => removeFile(idx)}
                  disabled={running}
                  style={{
                    height: 28,
                    padding: '0 10px',
                    border: '1px solid var(--color-neutral-300, #D1D5DB)',
                    borderRadius: 4,
                    background: '#fff',
                    cursor: running ? 'not-allowed' : 'pointer',
                    fontSize: 12,
                    color: 'var(--color-neutral-700, #374151)',
                  }}
                >
                  제거
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {/* ───── 날짜 + 비교 실행 ───── */}
      <div
        style={{
          display: 'flex',
          gap: 12,
          alignItems: 'flex-end',
          flexWrap: 'wrap',
        }}
      >
        <label
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
            fontSize: 13,
          }}
        >
          시작일
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            data-testid="reconcile-from"
            style={inputStyle}
          />
        </label>
        <label
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
            fontSize: 13,
          }}
        >
          종료일
          <input
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            data-testid="reconcile-to"
            style={inputStyle}
          />
        </label>
        <Button
          variant="primary"
          onClick={handleRun}
          disabled={running || files.length === 0}
          data-testid="reconcile-run-btn"
        >
          {running ? '비교 중…' : '비교 실행'}
        </Button>
      </div>

      {/* ───── 결과 요약 + 필터 + CSV ───── */}
      {rows ? (
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <SummaryChip
              label={STATUS_LABEL.TRUE}
              value={counts.TRUE}
              tone="success"
            />
            <SummaryChip
              label={STATUS_LABEL.FALSE_LEFT}
              value={counts.FALSE_LEFT}
              tone="warning"
            />
            <SummaryChip
              label={STATUS_LABEL.FALSE_RIGHT}
              value={counts.FALSE_RIGHT}
              tone="danger"
            />
          </div>
          <div
            style={{
              display: 'flex',
              gap: 8,
              alignItems: 'center',
              flexWrap: 'wrap',
            }}
          >
            <label
              htmlFor="reconcile-status-filter"
              style={{
                fontSize: 13,
                color: 'var(--color-neutral-700, #374151)',
              }}
            >
              상태 필터
            </label>
            <select
              id="reconcile-status-filter"
              value={statusFilter}
              onChange={(e) =>
                setStatusFilter(e.target.value as ReconcileStatus | '')
              }
              data-testid="reconcile-status-filter"
              style={inputStyle}
            >
              <option value="">전체</option>
              <option value="TRUE">{STATUS_LABEL.TRUE}</option>
              <option value="FALSE_LEFT">{STATUS_LABEL.FALSE_LEFT}</option>
              <option value="FALSE_RIGHT">{STATUS_LABEL.FALSE_RIGHT}</option>
            </select>
            <Button
              variant="secondary"
              onClick={handleCsv}
              data-testid="reconcile-csv-btn"
            >
              결과 CSV 다운로드
            </Button>
          </div>
        </div>
      ) : null}

      {/* ───── 결과 비교 테이블 ───── */}
      {rows ? (
        <div
          data-testid="reconcile-result-table"
          style={{
            border: '1px solid var(--color-neutral-200, #E5E7EB)',
            borderRadius: 6,
            background: 'var(--color-neutral-0, #fff)',
            overflow: 'auto',
          }}
        >
          <table
            style={{
              width: '100%',
              borderCollapse: 'collapse',
              fontSize: 13,
            }}
          >
            <thead>
              <tr
                style={{
                  background: 'var(--color-neutral-50, #F9FAFB)',
                  color: 'var(--color-neutral-700, #374151)',
                }}
              >
                <th style={{ ...thStyle, width: 120 }}>상태</th>
                <th style={thStyle}>slipNo</th>
                <th style={{ ...thStyle, width: 110 }}>일자</th>
                <th style={thStyle}>vendor</th>
                <th style={{ ...thStyle, width: 90 }}>우리 시간</th>
                <th style={{ ...thStyle, width: 90 }}>운송사 시간</th>
                <th style={thStyle}>비고</th>
              </tr>
            </thead>
            <tbody>
              {filteredRows.length === 0 ? (
                <tr>
                  <td
                    colSpan={7}
                    style={{
                      padding: 24,
                      textAlign: 'center',
                      color: 'var(--color-neutral-500, #6B7280)',
                    }}
                  >
                    {statusFilter
                      ? '해당 상태에 결과가 없습니다.'
                      : '비교 결과가 없습니다.'}
                  </td>
                </tr>
              ) : (
                filteredRows.map((r) => (
                  <tr
                    key={r.slipNo}
                    data-testid={`reconcile-row-${r.slipNo}`}
                    style={{
                      borderTop: '1px solid var(--color-neutral-100, #F3F4F6)',
                    }}
                  >
                    <td
                      style={{
                        ...tdStyle,
                        background: STATUS_CELL_BG[r.status],
                        fontWeight: 600,
                      }}
                    >
                      <Badge variant={STATUS_VARIANT[r.status]}>
                        {STATUS_LABEL[r.status]}
                      </Badge>
                    </td>
                    <td style={tdStyle}>{r.slipNo}</td>
                    <td style={tdStyle}>{r.dispatchDate}</td>
                    <td style={tdStyle}>{r.vendorName}</td>
                    <td style={tdStyle}>{r.ourTime ?? '—'}</td>
                    <td style={tdStyle}>{r.vendorTime ?? '—'}</td>
                    <td
                      style={{
                        ...tdStyle,
                        color: 'var(--color-neutral-600, #4B5563)',
                      }}
                    >
                      {r.remark}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Summary chip (상태별 카운트)
// ---------------------------------------------------------------------------

interface SummaryChipProps {
  label: string
  value: number
  tone: 'success' | 'warning' | 'danger'
}

const SUMMARY_BG: Record<SummaryChipProps['tone'], string> = {
  success: 'var(--color-success-50, #ecfdf5)',
  warning: 'var(--color-warning-50, #fffbeb)',
  danger: 'var(--color-danger-50, #fef2f2)',
}

const SUMMARY_FG: Record<SummaryChipProps['tone'], string> = {
  success: 'var(--color-success-700, #047857)',
  warning: 'var(--color-warning-700, #b45309)',
  danger: 'var(--color-danger-700, #b91c1c)',
}

function SummaryChip({ label, value, tone }: SummaryChipProps) {
  return (
    <div
      style={{
        padding: '6px 12px',
        borderRadius: 999,
        background: SUMMARY_BG[tone],
        color: SUMMARY_FG[tone],
        fontWeight: 600,
        fontSize: 13,
      }}
    >
      {label} {value}
    </div>
  )
}

// ---------------------------------------------------------------------------
// 결과 행 status cell 배경 색상 (TRUE=초록 / FALSE_LEFT=주황 / FALSE_RIGHT=빨강)
// ---------------------------------------------------------------------------

const STATUS_CELL_BG: Record<ReconcileStatus, string> = {
  TRUE: 'var(--color-success-50, #ecfdf5)',
  FALSE_LEFT: 'var(--color-warning-50, #fffbeb)',
  FALSE_RIGHT: 'var(--color-danger-50, #fef2f2)',
}

// ---------------------------------------------------------------------------
// 공통 스타일
// ---------------------------------------------------------------------------

const inputStyle: React.CSSProperties = {
  height: 32,
  padding: '0 10px',
  border: '1px solid #D1D5DB',
  borderRadius: 6,
  fontSize: 13,
}

const thStyle: React.CSSProperties = {
  padding: '10px 12px',
  textAlign: 'left',
  fontWeight: 600,
  borderBottom: '1px solid var(--color-neutral-200, #E5E7EB)',
}

const tdStyle: React.CSSProperties = {
  padding: '10px 12px',
}
