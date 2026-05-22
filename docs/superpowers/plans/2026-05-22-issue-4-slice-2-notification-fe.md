# Issue 4 Slice 2 — 통합 알림 센터 FE UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** AppLayout 우상단 알림 종 → multi-channel dropdown panel. 사이드바 "알림 내역" 메뉴 + NotificationHistoryPage. BE Slice 1 (`/api/notifications/**`) 와 1:1 정합.

**Architecture:** React + TanStack Query 60s polling. Dropdown panel = 채널별 grouping. acknowledge → mutation 후 cache invalidate → panel 에서 사라짐. 사이드바 "알림 내역" 신규 메뉴 (대시보드 다음, 모든 인증 사용자 가시).

**Tech Stack:** React 18, TanStack Query 5, Vite, TypeScript, electron-vite, axios

**Spec:** [`docs/superpowers/specs/2026-05-22-issue-4-unified-notification-center-design.md`](../specs/2026-05-22-issue-4-unified-notification-center-design.md) Slice 2

---

## File Structure

### Create
- `clients/desktop/src/renderer/api/notificationApi.ts` — fetchMyUnread / fetchHistory (Pageable) / acknowledge
- `clients/desktop/src/renderer/components/NotificationBellDropdown.tsx` — 종 클릭 dropdown panel
- `clients/desktop/src/renderer/routes/NotificationHistoryPage.tsx` — 전체 history 페이지 (Pageable + 필터)

### Modify
- `clients/desktop/src/renderer/components/AppLayout.tsx` — 기존 안전재고 chip 제거 + NotificationBellDropdown 통합 + 사이드바 "알림 내역" 메뉴 추가
- `clients/desktop/src/renderer/routes/index.tsx` — `/notifications` 라우트 (NotificationHistoryPage)
- `clients/desktop/src/renderer/api/mock.ts` — MOCK_NOTIFICATION_CENTER seed 3건 (안전재고 + 메신저 + 시스템 공지)

---

## Naming
TypeScript interface 명 BE record 와 1:1: `NotificationCenter`, `NotificationCenterResponse`, `NotificationCenterPage`, `NotificationPublishRequest`, `NotificationSeverity` (string union: `'INFO' | 'WARNING' | 'CRITICAL'`).

`SafetyStockAlert` API (`safetyStockApi.ts`) 는 그대로 유지 — Slice 3 에서 source 통합 시 안전재고 alert 가 NotificationCenter 로 자동 전환되지만 deprecated 잔존 endpoint 는 1 슬라이스 더 유지 (cross-channel 검증용).

---

## Task 1: notificationApi.ts (BE 1:1 정합 TS client)

**Files:**
- Create: `clients/desktop/src/renderer/api/notificationApi.ts`

- [ ] **Step 1: notificationApi.ts 작성**

```ts
/**
 * 통합 알림 센터 API client — Issue 4 Slice 2.
 *
 * BE (notification-service) 정합:
 * - GET  /api/notifications/my                   → 미확인 알림 list (최신순)
 * - GET  /api/notifications/history?size&page    → 전체 history (Pageable)
 * - POST /api/notifications/{id}/acknowledge     → read_at 설정 (idempotent)
 *
 * UUID 비공개 가드: id, deeplink, 비즈니스 라벨 (title/body/channel) 만 화면 노출.
 */
import { apiClient, type ApiEnvelope } from './client'

/** BE NotificationSeverity enum 정합. */
export type NotificationSeverity = 'INFO' | 'WARNING' | 'CRITICAL'

/** BE NotificationCenterResponse record 와 1:1. */
export interface NotificationCenter {
  id: string
  channel: string
  severity: NotificationSeverity
  title: string
  body: string | null
  deeplink: string | null
  createdAt: string  // ISO LocalDateTime
  readAt: string | null
}

/** BE NotificationCenterPage record 와 1:1. */
export interface NotificationCenterPage {
  content: NotificationCenter[]
  number: number
  size: number
  totalElements: number
  totalPages: number
}

export async function fetchMyUnread(): Promise<NotificationCenter[]> {
  const res = await apiClient.get<ApiEnvelope<NotificationCenter[]>>('/api/notifications/my')
  return res.data.data
}

export async function fetchHistory(page = 0, size = 50): Promise<NotificationCenterPage> {
  const res = await apiClient.get<ApiEnvelope<NotificationCenterPage>>(
    '/api/notifications/history',
    { params: { page, size } },
  )
  return res.data.data
}

export async function acknowledgeNotification(id: string): Promise<void> {
  await apiClient.post(`/api/notifications/${encodeURIComponent(id)}/acknowledge`)
}

/** 채널별 그룹핑 헬퍼 — dropdown panel section 렌더용. */
export function groupByChannel(
  notifications: NotificationCenter[],
): Record<string, NotificationCenter[]> {
  const out: Record<string, NotificationCenter[]> = {}
  for (const n of notifications) {
    const key = n.channel
    if (!out[key]) out[key] = []
    out[key].push(n)
  }
  return out
}

/** 채널 키 → 사용자 노출 라벨. */
export const CHANNEL_LABEL: Record<string, string> = {
  SAFETY_STOCK: '안전재고',
  MESSENGER: '메신저',
  APPROVAL: '결재',
  ECOUNT_IMPORT: '이카운트 import',
}
```

