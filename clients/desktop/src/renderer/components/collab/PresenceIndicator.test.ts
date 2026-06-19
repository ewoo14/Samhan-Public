import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, test } from 'vitest'
import { PresenceIndicator } from './PresenceIndicator'
import type { PresenceEntry } from '../../realtime/createPresenceClient'

describe('PresenceIndicator', () => {
  test('비배열 entries 를 빈 목록처럼 처리한다', () => {
    expect(() => renderToStaticMarkup(
      createElement(PresenceIndicator, { entries: { success: true } }),
    )).not.toThrow()
    expect(renderToStaticMarkup(
      createElement(PresenceIndicator, { entries: { success: true } }),
    )).toBe('')
  })

  test('빈 배열은 렌더하지 않는다', () => {
    expect(renderToStaticMarkup(
      createElement(PresenceIndicator, { entries: [] }),
    )).toBe('')
  })

  test('정상 배열은 displayName 기준으로 중복을 접어 렌더한다', () => {
    const entries: PresenceEntry[] = [
      { sessionId: 's1', displayName: '홍길동', color: 'BLUE' },
      { sessionId: 's2', displayName: '홍길동', color: 'BLUE' },
      { sessionId: 's3', displayName: '김관리', color: 'GREEN' },
    ]

    const html = renderToStaticMarkup(
      createElement(PresenceIndicator, { entries }),
    )

    expect(html).toContain('현재 보고 있음 2명')
    expect(html).toContain('홍길동 현재 보고 있음')
    expect(html).toContain('김관리 현재 보고 있음')
  })
})
