/**
 * 내일자 전표 이미지 페이지 — `/sales/next-day-slip` (PR-E1 FE-4 Samhan Public native).
 *
 * <p>legacy GAS 6번 "내일자 전표 이미지 생성" 의 desktop 자체 화면 이식. 사용자가 기준
 * date 를 선택하면 BE-A5 ({@code GET /slips/next-day-image-data}) 가 date+1 활성 슬립 +
 * chat-room/block/region 5 way 정보를 반환하며, 본 페이지가 단톡방별 섹션으로 재그룹핑
 * 후 인쇄 미리보기 / 단톡방별 인쇄 진입을 제공한다.
 *
 * <h2>화면 구성</h2>
 * <ul>
 *   <li>상단: 날짜 입력 (default = today, 응답 = date+1 안내) + [인쇄 미리보기] /
 *       [단톡방별 인쇄] 액션 버튼 2종</li>
 *   <li>본문: 단톡방별 섹션 카드 (chatRoomName + 거래처 count + 전표번호 요약)</li>
 *   <li>하단: 발송금지 거래처 자동 제외 안내 (BE 가 blocked=true 슬립을 응답에 포함하나
 *       FE 가 자동 제외 + 제외 건수 표시)</li>
 * </ul>
 *
 * <h2>인쇄 진입</h2>
 * <ul>
 *   <li>[인쇄 미리보기] → {@code /print/next-day-slip?date=YYYY-MM-DD} (한 페이지에 모든 단톡방)</li>
 *   <li>[단톡방별 인쇄] → 동일 path + {@code &perRoom=1} (page-break-after per room)</li>
 * </ul>
 * <p>실제 인쇄 view 는 Designer commit 1f85605 의 {@link NextDaySlipView} 가 담당.
 *
 * <h2>UUID 비공개</h2>
 * <p>화면 노출 식별자는 slipNo / partnerCode / partnerName / chatRoomName / driverPhone 만.
 * BE 응답 entry 는 partner_id 미포함 (V15 partner_code snapshot 직접 사용).
 *
 * <h2>접근 제어</h2>
 * <p>SALES / MANAGER / MASTER 만 진입 — BE {@code @PreAuthorize} 와 1:1. RoleGuard 는 router
 * 측에서 적용.
 *
 * <h2>data-testid</h2>
 * <ul>
 *   <li>{@code next-day-slip-date} — 날짜 입력</li>
 *   <li>{@code next-day-slip-summary} — 단톡방 N건 / 전표 합계 N건 요약 영역</li>
 *   <li>{@code next-day-slip-room-{chatRoomName}} — 단톡방별 섹션</li>
 *   <li>{@code next-day-slip-print-button} — 인쇄 미리보기</li>
 *   <li>{@code next-day-slip-print-per-room-button} — 단톡방별 인쇄</li>
 * </ul>
 */
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@samhan/design-system'
import {
  getNextDayImageData,
  groupByChatRoom,
} from '../api/nextDaySlipApi'
import { usePageTitle } from '../hooks/usePageTitle'
import { krDate } from '../print/PrintLayout'

/**
 * 오늘 일자 (YYYY-MM-DD) — date 입력 default.
 *
 * <p>local 시간 기준 (운영 환경 KST 만 가정 — UTC 변환 미수행, BE 가 동일 기준 적용).
 */