- [ ] **Step 2: typecheck 검증**

Run: `cd clients/desktop && npm run typecheck`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add clients/desktop/src/renderer/api/notificationApi.ts
git commit -m "feat(notification-fe): Slice 2 Task 1 — notificationApi.ts (BE Slice 1 정합 client)"
```

---

## Task 2: NotificationBellDropdown.tsx (채널별 grouping panel)

**Files:**
- Create: `clients/desktop/src/renderer/components/NotificationBellDropdown.tsx`

- [ ] **Step 1: 컴포넌트 작성**

```tsx
/**
 * 알림 종 dropdown panel — Issue 4 Slice 2.
 *
 * - 60초 polling fetchMyUnread
 * - 채널별 grouping (안전재고 / 메신저 / 결재 / 이카운트 import)
 * - 각 row 클릭 → acknowledge mutation + cache invalidate + deeplink navigate
 * - 빈 panel 시 "확인할 알림이 없습니다" 표시
 * - 하단 "전체 알림 보기" 링크 → /notifications
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  acknowledgeNotification,
  CHANNEL_LABEL,
  fetchMyUnread,
  groupByChannel,
  type NotificationCenter,
} from '../api/notificationApi'

export function NotificationBellDropdown() {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: notifications = [] } = useQuery({
    queryKey: ['notifications', 'my'],
    queryFn: fetchMyUnread,
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  })

  const ackMutation = useMutation({
    mutationFn: acknowledgeNotification,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications', 'my'] })
    },
  })

  // 외부 클릭 닫기
  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  const handleClickRow = (n: NotificationCenter) => {
    ackMutation.mutate(n.id)
    if (n.deeplink) {
      navigate(n.deeplink)
      setOpen(false)
    }
  }

  const grouped = groupByChannel(notifications)
  const channelKeys = Object.keys(grouped)
  const count = notifications.length

  return (
    <div ref={containerRef} style={{ position: 'relative' }}>
      <button
        type="button"
        data-testid="notification-bell"
        aria-label={`알림 ${count}건`}
        onClick={() => setOpen((v) => !v)}
        style={{
          position: 'relative',
          width: 36,
          height: 36,
          background: 'transparent',
          border: '1px solid var(--color-neutral-200)',
          borderRadius: 8,
          cursor: 'pointer',
          padding: 0,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--color-neutral-600)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
        {count > 0 ? (
          <span
            data-testid="notification-bell-badge"
            style={{
              position: 'absolute',
              top: -6,
              right: -6,
              minWidth: 18,
              height: 18,
              borderRadius: 9,
              background: 'var(--color-danger-500)',
              color: 'var(--color-neutral-0)',
              fontSize: 10,
              fontWeight: 700,
              padding: '0 4px',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {count > 99 ? '99+' : count}
          </span>
        ) : null}
      </button>

      {open ? (
        <div
          data-testid="notification-bell-panel"
          style={{
            position: 'absolute',
            top: 44,
            right: 0,
            width: 360,
            maxHeight: 480,
            overflowY: 'auto',
            background: 'var(--color-neutral-0)',
            border: '1px solid var(--color-neutral-200)',
            borderRadius: 8,
            boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
            zIndex: 1000,
          }}
        >
          <div style={{ padding: 12, borderBottom: '1px solid var(--color-neutral-100)', fontWeight: 700 }}>
            알림 {count > 0 ? `(${count})` : ''}
          </div>

          {channelKeys.length === 0 ? (
            <div style={{ padding: 24, textAlign: 'center', color: 'var(--color-neutral-500)' }}>
              확인할 알림이 없습니다
            </div>
          ) : (
            channelKeys.map((channel) => {
              const rows = grouped[channel]!
              const label = CHANNEL_LABEL[channel] ?? channel
              return (
                <div key={channel} data-testid={`notification-section-${channel}`}>
                  <div
                    style={{
                      padding: '8px 12px',
                      background: 'var(--color-neutral-50)',
                      fontSize: 12,
                      fontWeight: 600,
                      color: 'var(--color-neutral-700)',
                    }}
                  >
                    {label} ({rows.length})
                  </div>
                  {rows.slice(0, 5).map((n) => (
                    <button
                      type="button"
                      key={n.id}
                      data-testid={`notification-row-${n.id}`}
                      onClick={() => handleClickRow(n)}
                      style={{
                        display: 'block',
                        width: '100%',
                        padding: '10px 12px',
                        textAlign: 'left',
                        background: 'transparent',
                        border: 'none',
                        borderBottom: '1px solid var(--color-neutral-100)',
                        cursor: 'pointer',
                      }}
                    >
                      <div style={{ fontSize: 13, fontWeight: 500, marginBottom: 2 }}>{n.title}</div>
                      {n.body ? (
                        <div
                          style={{
                            fontSize: 12,
                            color: 'var(--color-neutral-500)',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {n.body}
                        </div>
                      ) : null}
                    </button>
                  ))}
                  {rows.length > 5 ? (
                    <button
                      type="button"
                      onClick={() => {
                        navigate('/notifications')
                        setOpen(false)
                      }}
                      style={{
                        display: 'block',
                        width: '100%',
                        padding: '8px 12px',
                        textAlign: 'right',
                        background: 'transparent',
                        border: 'none',
                        fontSize: 12,
                        color: 'var(--color-primary-600)',
                        cursor: 'pointer',
                      }}
                    >
                      {rows.length - 5}건 더 보기 →
                    </button>
                  ) : null}
                </div>
              )
            })
          )}

          <div style={{ padding: 12, borderTop: '1px solid var(--color-neutral-100)', textAlign: 'center' }}>
            <button
              type="button"
              data-testid="notification-history-link"
              onClick={() => {
                navigate('/notifications')
                setOpen(false)
              }}
              style={{
                background: 'transparent',
                border: 'none',
                color: 'var(--color-primary-600)',
                fontSize: 13,
                cursor: 'pointer',
              }}
            >
              전체 알림 보기 →
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}
```

- [ ] **Step 2: typecheck**

Run: `cd clients/desktop && npm run typecheck`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add clients/desktop/src/renderer/components/NotificationBellDropdown.tsx
git commit -m "feat(notification-fe): Slice 2 Task 2 — NotificationBellDropdown (60s polling + 채널별 grouping + acknowledge mutation)"
```

---

## Task 3: NotificationHistoryPage.tsx (Pageable + 필터)

**Files:**
- Create: `clients/desktop/src/renderer/routes/NotificationHistoryPage.tsx`

- [ ] **Step 1: 컴포넌트 작성**

```tsx
/**
 * 알림 내역 페이지 — Issue 4 Slice 2.
 *
 * /notifications 라우트. 사이드바 "알림 내역" 메뉴 → 본 페이지.
 * BE GET /api/notifications/history Pageable (page, size).
 * 채널/읽음 필터는 client-side 필터 (1차 — 향후 BE 필터 endpoint 추가 시 server-side 로 이동).
 */
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import {
  CHANNEL_LABEL,
  fetchHistory,
  type NotificationCenter,
  type NotificationSeverity,
} from '../api/notificationApi'
import { usePageTitle } from '../hooks/usePageTitle'

