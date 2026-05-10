/**
 * 관리자 — 알리고 주소록 자동 동기화 (`/admin/aligo-address-book`).
 *
 * Phase 10 step-12 PR-F1 Designer mock — legacy GAS 9번 (알리고 자동 업로드) 이식 1차 mock.
 *
 * <h2>용도</h2>
 * 거래처 마스터 → 알리고 (SMS/카카오톡 발송 vendor) 주소록 자동 sync.
 * 사용자가 알리고 콘솔을 직접 열지 않고 우리 desktop UI 에서 단일 클릭으로 동기화.
 *
 * <h2>UX 흐름</h2>
 * <pre>
 *   1) 그룹 선택 dropdown — 전체 / SF벤더 / 신용정보 / 일반
 *   2) 거래처 미리보기 표 (mock) — partnerCode / partnerName / phone / group / status / blocked
 *   3) "동기화 실행" 버튼 → BE 실 호출 (FE 단계에서 연결)
 *   4) 결과 4분 색상 chip — added(brand) / updated(success) / skipped(neutral) / failed(danger)
 * </pre>
 *
 * <h2>BE 의존</h2>
 * FE-1 슬라이스 (BE-1 endpoint) 의존. 본 단계는 mock data UI 만, FE 단계에서
 * `POST /api/v1/notify/aligo/address-book/sync?group=` 실 API 연결 예정.
 *
 * <h2>설계 노트</h2>
 * <ul>
 *   <li>UUID 비공개 (feedback_uuid_no_user_visibility) — 비즈니스 식별자만 노출.
 *       사용자 노출 = partnerCode / partnerName / phone / group.</li>
 *   <li>풀네임 ROLE (feedback_role_naming_full) — MASTER 가드 (AdminLayout 가드).</li>
 *   <li>한국어 라벨 100% — 영문 라벨 금지.</li>
 *   <li>PR-D admin 패턴 일관 — SheetSyncPage / BlockedPartnersPage 와 동일한 헤더 + chip + table 구조.</li>
 *   <li>blocked 거래처 표시 — Badge "발송금지" (danger). BE sync 시 자동 제외 가정.</li>
 * </ul>
 *
 * <h2>data-testid</h2>
 * <ul>
 *   <li>{@code admin-aligo-group-filter}</li>
 *   <li>{@code admin-aligo-sync-btn}</li>
 *   <li>{@code admin-aligo-preview-table}</li>
 *   <li>{@code admin-aligo-row-{partnerCode}}</li>
 *   <li>{@code admin-aligo-result-added / updated / skipped / failed}</li>
 * </ul>
 */
import { useMemo, useState } from 'react'
import {
  Badge,
  Button,
  DataTable,
  Spinner,
  type DataTableColumn,
} from '@samhan/design-system'
import { usePageTitle } from '../../hooks/usePageTitle'

// ---------------------------------------------------------------------------
// Mock 도메인 타입 (FE 단계에 실 API 타입으로 교체 예정)
// ---------------------------------------------------------------------------

/** 알리고 주소록 그룹. legacy GAS 9번 group 컬럼 그대로 이식. */
type AligoGroup = 'SF_VENDOR' | 'CREDIT' | 'GENERAL'

const GROUP_LABEL: Record<AligoGroup, string> = {
  SF_VENDOR: 'SF벤더',
  CREDIT: '신용정보',
  GENERAL: '일반',
}

/** 거래처 sync 미리보기 1행 (mock). */
interface AligoPartnerPreview {
  partnerCode: string
  partnerName: string
  phone: string
  group: AligoGroup
  status: 'ACTIVE' | 'SUSPENDED'
  blocked: boolean
}

/** sync 결과 4분 통계. BE response shape 가정 (FE 단계에서 실 API 타입 교체). */
interface AligoSyncResult {
  added: number
  updated: number
  skipped: number
  failed: number
  durationMs: number
}

// ---------------------------------------------------------------------------
// Mock 데이터 — FE 단계에 BE API 호출로 교체
// ---------------------------------------------------------------------------