function today(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function NextDaySlipPage() {
  usePageTitle('내일자 전표 이미지')
  const navigate = useNavigate()

  const [date, setDate] = useState<string>(today())

  const query = useQuery({
    queryKey: ['next-day-slip-image', date],
    queryFn: () => getNextDayImageData(date),
    enabled: !!date,
  })

  /** BE 응답을 단톡방별로 재그룹핑 + 발송금지 자동 제외. */
  const grouped = useMemo(() => {
    if (!query.data) {
      return { groups: [], blockedExcludedCount: 0 }
    }
    return groupByChatRoom(query.data)
  }, [query.data])

  const targetDate = query.data?.targetDate ?? ''
  const totalSlips = query.data?.totalSlips ?? 0
  const totalRoomSlips = grouped.groups.reduce(
    (s, g) => s + g.partners.length,
    0,
  )

  /** 인쇄 미리보기로 이동 — Designer NextDaySlipView 가 같은 ?date= 를 읽음. */
  const handlePrint = () => {
    navigate(`/print/next-day-slip?date=${encodeURIComponent(date)}`)
  }

  /** 단톡방별 분리 인쇄 — perRoom=1 query 로 page-break-after CSS 활성. */
  const handlePrintPerRoom = () => {
    navigate(
      `/print/next-day-slip?date=${encodeURIComponent(date)}&perRoom=1`,
    )
  }

  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <h3 style={{ margin: 0 }}>내일자 전표 이미지</h3>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 13, color: 'var(--color-neutral-600)' }}>
              기준 날짜
            </span>
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              data-testid="next-day-slip-date"
              className="next-day-slip-date-input"
            />
          </label>
          <Button
            variant="primary"
            onClick={handlePrint}
            disabled={!query.data || grouped.groups.length === 0}
            data-testid="next-day-slip-print-button"
          >
            인쇄 미리보기
          </Button>
          <Button
            variant="ghost"
            onClick={handlePrintPerRoom}
            disabled={!query.data || grouped.groups.length === 0}
            data-testid="next-day-slip-print-per-room-button"
          >
            단톡방별 인쇄
          </Button>
        </div>
      </div>

      <div
        data-testid="next-day-slip-summary"
        style={{
          padding: '12px 16px',
          background: 'var(--color-neutral-50, #F9FAFB)',
          border: '1px solid var(--color-neutral-200, #E5E7EB)',
          borderRadius: 6,
          marginBottom: 16,
          display: 'flex',
          flexWrap: 'wrap',
          gap: 16,
          alignItems: 'center',
          fontSize: 13,
        }}
      >
        <span>
          기준 날짜:{' '}
          <strong>{krDate(date)}</strong>
        </span>
        <span aria-hidden="true" style={{ color: '#9CA3AF' }}>
          →
        </span>
        <span>
          출고 예정일:{' '}
          <strong>{targetDate ? krDate(targetDate) : '(조회 중)'}</strong>
        </span>
        <span style={{ marginLeft: 'auto', color: 'var(--color-neutral-700)' }}>
          단톡방 {grouped.groups.length}건 · 전표 합계{' '}
          {totalRoomSlips.toLocaleString('ko-KR')}건
          {grouped.blockedExcludedCount > 0 ? (
            <>
              {' · '}
              <span style={{ color: '#B91C1C' }}>
                발송금지 자동 제외{' '}
                {grouped.blockedExcludedCount.toLocaleString('ko-KR')}건
              </span>
            </>
          ) : null}
        </span>
      </div>

      {query.isLoading ? (
        <div style={{ padding: 24, textAlign: 'center', color: '#6B7280' }}>
          불러오는 중...
        </div>
      ) : null}

      {query.isError ? (
        <div className="error-banner" role="alert" style={{ marginTop: 16 }}>
          내일자 전표 이미지 데이터를 불러오지 못했습니다. 백엔드 연결을 확인하세요.
        </div>
      ) : null}

      {query.data && grouped.groups.length === 0 ? (
        <div
          style={{
            padding: 32,
            textAlign: 'center',
            color: '#6B7280',
            border: '1px dashed var(--color-neutral-300, #D1D5DB)',
            borderRadius: 6,
          }}
        >
          {totalSlips === 0
            ? '다음날 출고 예정 전표가 없습니다.'
            : '발송 대상 전표가 없습니다 (모두 발송금지 또는 단톡방 미매핑).'}
        </div>
      ) : null}

      {grouped.groups.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {grouped.groups.map((room) => (
            <section
              key={room.chatRoomName}
              data-testid={`next-day-slip-room-${room.chatRoomName}`}
              style={{
                border: '1px solid var(--color-neutral-200, #E5E7EB)',
                borderRadius: 6,
                background: '#fff',
              }}
            >
              <header
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '10px 16px',
                  background: 'var(--color-neutral-50, #F9FAFB)',
                  borderBottom:
                    '1px solid var(--color-neutral-200, #E5E7EB)',
                  borderRadius: '6px 6px 0 0',
                }}
              >
                <h4 style={{ margin: 0, fontSize: 14 }}>
                  {room.chatRoomName}
                </h4>
                <span
                  style={{ fontSize: 12, color: 'var(--color-neutral-600)' }}
                >
                  거래처{' '}
                  {new Set(room.partners.map((p) => p.partnerCode ?? ''))
                    .size}
                  건 · 전표{' '}
                  {room.partners.length.toLocaleString('ko-KR')}건
                </span>
              </header>
              <div style={{ padding: '8px 16px' }}>
                {room.partners.length === 0 ? (
                  <div
                    style={{
                      padding: '8px 0',
                      color: '#9CA3AF',
                      fontSize: 13,
                    }}
                  >
                    해당 단톡방의 전표가 없습니다.
                  </div>
                ) : (
                  <table
                    style={{
                      width: '100%',
                      borderCollapse: 'collapse',
                      fontSize: 13,
                    }}
                  >
                    <thead>
                      <tr style={{ borderBottom: '1px solid #E5E7EB' }}>
                        <th
                          style={{
                            textAlign: 'left',
                            padding: '6px 8px',
                            width: '180px',
                          }}
                        >
                          전표번호
                        </th>
                        <th style={{ textAlign: 'left', padding: '6px 8px' }}>
                          거래처
                        </th>
                        <th
                          style={{
                            textAlign: 'left',
                            padding: '6px 8px',
                            width: '160px',
                          }}
                        >
                          기사
                        </th>
                        <th
                          style={{
                            textAlign: 'left',
                            padding: '6px 8px',
                            width: '140px',
                          }}
                        >
                          지역
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {room.partners.map((slip) => (
                        <tr
                          key={`${room.chatRoomName}-${slip.slipNo}`}
                          style={{ borderBottom: '1px solid #F3F4F6' }}
                        >
                          <td style={{ padding: '6px 8px' }}>{slip.slipNo}</td>
                          <td style={{ padding: '6px 8px' }}>
                            {slip.partnerName ?? '-'}
                            {slip.partnerCode ? (
                              <span
                                style={{
                                  marginLeft: 6,
                                  fontSize: 11,
                                  color: '#9CA3AF',
                                }}
                              >
                                ({slip.partnerCode})
                              </span>
                            ) : null}
                          </td>
                          <td style={{ padding: '6px 8px' }}>
                            {slip.driverName ?? '-'}
                            {slip.driverPhone ? (
                              <span
                                style={{
                                  marginLeft: 6,
                                  fontSize: 11,
                                  color: '#9CA3AF',
                                }}
                              >
                                {slip.driverPhone}
                              </span>
                            ) : null}
                          </td>
                          <td style={{ padding: '6px 8px' }}>
                            {slip.classifiedRegionGroup ?? '미분류'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </section>
          ))}
        </div>
      ) : null}

      <footer
        style={{
          marginTop: 24,
          padding: '12px 16px',
          fontSize: 12,
          color: 'var(--color-neutral-600)',
          background: 'var(--color-neutral-50, #F9FAFB)',
          borderRadius: 6,
        }}
      >
        ※ 발송금지 거래처 (admin 등록) 는 응답에서 자동 제외됩니다. 단톡방 미매핑
        전표는 별도 섹션 "단톡방 미매핑" 에 표시되며, 사이드바 [관리자 → 단톡방 매핑]
        에서 매핑을 추가하세요.
      </footer>
    </>
  )
}
