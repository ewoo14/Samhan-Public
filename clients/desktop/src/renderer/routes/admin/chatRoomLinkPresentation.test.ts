import { describe, expect, it } from 'vitest'
import {
  getChatRoomLinkLabel,
  getChatRoomLinkReason,
  getChatRoomPartnerCodeLabel,
  getChatRoomLinkStatus,
  getChatRoomDeleteConfirmationMessage,
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

  it('미연결 삭제 확인창은 alias 대신 미연결과 사업자명·방을 보여준다', () => {
    const message = getChatRoomDeleteConfirmationMessage({
      partnerCode: 'LEGACY-NAME-delete',
      partnerLinkStatus: 'UNLINKED_AMBIGUOUS',
      partnerBusinessName: '확인용 사업자명',
      chatRoomName: '확인용 단톡방',
    })
    expect(message).toContain('거래처: 미연결 (확인용 사업자명)')
    expect(message).toContain('단톡방: 확인용 단톡방')
    expect(message).not.toContain('LEGACY-NAME-delete')
  })

  it('연결 삭제 확인창은 실제 거래처코드를 유지한다', () => {
    const message = getChatRoomDeleteConfirmationMessage({
      partnerCode: '1117100334',
      partnerLinkStatus: 'LINKED',
      partnerBusinessName: '연결 거래처',
      chatRoomName: '연결 단톡방',
    })
    expect(message).toContain('거래처: 1117100334 (연결 거래처)')
  })
})