const PAGE_SIZE_OPTIONS = [50, 100, 200, 500] as const

export function NotificationHistoryPage() {
  usePageTitle('알림 내역')

  const [page, setPage] = useState(0)
  const [size, setSize] = useState<(typeof PAGE_SIZE_OPTIONS)[number]>(50)
  const [channelFilter, setChannelFilter] = useState<string>('')
  const [showOnlyUnread, setShowOnlyUnread] = useState(false)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['notifications', 'history', page, size],
    queryFn: () => fetchHistory(page, size),
    staleTime: 30_000,
  })

  const rows = (data?.content ?? []).filter((n) => {
    if (channelFilter && n.channel !== channelFilter) return false
    if (showOnlyUnread && n.readAt !== null) return false
    return true
  })

  const channels = Array.from(new Set((data?.content ?? []).map((n) => n.channel)))

  return (
    <div data-testid="notification-history-page" style={{ padding: 16 }}>
      <h2 style={{ margin: '0 0 16px 0' }}>알림 내역</h2>

      <div style={{ display: 'flex', gap: 12, marginBottom: 12, flexWrap: 'wrap' }}>
        <label>
          채널{' '}
          <select
            data-testid="notification-channel-filter"
            value={channelFilter}
            onChange={(e) => setChannelFilter(e.target.value)}
          >
            <option value="">전체</option>
            {channels.map((ch) => (
              <option key={ch} value={ch}>
                {CHANNEL_LABEL[ch] ?? ch}
              </option>
            ))}
          </select>
        </label>
        <label>
          <input
            type="checkbox"
            data-testid="notification-unread-only"
            checked={showOnlyUnread}
            onChange={(e) => setShowOnlyUnread(e.target.checked)}
          />{' '}
          미확인만
        </label>
        <label>
          페이지 크기{' '}
          <select
            data-testid="notification-page-size"
            value={size}
            onChange={(e) => {
              setSize(Number(e.target.value) as (typeof PAGE_SIZE_OPTIONS)[number])
              setPage(0)
            }}
          >
            {PAGE_SIZE_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
      </div>

      {isLoading ? (
        <div>로딩 중...</div>
      ) : isError ? (
        <div style={{ color: 'var(--color-danger-500)' }}>알림 내역을 불러오지 못했습니다</div>
      ) : (
        <>
          <table data-testid="notification-history-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--color-neutral-300)' }}>
                <th style={{ textAlign: 'left', padding: 8 }}>발생 시각</th>
                <th style={{ textAlign: 'left', padding: 8 }}>채널</th>
                <th style={{ textAlign: 'left', padding: 8 }}>심각도</th>
                <th style={{ textAlign: 'left', padding: 8 }}>제목</th>
                <th style={{ textAlign: 'left', padding: 8 }}>본문</th>
                <th style={{ textAlign: 'left', padding: 8 }}>확인</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((n) => (
                <tr key={n.id} style={{ borderBottom: '1px solid var(--color-neutral-100)' }}>
                  <td style={{ padding: 8, fontSize: 12 }}>{n.createdAt}</td>
                  <td style={{ padding: 8 }}>{CHANNEL_LABEL[n.channel] ?? n.channel}</td>
                  <td style={{ padding: 8 }}>
                    <SeverityBadge severity={n.severity} />
                  </td>
                  <td style={{ padding: 8 }}>{n.title}</td>
                  <td style={{ padding: 8, fontSize: 12, color: 'var(--color-neutral-500)' }}>{n.body ?? ''}</td>
                  <td style={{ padding: 8, fontSize: 12 }}>
                    {n.readAt ? <span style={{ color: 'var(--color-neutral-500)' }}>확인됨</span> : <span style={{ color: 'var(--color-danger-500)', fontWeight: 600 }}>미확인</span>}
                  </td>
                </tr>
              ))}
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ padding: 24, textAlign: 'center', color: 'var(--color-neutral-500)' }}>
                    조건에 맞는 알림이 없습니다
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>

          <div style={{ display: 'flex', gap: 8, marginTop: 12, justifyContent: 'center', alignItems: 'center' }}>
            <button type="button" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              이전
            </button>
            <span data-testid="notification-history-page-info">
              {data?.number != null ? data.number + 1 : 1} / {data?.totalPages ?? 1} (전체 {data?.totalElements ?? 0}건)
            </span>
            <button
              type="button"
              disabled={data == null || data.number >= data.totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              다음
            </button>
          </div>
        </>
      )}
    </div>
  )
}

