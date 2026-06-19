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

  test('정상 배열은 displayName+color 기준으로 중복을 접어 렌더한다', () => {
    const entries: PresenceEntry[] = [
      { sessionId: 's1', displayName: '홍길동', color: 'BLUE' },
      { sessionId: 's2', displayName: '홍길동', color: 'BLUE' },
      { sessionId: 's3', displayName: '김관리', color: 'GREEN' },
      { sessionId: 's4', displayName: '홍길동', color: 'AMBER' },
    ]

    const html = renderToStaticMarkup(
      createElement(PresenceIndicator, { entries }),
    )

    expect(html).toContain('현재 보고 있음 3명')
    expect(html).toContain('홍길동 현재 보고 있음')
    expect(html).toContain('김관리 현재 보고 있음')
  })

  test('접힌 시청자 명단은 +N Badge title 과 aria-label 에 displayName 만 노출한다', () => {
    const entries: PresenceEntry[] = [
      { sessionId: 's1', displayName: '오병승', color: 'BLUE' },
      { sessionId: 's2', displayName: '김관리', color: 'GREEN' },
      { sessionId: 's3', displayName: '박출고', color: 'AMBER' },
      { sessionId: 's4', displayName: '이검수', color: 'ROSE' },
      { sessionId: 's5', displayName: '최물류', color: 'CYAN' },
    ]

    const html = renderToStaticMarkup(
      createElement(PresenceIndicator, { entries }),
    )

    expect(html).toContain('title="이검수, 최물류"')
    expect(html).toContain('aria-label="이검수, 최물류"')
    expect(html).toContain('+2')
  })

  test('이모지 표시명 initial 은 코드포인트 단위로 렌더한다', () => {
    const html = renderToStaticMarkup(
      createElement(PresenceIndicator, {
        entries: [{ sessionId: 's1', displayName: '😀사용자', color: 'BLUE' }],
      }),
    )

    expect(html).toContain('>😀</span>')
  })

  test('PresenceColor hex 는 BE enum 대비 보정된 AA 색상을 사용한다', () => {
    const entries: PresenceEntry[] = [
      { sessionId: 's1', displayName: '초록', color: 'GREEN' },
      { sessionId: 's2', displayName: '호박', color: 'AMBER' },
      { sessionId: 's3', displayName: '청록', color: 'CYAN' },
    ]

    const html = renderToStaticMarkup(
      createElement(PresenceIndicator, { entries }),
    )

    expect(html).toContain('background:#15803D')
    expect(html).toContain('background:#B45309')
    expect(html).toContain('background:#0E7490')

    const limeHtml = renderToStaticMarkup(
      createElement(PresenceIndicator, {
        entries: [{ sessionId: 's4', displayName: '라임', color: 'LIME' }],
      }),
    )
    expect(limeHtml).toContain('background:#4D7C0F')
  })
})
