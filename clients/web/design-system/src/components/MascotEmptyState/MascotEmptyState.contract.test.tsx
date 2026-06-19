import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MascotEmptyState } from './MascotEmptyState'

const basicEmptyState = <MascotEmptyState title="조회 결과가 없습니다." />
const emptyStateWithAction = (
  <MascotEmptyState
    title="주문서가 없습니다."
    description="필터를 변경하거나 새 주문서를 등록해 주세요."
    action={<button type="button">새 주문서</button>}
  />
)

// @ts-expect-error title 은 필수다.
const invalidEmptyState = <MascotEmptyState />

describe('MascotEmptyState contract', () => {
  it('title, description, action 을 렌더링한다', () => {
    render(emptyStateWithAction)

    expect(screen.getByText('주문서가 없습니다.')).toBeTruthy()
    expect(screen.getByText('필터를 변경하거나 새 주문서를 등록해 주세요.')).toBeTruthy()
    expect(screen.getByRole('button', { name: '새 주문서' })).toBeTruthy()
  })

  it('mascot 이미지는 장식용으로 숨긴다', () => {
    const { container } = render(basicEmptyState)
    const image = container.querySelector('img')

    expect(image?.getAttribute('aria-hidden')).toBe('true')
    expect(image?.getAttribute('alt')).toBe('')
  })
})

export { basicEmptyState, emptyStateWithAction, invalidEmptyState }