function SeverityBadge({ severity }: { severity: NotificationSeverity }) {
  const colorMap: Record<NotificationSeverity, string> = {
    INFO: 'var(--color-neutral-400)',
    WARNING: 'var(--color-warning-500)',
    CRITICAL: 'var(--color-danger-500)',
  }
  return (
    <span
      style={{
        background: colorMap[severity],
        color: 'var(--color-neutral-0)',
        fontSize: 11,
        fontWeight: 600,
        padding: '2px 6px',
        borderRadius: 4,
      }}
    >
      {severity}
    </span>
  )
}
```

- [ ] **Step 2: typecheck**

Run: `cd clients/desktop && npm run typecheck`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add clients/desktop/src/renderer/routes/NotificationHistoryPage.tsx
git commit -m "feat(notification-fe): Slice 2 Task 3 — NotificationHistoryPage (Pageable + 채널/읽음 필터)"
```

---

## Task 4: AppLayout 통합 (기존 안전재고 chip 제거 + 종 + 사이드바 메뉴)

**Files:**
- Modify: `clients/desktop/src/renderer/components/AppLayout.tsx`

- [ ] **Step 1: 기존 안전재고 chip 코드 제거**

위치: `clients/desktop/src/renderer/components/AppLayout.tsx`

제거할 import:
- `fetchSafetyStockAlertCount` (from `./api/safetyStockApi` — 다른 import 가 있으면 유지)

