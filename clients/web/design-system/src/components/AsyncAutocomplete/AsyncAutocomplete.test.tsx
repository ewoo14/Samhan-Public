import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AsyncAutocomplete } from './AsyncAutocomplete'

interface Option {
  id: string
  label: string
}

describe('AsyncAutocomplete', () => {
  it('dropdown listbox 를 body portal 로 렌더해 overflow 컨테이너 클리핑을 피한다', async () => {
    const search = vi.fn<(q: string) => Promise<Option[]>>().mockResolvedValue([
      { id: 'p-1', label: '삼한테스트상사' },
    ])

    const view = render(
      <div style={{ height: 48, overflow: 'auto' }}>
        <AsyncAutocomplete<Option>
          value={null}
          onChange={vi.fn()}
          search={search}
          getKey={(item) => item.id}
          getInputLabel={(item) => item.label}
          renderOption={(item) => <span>{item.label}</span>}
          listboxLabel="거래처 목록"
          ariaLabel="거래처"
          debounceMs={0}
        />
      </div>,
    )

    fireEvent.focus(screen.getByRole('combobox', { name: '거래처' }))
    fireEvent.change(screen.getByRole('combobox', { name: '거래처' }), {
      target: { value: '삼한' },
    })

    await screen.findByRole('listbox', { name: '거래처 목록' })

    await waitFor(() => expect(search).toHaveBeenCalledWith('삼한'))
    expect(Array.from(document.body.children).some((child) => (
      child.getAttribute('role') === 'listbox'
      && child.getAttribute('aria-label') === '거래처 목록'
    ))).toBe(true)
    expect(view.container.querySelector('[role="listbox"]')).toBeNull()
  })

  it('open 상태에서 disabled 로 플립되면 선택 라벨을 복원하고 aria-expanded 를 내린다 (R8-QA-9)', () => {
    const selected: Option = { id: 'p-4', label: '한일냉동기술' }
    const search = vi.fn<(q: string) => Promise<Option[]>>().mockResolvedValue([])

    const view = render(
      <AsyncAutocomplete<Option>
        value={selected}
        onChange={vi.fn()}
        search={search}
        getKey={(item) => item.id}
        getInputLabel={(item) => item.label}
        renderOption={(item) => <span>{item.label}</span>}
        listboxLabel="거래처 목록"
        ariaLabel="거래처"
        disabled={false}
      />,
    )

    const input = screen.getByRole('combobox', { name: '거래처' }) as HTMLInputElement

    // 포커스 → open + draft='' → 표시값이 빈칸(전표 수정 진입 순간 재현)
    fireEvent.focus(input)
    expect(input.value).toBe('')
    expect(input.getAttribute('aria-expanded')).toBe('true')

    // disabled 로 플립(coedit provider 로딩) — React 는 disabled 요소에 onBlur 미발화
    view.rerender(
      <AsyncAutocomplete<Option>
        value={selected}
        onChange={vi.fn()}
        search={search}
        getKey={(item) => item.id}
        getInputLabel={(item) => item.label}
        renderOption={(item) => <span>{item.label}</span>}
        listboxLabel="거래처 목록"
        ariaLabel="거래처"
        disabled
      />,
    )

    // disabled 감지 effect 가 open 을 닫아 selectedLabel 이 복원돼야 한다.
    expect(input.value).toBe('한일냉동기술')
    expect(input.getAttribute('aria-expanded')).toBe('false')
  })
})
