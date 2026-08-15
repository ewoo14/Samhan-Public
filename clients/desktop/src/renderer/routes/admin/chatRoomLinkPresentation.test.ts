import { describe, expect, it } from 'vitest'
import {
  getChatRoomLinkLabel,
  getChatRoomLinkReason,
  getChatRoomPartnerCodeLabel,
  getChatRoomLinkStatus,
} from './chatRoomLinkPresentation'

describe('단톡방 거래처 연결 상태 표시', () => {
  it('연결 행은 실제 코드를 유지한다', () => {
    const row = { partnerCode: '1117100334', partnerLinkStatus: 'LINKED' as const }
    expect(getChatRoomLinkStatus(row)).toBe('LINKED')
    expect(getChatRoomPartnerCodeLabel(row)).toBe('1117100334')
    expect(getChatRoomLinkLabel('LINKED')).toBe('연결')
  })

  it('모호 행은 LEGACY alias 대신 미연결·후보 여러 건을 표시한다', () => {
    const row = {
      partnerCode: 'LEGACY-NAME-abc',
      partnerLinkStatus: 'UNLINKED_AMBIGUOUS' as const,
    }
    expect(getChatRoomPartnerCodeLabel(row)).toBe('미연결')
    expect(getChatRoomLinkLabel(getChatRoomLinkStatus(row))).toBe('미연결')
    expect(getChatRoomLinkReason(row.partnerLinkStatus)).toBe('모호 · 후보 여러 건')
  })

  it('미매칭 행은 후보 없음 사유를 구분한다', () => {
    const row = {
      partnerCode: 'LEGACY-NAME-def',
      partnerLinkStatus: 'UNLINKED_UNMATCHED' as const,
    }
    expect(getChatRoomPartnerCodeLabel(row)).toBe('미연결')
    expect(getChatRoomLinkReason(row.partnerLinkStatus)).toBe('미매칭 · 후보 없음')
  })

  it('상태가 없는 새 행도 alias를 노출하지 않는다', () => {
    const row = { partnerCode: 'LEGACY-NAME-new' }
    expect(getChatRoomLinkStatus(row)).toBe('UNLINKED')
    expect(getChatRoomPartnerCodeLabel(row)).toBe('미연결')
  })
})