제거할 state/useEffect:
- `const [safetyStockCount, setSafetyStockCount] = useState(0)` (line 208)
- `refreshSafetyStockCount` useCallback (line 210-215)
- 60초 polling useEffect (line 216-220)

제거할 JSX 블록: `app-header-actions` 안의 헤더 chip (line 1234-1290 영역, `header-safety-stock-count-chip` 버튼 + 종 + 배지 + 'X건' 텍스트 전체).

대신 다음 코드 삽입 (동일 `app-header-actions` 안):

```tsx
<NotificationBellDropdown />
```

import 추가:
```tsx
import { NotificationBellDropdown } from './NotificationBellDropdown'
```

- [ ] **Step 2: 사이드바 "알림 내역" 메뉴 추가**

위치: `clients/desktop/src/renderer/components/AppLayout.tsx` line 417-419 (대시보드 NavLink 직후)

기존:
```tsx
<NavLink to="/" end>
  대시보드
</NavLink>
<NavLink to="/warehouses" data-testid="sidebar-warehouses">창고관리</NavLink>
```

변경:
```tsx
<NavLink to="/" end>
  대시보드
</NavLink>
<NavLink to="/notifications" data-testid="sidebar-notifications">
  알림 내역
</NavLink>
<NavLink to="/warehouses" data-testid="sidebar-warehouses">창고관리</NavLink>
```

- [ ] **Step 3: typecheck**

Run: `cd clients/desktop && npm run typecheck`
Expected: exit 0

- [ ] **Step 4: Commit**

```bash
git add clients/desktop/src/renderer/components/AppLayout.tsx
git commit -m "feat(notification-fe): Slice 2 Task 4 — AppLayout 통합 (안전재고 chip 제거 + NotificationBellDropdown + 사이드바 알림 내역)"
```

---

## Task 5: routes/index.tsx 라우트 등록

**Files:**
- Modify: `clients/desktop/src/renderer/routes/index.tsx`

- [ ] **Step 1: import + Route 추가**

위치: routes/index.tsx 의 import 블록 + Route 정의 블록

import 추가:
```tsx
import { NotificationHistoryPage } from './NotificationHistoryPage'
```

Route 추가 (대시보드 Route 다음 적절한 위치):
```tsx
<Route path="/notifications" element={<NotificationHistoryPage />} />
```

> 권한 가드: 모든 인증 사용자 가시 (BE `@PreAuthorize isAuthenticated`). PermissionGuard 불요.

- [ ] **Step 2: typecheck**

Run: `cd clients/desktop && npm run typecheck`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add clients/desktop/src/renderer/routes/index.tsx
git commit -m "feat(notification-fe): Slice 2 Task 5 — /notifications 라우트 등록 (NotificationHistoryPage)"
```

---

## Task 6: mock.ts seed (dev VITE_MOCK_MODE=1 동작)

**Files:**
- Modify: `clients/desktop/src/renderer/api/mock.ts`

- [ ] **Step 1: MOCK_NOTIFICATION_CENTER seed 3건 추가**

`mock.ts` 안에 다음 추가 (기존 mock seed 패턴 일관):

```ts
// Issue 4 Slice 2 — 통합 알림 센터 mock seed
const MOCK_NOTIFICATION_CENTER = [
  {
    id: 'n0000000-0000-0000-0000-000000000001',
    channel: 'SAFETY_STOCK',
    severity: 'WARNING' as const,
    title: 'AJ056RXH4BC1 HQ 본사 창고 안전재고 부족',
    body: '현재 43 / 임계 50 (부족 -7)',
    deeplink: '/inventory/safety-stock-alerts',
    createdAt: '2026-05-22T10:00:00',
    readAt: null,
  },
  {
    id: 'n0000000-0000-0000-0000-000000000002',
    channel: 'MESSENGER',
    severity: 'INFO' as const,
    title: '김미선 → 새 메시지',
    body: '김종 압축기 견적 검토 부탁드립니다',
    deeplink: '/messenger',
    createdAt: '2026-05-22T10:30:00',
    readAt: null,
  },
  {
    id: 'n0000000-0000-0000-0000-000000000003',
    channel: 'ECOUNT_IMPORT',
    severity: 'CRITICAL' as const,
    title: 'mig-2 product import 실패',
    body: '2836 row rejected (Eureka product-service stale)',
    deeplink: '/admin/ecount/reimport',
    createdAt: '2026-05-22T09:00:00',
    readAt: null,
  },
]
```

mock handler 추가 (기존 `getMockResponse` 내부 match):

```ts
// GET /api/notifications/my
if (method === 'GET' && url.endsWith('/api/notifications/my')) {
  return envelope(MOCK_NOTIFICATION_CENTER.filter((n) => n.readAt === null))
}

