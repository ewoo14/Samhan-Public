import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MascotLoader } from './MascotLoader'

const defaultLoader = <MascotLoader />
const labeledLoader = <MascotLoader label="거래처 목록 불러오는 중" size="lg" />

// @ts-expect-error MascotLoader size 는 sm/md/lg 만 허용한다.
const invalidLoader = <MascotLoader size="xl" />

describe('MascotLoader contract', () => {
  it('기본 label 과 status 역할을 렌더링한다', () => {
    render(defaultLoader)
    const status = screen.getByRole('status', { name: '불러오는 중' })

    expect(status).toBeTruthy()
    expect(screen.getByText('불러오는 중')).toBeTruthy()
  })

  it('size=lg 는 CSS 변수로 120px 을 지정한다', () => {
    render(labeledLoader)
    const status = screen.getByRole('status', { name: '거래처 목록 불러오는 중' }) as HTMLElement

    expect(status.style.getPropertyValue('--mascot-loader-size')).toBe('120px')
  })
})

export { defaultLoader, labeledLoader, invalidLoader }
