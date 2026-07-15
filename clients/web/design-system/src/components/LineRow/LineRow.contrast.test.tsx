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

describe('LineRow refreshed-price contrast', () => {
  it('raises inherited tertiary text and icons to ink-secondary on selected surface', () => {
    const css = readFileSync(
      join(process.cwd(), 'src/components/LineRow/LineRow.module.css'),
      'utf8',
    )

    expect(css).toMatch(
      /\.lineRow\.priceRefreshed\s*\{[^}]*--ink-tertiary:\s*var\(--ink-secondary\)/s,
    )
    expect(css).toMatch(
      /\.lineRow\.priceRefreshed\s*\{[^}]*--line-hover:\s*var\(--ink-secondary\)/s,
    )
    expect(css).toMatch(
      /\.lineRow\.priceRefreshed\s*\{[^}]*border-bottom-color:\s*var\(--line-focus\)/s,
    )
    expect(css).toMatch(
      /\.lineRow\.priceRefreshed\s*\{[^}]*box-shadow:[^;]*var\(--action-brand\)/s,
    )
    expect(contrast('#5C6773', '#EFF6FF')).toBeGreaterThanOrEqual(4.5)
    expect(contrast('#5C6773', '#E0EAFB')).toBeGreaterThanOrEqual(4.5)
    expect(contrast('#3B82F6', '#E0EAFB')).toBeGreaterThanOrEqual(3)
    expect(contrast('#1E40AF', '#E0EAFB')).toBeGreaterThanOrEqual(3)
  })
})
