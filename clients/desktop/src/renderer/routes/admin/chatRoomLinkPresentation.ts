import type { ChatRoomMapping } from '../../api/chatRoomApi'

export type ChatRoomLinkPresentationStatus =
  | 'LINKED'
  | 'UNLINKED'
  | 'UNLINKED_AMBIGUOUS'
  | 'UNLINKED_UNMATCHED'

export function getChatRoomLinkStatus(
  row: Pick<ChatRoomMapping, 'partnerCode' | 'partnerLinkStatus'>,
): ChatRoomLinkPresentationStatus {
  if (row.partnerLinkStatus) return row.partnerLinkStatus
  return row.partnerCode.startsWith('LEGACY-NAME-') ? 'UNLINKED' : 'LINKED'
}

export function getChatRoomLinkLabel(status: ChatRoomLinkPresentationStatus): string {
  return status === 'LINKED' ? '연결' : '미연결'
}

export function getChatRoomLinkReason(
  status: ChatRoomLinkPresentationStatus,
): string | null {
  if (status === 'UNLINKED_AMBIGUOUS') {
    return '모호 · 후보 여러 건'
  }
  if (status === 'UNLINKED_UNMATCHED') {
    return '미매칭 · 후보 없음'
  }
  if (status === 'UNLINKED') return '아직 거래처 미연결'
  return null
}

export function getChatRoomPartnerCodeLabel(
  row: Pick<ChatRoomMapping, 'partnerCode' | 'partnerLinkStatus'>,
): string {
  return getChatRoomLinkStatus(row) === 'LINKED' ? row.partnerCode : '미연결'
}