/** TODO(FE-1): BE 연결 시점에 `GET /api/v1/notify/aligo/address-book/preview?group=` 호출로 교체. */
const MOCK_PARTNERS: AligoPartnerPreview[] = [
  {
    partnerCode: 'P001234',
    partnerName: '동부수산',
    phone: '010-1234-5678',
    group: 'SF_VENDOR',
    status: 'ACTIVE',
    blocked: false,
  },
  {
    partnerCode: 'P001235',
    partnerName: '대한물류',
    phone: '010-2345-6789',
    group: 'CREDIT',
    status: 'ACTIVE',
    blocked: false,
  },
  {
    partnerCode: 'P001236',
    partnerName: '한일유통',
    phone: '010-3456-7890',
    group: 'GENERAL',
    status: 'ACTIVE',
    blocked: false,
  },
  {
    partnerCode: 'P001237',
    partnerName: '서해무역',
    phone: '010-4567-8901',
    group: 'SF_VENDOR',
    status: 'SUSPENDED',
    blocked: false,
  },
  {
    partnerCode: 'P001238',
    partnerName: '남해상사',
    phone: '010-5678-9012',
    group: 'GENERAL',
    status: 'ACTIVE',
    blocked: true, // 발송금지 — sync 시 skip 대상
  },
  {
    partnerCode: 'P001239',
    partnerName: '북부창고',
    phone: '010-6789-0123',
    group: 'CREDIT',
    status: 'ACTIVE',
    blocked: false,
  },
]

/** TODO(FE-1): BE 연결 시점에 `POST /api/v1/notify/aligo/address-book/sync` 결과로 교체. */
const MOCK_RESULT: AligoSyncResult = {
  added: 2,
  updated: 3,
  skipped: 1, // blocked 거래처
  failed: 0,
  durationMs: 1340,
}

// ---------------------------------------------------------------------------
// 컴포넌트
// ---------------------------------------------------------------------------

export function AligoAddressBookPage() {
  usePageTitle('알리고 주소록 자동 동기화')

  const [group, setGroup] = useState<AligoGroup | ''>('')
  // mock — 실제로는 useMutation pending state
  const [syncing, setSyncing] = useState(false)
  const [result, setResult] = useState<AligoSyncResult | null>(null)

  const filteredPartners = useMemo<AligoPartnerPreview[]>(() => {
    if (!group) return MOCK_PARTNERS
    return MOCK_PARTNERS.filter((p) => p.group === group)
  }, [group])

  const handleSync = () => {
    // TODO(FE-1): BE 연결 시점에 실 API 호출 + react-query useMutation 으로 교체.
    setSyncing(true)
    setResult(null)
    setTimeout(() => {
      setResult(MOCK_RESULT)
      setSyncing(false)
    }, 800)
  }

  const columns: DataTableColumn<AligoPartnerPreview>[] = useMemo(
    () => [
      {
        key: 'partnerCode',
        header: '거래처 코드',
        width: '120px',
        render: (p) => (
          <span data-testid={`admin-aligo-row-${p.partnerCode}`}>
            {p.partnerCode}
          </span>
        ),
      },
      { key: 'partnerName', header: '상호' },
      { key: 'phone', header: '전화', width: '140px' },
      {
        key: 'group',
        header: '그룹',
        width: '110px',
        render: (p) => (
          <Badge variant={GROUP_VARIANT[p.group]}>{GROUP_LABEL[p.group]}</Badge>
        ),
      },
      {
        key: 'status',
        header: '상태',
        width: '90px',
        render: (p) => (
          <Badge variant={p.status === 'ACTIVE' ? 'success' : 'warning'}>
            {p.status === 'ACTIVE' ? '활성' : '정지'}
          </Badge>
        ),
      },
      {
        key: 'blocked',
        header: '발송 가능',
        width: '110px',
        render: (p) =>
          p.blocked ? (
            <Badge variant="danger">발송금지</Badge>
          ) : (
            <Badge variant="neutral">가능</Badge>
          ),
      },
    ],
    [],
  )

  return (
    <>
      <header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 16,
          marginBottom: 16,
          flexWrap: 'wrap',
        }}
      >
        <div>
          <h3 style={{ margin: '0 0 4px' }}>알리고 주소록 자동 동기화</h3>
          <div
            style={{
              fontSize: 12,
              color: 'var(--color-neutral-600)',
            }}
          >
            거래처 마스터 → 알리고 주소록 group 별 sync. 발송금지 거래처는 자동
            제외됩니다.
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {syncing ? (
            <Spinner
              size="sm"
              tone="var(--color-brand-500)"
              label="동기화 중"
            />
          ) : null}
          <Button
            type="button"
            variant="primary"
            data-testid="admin-aligo-sync-btn"
            disabled={syncing}
            onClick={handleSync}
          >
            {syncing ? '동기화 중…' : '동기화 실행'}
          </Button>
        </div>
      </header>

      <p
        style={{
          margin: '0 0 12px',
          padding: '8px 12px',
          fontSize: 12,
          color: 'var(--color-warning-700, #b45309)',
          background: 'var(--color-warning-50, #fffbeb)',
          border: '1px solid var(--color-warning-200, #fde68a)',
          borderRadius: 4,
        }}
      >
        본 화면은 PR-F1 1차 mock 입니다. BE 연결 시점에 실 API 호출로 교체
        예정입니다 (TODO FE-1).
      </p>

      <div
        style={{
          display: 'flex',
          gap: 12,
          marginBottom: 16,
          flexWrap: 'wrap',
          alignItems: 'center',
        }}
      >
        <label
          htmlFor="admin-aligo-group-filter"
          style={{
            fontSize: 13,
            color: 'var(--color-neutral-700, #374151)',
            fontWeight: 500,
          }}
        >
          그룹
        </label>
        <select
          id="admin-aligo-group-filter"
          value={group}
          onChange={(e) => setGroup(e.target.value as AligoGroup | '')}
          data-testid="admin-aligo-group-filter"
          style={{
            height: 32,
            padding: '0 10px',
            border: '1px solid #D1D5DB',
            borderRadius: 6,
            fontSize: 13,
          }}
        >
          <option value="">전체</option>
          <option value="SF_VENDOR">{GROUP_LABEL.SF_VENDOR}</option>
          <option value="CREDIT">{GROUP_LABEL.CREDIT}</option>
          <option value="GENERAL">{GROUP_LABEL.GENERAL}</option>
        </select>
        <span style={{ fontSize: 12, color: 'var(--color-neutral-500)' }}>
          미리보기 {filteredPartners.length}건
        </span>
      </div>

      {result ? <ResultChips result={result} /> : null}

      <div data-testid="admin-aligo-preview-table">
        <DataTable
          columns={columns}
          rows={filteredPartners}
          rowKey={(p) => p.partnerCode}
          emptyMessage="해당 그룹에 거래처가 없습니다."
        />
      </div>
    </>
  )
}

