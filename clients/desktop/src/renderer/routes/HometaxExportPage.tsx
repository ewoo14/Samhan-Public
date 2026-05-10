/**
 * 홈택스 일괄 등록 양식 화면 — `/accounting/hometax-export` (PR-E2 FE-9).
 *
 * <p>매뉴얼 출처: legacy GAS B 카테고리 — 세금계산서 홈택스 일괄 업로드 양식.
 * BE: {@code accounting-service} commit c48e156 — AccountingReportController#hometaxExport.
 *
 * <p>UI 흐름:
 * <ol>
 *   <li>필터 — from / to (default = 이번 달 1일 ~ 오늘)</li>
 *   <li>발행 (ISSUED) 세금계산서 미리보기 — 건수 + 합계 (count/SUM 보기)</li>
 *   <li>"엑셀 다운로드" 버튼 → binary xlsx Blob → 한국어 파일명 다운로드</li>
 *   <li>안내 — "홈택스 표준 양식 .xlsx 파일이 다운로드됩니다. 100건 초과 시 sheet 가 분할됩니다."</li>
 * </ol>
 *
 * <p>UUID 비공개 가드 (feedback_uuid_no_user_visibility.md) — 사용자 노출은
 * count + 합계만. 개별 세금계산서 id / partnerId 는 화면 노출 X.
 *
 * <p>권한 — RoleGuard 가 ACCOUNTANT / MANAGER / MASTER 만 통과 (라우팅 단계).
 *
 * <p>data-testid:
 * <ul>
 *   <li>{@code hometax-export-from} — 시작일 input</li>
 *   <li>{@code hometax-export-to} — 종료일 input</li>
 *   <li>{@code hometax-export-count} — 미리보기 건수 영역</li>
 *   <li>{@code hometax-export-download-button} — 엑셀 다운로드 버튼</li>
 * </ul>
 */
import { useCallback, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Input } from '@samhan/design-system'
import {
  buildHometaxExportFilename,
  downloadHometaxExport,
} from '../api/hometaxExportApi'
import { listTaxInvoices } from '../api/taxInvoiceApi'
import { usePageTitle } from '../hooks/usePageTitle'

/** 이번 달 1일 (YYYY-MM-DD). */
function firstOfMonth(): string {
  const d = new Date()
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  return `${yyyy}-${mm}-01`
}