// GET /api/notifications/history?page=&size=
if (method === 'GET' && url.includes('/api/notifications/history')) {
  return envelope({
    content: MOCK_NOTIFICATION_CENTER,
    number: 0,
    size: 50,
    totalElements: MOCK_NOTIFICATION_CENTER.length,
    totalPages: 1,
  })
}

// POST /api/notifications/{id}/acknowledge
const ackMatch = url.match(/\/api\/notifications\/([^/]+)\/acknowledge$/)
if (method === 'POST' && ackMatch) {
  const id = ackMatch[1]!
  const target = MOCK_NOTIFICATION_CENTER.find((n) => n.id === id)
  if (target) target.readAt = new Date().toISOString()
  return envelope(null)
}
```

- [ ] **Step 2: typecheck**

Run: `cd clients/desktop && npm run typecheck`
Expected: exit 0

- [ ] **Step 3: Commit**

```bash
git add clients/desktop/src/renderer/api/mock.ts
git commit -m "feat(notification-fe): Slice 2 Task 6 — mock.ts seed 3건 (안전재고 + 메신저 + 이카운트 import)"
```

---

## Task 7: PR 발행

- [ ] **Step 1: 전체 typecheck + lint**

Run: `cd clients/desktop && npm run typecheck && npm run lint`
Expected: exit 0 (기존 warning 2건 유지)

- [ ] **Step 2: Push + PR 발행**

```bash
git push -u origin feat/issue-4-slice-2-notification-fe

gh pr create --title "[FEAT] Issue 4 Slice 2 — 통합 알림 센터 FE UI" \
  --body "$(cat <<'EOF'
## 요약

Issue 4 Slice 2 — FE UI 통합. PR #297 (Slice 1 BE) 머지 후 후속.

## 변경 (6 task)

### FE
- \`notificationApi.ts\` — BE NotificationCenter 1:1 정합 (fetchMyUnread/fetchHistory/acknowledge)
- \`NotificationBellDropdown\` — 60s polling, 채널별 grouping, acknowledge mutation
- \`NotificationHistoryPage\` — Pageable + 채널/읽음 필터
- \`AppLayout\` — 기존 안전재고 chip 제거 → NotificationBellDropdown 통합, 사이드바 "알림 내역" 메뉴 (대시보드 다음)
- \`routes/index.tsx\` — /notifications 라우트
- \`mock.ts\` — MOCK_NOTIFICATION_CENTER seed 3건 + 3 handler

## 검증
- npm run typecheck PASS
- npm run lint PASS

## 다음 Slice
- Slice 3: source services 통합 (SafetyStockService + MessageService → NotificationPublisher)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review

### Spec coverage
- [x] dropdown panel (Task 2)
- [x] 채널별 grouping (Task 2 groupByChannel)
- [x] read/unread (Task 2 ackMutation)
- [x] 사이드바 "알림 내역" 메뉴 (Task 4)
- [x] NotificationHistoryPage Pageable + 필터 (Task 3)
- [x] mock 환경 동작 (Task 6)

### Placeholder scan
0건

### Type consistency
- `NotificationCenter` / `NotificationSeverity` / `NotificationCenterPage` 명 BE record 와 일치
- `acknowledgeNotification(id)` 시그니처 Task 1 → Task 2 일치

### Scope
단일 Slice 2 (FE UI) — Slice 1 BE 변경 의존, Slice 3 source service 변경은 별도 PR.