// ---------------------------------------------------------------------------
// 결과 4분 chip — added / updated / skipped / failed
// ---------------------------------------------------------------------------

interface ResultChipsProps {
  result: AligoSyncResult
}

function ResultChips({ result }: ResultChipsProps) {
  return (
    <div
      style={{
        display: 'flex',
        gap: 12,
        marginBottom: 16,
        flexWrap: 'wrap',
        fontSize: 13,
      }}
    >
      <ResultChip
        label="신규"
        value={result.added}
        tone="brand"
        testId="admin-aligo-result-added"
      />
      <ResultChip
        label="변경"
        value={result.updated}
        tone="success"
        testId="admin-aligo-result-updated"
      />
      <ResultChip
        label="제외"
        value={result.skipped}
        tone="neutral"
        testId="admin-aligo-result-skipped"
      />
      <ResultChip
        label="실패"
        value={result.failed}
        tone="danger"
        testId="admin-aligo-result-failed"
      />
      <ResultChip
        label="소요"
        value={`${result.durationMs}ms`}
        tone="neutral"
      />
    </div>
  )
}

interface ResultChipProps {
  label: string
  value: number | string
  tone: 'brand' | 'success' | 'warning' | 'neutral' | 'danger'
  testId?: string
}

const CHIP_BG: Record<ResultChipProps['tone'], string> = {
  brand: 'var(--color-brand-50)',
  success: 'var(--color-success-50, #ecfdf5)',
  warning: 'var(--color-warning-50, #fffbeb)',
  neutral: 'var(--color-neutral-50)',
  danger: 'var(--color-danger-50, #fef2f2)',
}

const CHIP_FG: Record<ResultChipProps['tone'], string> = {
  brand: 'var(--color-brand-700)',
  success: 'var(--color-success-700, #047857)',
  warning: 'var(--color-warning-700, #b45309)',
  neutral: 'var(--color-neutral-700)',
  danger: 'var(--color-danger-700, #b91c1c)',
}

function ResultChip({ label, value, tone, testId }: ResultChipProps) {
  return (
    <div
      data-testid={testId}
      style={{
        padding: '6px 12px',
        borderRadius: 999,
        background: CHIP_BG[tone],
        color: CHIP_FG[tone],
        fontWeight: 600,
      }}
    >
      {label} {value}
    </div>
  )
}

// ---------------------------------------------------------------------------
// 그룹 → Badge tone
// ---------------------------------------------------------------------------

const GROUP_VARIANT: Record<
  AligoGroup,
  'brand' | 'neutral' | 'success' | 'warning'
> = {
  SF_VENDOR: 'brand',
  CREDIT: 'success',
  GENERAL: 'neutral',
}
