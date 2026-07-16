import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

function channel(value: number): number {
  const normalized = value / 255
  return normalized <= 0.04045
    ? normalized / 12.92
    : ((normalized + 0.055) / 1.055) ** 2.4
}

function luminance(hex: string): number {
  const rgb = hex.match(/[0-9a-f]{2}/gi)
  if (!rgb || rgb.length !== 3) throw new Error(`invalid color: ${hex}`)
  const [r, g, b] = rgb.map((value) => channel(Number.parseInt(value, 16)))
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!
}

function contrast(foreground: string, background: string): number {
  const lighter = Math.max(luminance(foreground), luminance(background))
  const darker = Math.min(luminance(foreground), luminance(background))
  return (lighter + 0.05) / (darker + 0.05)
}

/**
 * R6-M6: tokens.css 를 파싱해 토큰→hex 를 동적 결선한다.
 *
 * hex 하드코딩 단언은 tokens.css 의 --ink-secondary 등이 바뀌어도 영구 green 이라
 * 토큰 회귀를 잡지 못했다. light(:root) 값 = 첫 선언 우선(dark override 는 파일 후반),
 * var(--alias) 는 재귀 해석(깊이 상한 4).
 */
function loadTokenResolver(): (name: string) => string {
  const css = readFileSync(join(process.cwd(), 'src/tokens/tokens.css'), 'utf8')
  const raw = new Map<string, string>()
  for (const declaration of css.matchAll(/--([\w-]+)\s*:\s*([^;]+);/g)) {
    const name = declaration[1]!
    if (!raw.has(name)) raw.set(name, declaration[2]!.trim())
  }
  const resolve = (name: string, depth = 0): string => {
    if (depth > 4) throw new Error(`token alias too deep: --${name}`)
    const value = raw.get(name)
    if (!value) throw new Error(`token missing in tokens.css: --${name}`)
    const alias = /^var\(\s*--([\w-]+)\s*\)$/.exec(value)
    if (alias) return resolve(alias[1]!, depth + 1)
    if (!/^#[0-9a-fA-F]{6}$/.test(value)) {
      throw new Error(`token --${name} is not a 6-digit hex: ${value}`)
    }
    return value
  }
  return resolve
}

describe('LineRow refreshed-price contrast', () => {
  const lineRowCss = readFileSync(
    join(process.cwd(), 'src/components/LineRow/LineRow.module.css'),
    'utf8',
  )
  const token = loadTokenResolver()

  it('raises inherited tertiary text and icons to ink-secondary on selected surface', () => {
    expect(lineRowCss).toMatch(
      /\.lineRow\.priceRefreshed\s*\{[^}]*--ink-tertiary:\s*var\(--ink-secondary\)/s,
    )
    expect(lineRowCss).toMatch(
      /\.lineRow\.priceRefreshed\s*\{[^}]*--line-hover:\s*var\(--ink-secondary\)/s,
    )
    expect(lineRowCss).toMatch(
      /\.lineRow\.priceRefreshed\s*\{[^}]*border-bottom-color:\s*var\(--line-focus\)/s,
    )
    expect(lineRowCss).toMatch(
      /\.lineRow\.priceRefreshed\s*\{[^}]*box-shadow:[^;]*var\(--action-brand\)/s,
    )
    // R6-M6: hex 리터럴 → 토큰 실값 결선 (쌍 자체는 기존 단언과 동일 — 약화 없음).
    expect(contrast(token('ink-secondary'), token('surface-selected'))).toBeGreaterThanOrEqual(4.5)
    expect(contrast(token('ink-secondary'), token('surface-selected-hover'))).toBeGreaterThanOrEqual(4.5)
    expect(contrast(token('line-focus'), token('surface-selected-hover'))).toBeGreaterThanOrEqual(3)
    expect(contrast(token('action-brand'), token('surface-selected-hover'))).toBeGreaterThanOrEqual(3)
  })

  it('keeps non-text markers (ring, left border, divider) at 3:1 on the selected surface', () => {
    // WCAG 1.4.11 — 강조행 inset 링/좌측 보더(--action-brand)와 구분선(--line-focus)은
    // 기본(--surface-selected)과 hover(--surface-selected-hover) 표면 모두에서 3:1 이상.
    expect(contrast(token('action-brand'), token('surface-selected'))).toBeGreaterThanOrEqual(3)
    expect(contrast(token('line-focus'), token('surface-selected'))).toBeGreaterThanOrEqual(3)
    // 행 본문 텍스트(--ink-primary)는 강조행 표면에서 AA 텍스트 기준.
    expect(contrast(token('ink-primary'), token('surface-selected'))).toBeGreaterThanOrEqual(4.5)
    expect(contrast(token('ink-primary'), token('surface-selected-hover'))).toBeGreaterThanOrEqual(4.5)
  })

  it('keeps the price marker chip readable on the surfaces it actually renders on', () => {
    // R6-M6: 실제 렌더 쌍 — 칩(.priceChangedStatus/.priceMemoryNote) 텍스트는
    // --action-brand on --action-brand-subtle (11px bold = small text → AA 4.5:1).
    // 두 칩 모두 background:--action-brand-subtle / color:--action-brand 라 이 쌍이 공용이다.
    expect(contrast(token('action-brand'), token('action-brand-subtle'))).toBeGreaterThanOrEqual(4.5)

    // [R8-DESIGN-4] 🔴 종전 단언 `action-brand on surface-card` 는 **렌더되지 않는 쌍**이었다:
    //  - 테두리를 가진 칩은 .priceChangedStatus 뿐인데(.priceMemoryNote 는 border 선언 자체가 없음),
    //  - .priceChangedStatus 는 line.priceRefreshChanged 일 때만 렌더되고 그 행은 항상
    //    .lineRow.priceRefreshed → background: --surface-selected (hover 시 --surface-selected-hover).
    //  즉 그 테두리가 --surface-card 위에 뜨는 경우는 없다. 실제 렌더 표면으로 교체한다.
    expect(contrast(token('action-brand'), token('surface-selected'))).toBeGreaterThanOrEqual(3)
    expect(contrast(token('action-brand'), token('surface-selected-hover'))).toBeGreaterThanOrEqual(3)

    // 기본 배경 쌍 — 기본 행 본문 텍스트 AA (.priceMemoryNote 가 뜨는 행은 --surface-card 유지).
    expect(contrast(token('ink-primary'), token('surface-card'))).toBeGreaterThanOrEqual(4.5)
  })

  it('R6-L5: dragging a refreshed row keeps both the inset ring and the popover elevation', () => {
    expect(lineRowCss).toMatch(
      /\.lineRow\.priceRefreshed\.dragging\s*\{[^}]*box-shadow:\s*inset 0 0 0 1px var\(--action-brand\),\s*var\(--elev-popover\)/s,
    )
  })
})