/** 오늘 (YYYY-MM-DD). */
function todayIso(): string {
  const d = new Date()
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

/** KRW BigDecimal string → 천단위 콤마 + ₩ prefix. */
function fmtKrw(raw: string): string {
  const n = Number.parseFloat(raw)
  if (!Number.isFinite(n)) return raw
  return '₩' + Math.trunc(n).toLocaleString('ko-KR')
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
  return '엑셀 다운로드 중 오류가 발생했습니다. 다시 시도해 주세요.'
}

export function HometaxExportPage() {
  usePageTitle('홈택스 일괄 양식')

  const defaultFrom = useMemo(firstOfMonth, [])
  const defaultTo = useMemo(todayIso, [])
  const [from, setFrom] = useState<string>(defaultFrom)
  const [to, setTo] = useState<string>(defaultTo)
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)

  // ── 미리보기 — 발행 (ISSUED) 세금계산서 page 조회 (size 200 = 화면 합계 계산용 cap) ─
  // BE 가 sum aggregation endpoint 를 별도 제공하지 않아 page 조회 결과로 합계 계산.
  // 200 cap 은 실제 월 발행 건수 (수십 건) 대비 충분. 200 초과 시 미리보기 건수만 정확,
  // 합계는 first-200 합계로 표시 (사용자 다운로드 자체는 BE 가 전체 sheet 분할).
  const previewQuery = useQuery({
    queryKey: ['accounting', 'hometax-export', 'preview', from, to],
    queryFn: () =>
      listTaxInvoices({
        status: 'ISSUED',
        from,
        to,
        page: 0,
        size: 200,
      }),
    enabled: Boolean(from && to),
  })

  const previewRows = previewQuery.data?.content ?? []
  const totalElements = previewQuery.data?.totalElements ?? 0
  const sumSupply = useMemo(
    () =>
      previewRows.reduce(
        (acc, r) => acc + (Number.parseFloat(r.supplyAmount) || 0),
        0,
      ),
    [previewRows],
  )
  const sumVat = useMemo(
    () =>
      previewRows.reduce(
        (acc, r) => acc + (Number.parseFloat(r.vatAmount) || 0),
        0,
      ),
    [previewRows],
  )
  const sumTotal = useMemo(
    () =>
      previewRows.reduce(
        (acc, r) => acc + (Number.parseFloat(r.totalAmount) || 0),
        0,
      ),
    [previewRows],
  )

  // 합계 표시 정확도 안내 — page 조회 cap 초과 시 (>200 건).
  const hasMoreThanCap = totalElements > previewRows.length

  // ── 엑셀 다운로드 ─────────────────────────────────────────
  const handleDownload = useCallback(async () => {
    setDownloadError(null)
    if (!from || !to) {
      setDownloadError('기간 (시작/종료) 을 모두 입력해 주세요.')
      return
    }
    if (from > to) {
      setDownloadError('시작일이 종료일보다 늦을 수 없습니다.')
      return
    }
    setDownloading(true)
    try {
      const blob = await downloadHometaxExport(from, to)
      triggerDownload(blob, buildHometaxExportFilename(from, to))
    } catch (err) {
      setDownloadError(errorMessage(err))
    } finally {
      setDownloading(false)
    }
  }, [from, to])

  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-end',
          gap: 16,
          marginBottom: 16,
          flexWrap: 'wrap',
        }}
      >
        <div>
          <h3 style={{ margin: 0 }}>계산서 일괄 등록 양식 (홈택스)</h3>
          <div
            style={{
              fontSize: 12,
              color: '#6B7280',
              marginTop: 4,
            }}
          >
            발행 완료 (ISSUED) 세금계산서를 홈택스 표준 컬럼 .xlsx 로 일괄
            export 합니다.
          </div>
        </div>
      </div>

      {/* 필터 영역 */}
      <Card style={{ marginBottom: 16 }}>
        <div
          style={{
            display: 'flex',
            gap: 12,
            flexWrap: 'wrap',
            alignItems: 'flex-end',
          }}
        >
          <Input
            label="기간 (시작)"
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            fullWidth={false}
            data-testid="hometax-export-from"
          />
          <Input
            label="기간 (종료)"
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            fullWidth={false}
            data-testid="hometax-export-to"
          />
          <Button
            variant="primary"
            onClick={handleDownload}
            disabled={downloading || !from || !to}
            data-testid="hometax-export-download-button"
          >
            {downloading ? '다운로드 중...' : '엑셀 다운로드'}
          </Button>
        </div>
      </Card>

      {/* 미리보기 — 건수 + 합계 (UUID 비공개 — count + SUM 만 노출) */}
      <Card style={{ marginBottom: 16 }}>
        <div data-testid="hometax-export-count">
          {previewQuery.isLoading ? (
            <div style={{ color: '#6B7280', fontSize: 13 }}>
              미리보기 조회 중...
            </div>
          ) : previewQuery.isError ? (
            <div className="error-banner" role="alert">
              미리보기 조회 실패. 백엔드 연결을 확인하세요.
            </div>
          ) : (
            <div
              style={{
                display: 'flex',
                gap: 24,
                flexWrap: 'wrap',
                alignItems: 'baseline',
              }}
            >
              <div>
                <div style={{ fontSize: 12, color: '#6B7280' }}>발행 건수</div>
                <div
                  style={{
                    fontSize: 22,
                    fontWeight: 600,
                    fontVariantNumeric: 'tabular-nums',
                  }}
                >
                  {totalElements.toLocaleString('ko-KR')} 건
                </div>
              </div>
              <div>
                <div style={{ fontSize: 12, color: '#6B7280' }}>공급가액 합계</div>
                <div
                  style={{
                    fontSize: 18,
                    fontVariantNumeric: 'tabular-nums',
                  }}
                >
                  {fmtKrw(String(sumSupply))}
                </div>
              </div>
              <div>
                <div style={{ fontSize: 12, color: '#6B7280' }}>세액 합계</div>
                <div
                  style={{
                    fontSize: 18,
                    fontVariantNumeric: 'tabular-nums',
                  }}
                >
                  {fmtKrw(String(sumVat))}
                </div>
              </div>
              <div>
                <div style={{ fontSize: 12, color: '#6B7280' }}>총합계</div>
                <div
                  style={{
                    fontSize: 18,
                    fontWeight: 600,
                    fontVariantNumeric: 'tabular-nums',
                  }}
                >
                  {fmtKrw(String(sumTotal))}
                </div>
              </div>
            </div>
          )}
          {hasMoreThanCap ? (
            <div
              style={{
                marginTop: 12,
                fontSize: 12,
                color: '#B45309',
              }}
            >
              ※ 미리보기 합계는 최근 200건 기준입니다. 전체{' '}
              {totalElements.toLocaleString('ko-KR')}건은 다운로드 .xlsx 에
              모두 포함됩니다.
            </div>
          ) : null}
        </div>
      </Card>

      {/* 안내 문구 */}
      <Card>
        <div style={{ fontSize: 13, color: '#374151', lineHeight: 1.7 }}>
          <strong>안내</strong>
          <ul style={{ margin: '8px 0 0 20px', padding: 0 }}>
            <li>홈택스 표준 양식 .xlsx 파일이 다운로드됩니다.</li>
            <li>100건 초과 시 sheet 가 분할됩니다.</li>
            <li>
              발행 (ISSUED) 상태의 세금계산서만 포함됩니다 (DRAFT / CANCELLED
              제외).
            </li>
            <li>
              파일명: <code>홈택스_일괄등록_시작일_종료일.xlsx</code>
            </li>
          </ul>
        </div>
      </Card>

      {downloadError ? (
        <div className="error-banner" role="alert" style={{ marginTop: 16 }}>
          엑셀 다운로드 실패: {downloadError}
        </div>
      ) : null}
    </>
  )
}
