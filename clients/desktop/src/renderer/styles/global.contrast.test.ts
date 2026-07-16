import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

function luminance(hex: string): number {
  const rgb = hex.match(/[0-9a-f]{2}/gi)
  if (!rgb || rgb.length !== 3) throw new Error(`invalid color: ${hex}`)
  const channel = (raw: string) => {
    const value = Number.parseInt(raw, 16) / 255
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * channel(rgb[0]!) + 0.7152 * channel(rgb[1]!) + 0.0722 * channel(rgb[2]!)
}

function contrast(foreground: string, background: string): number {
  const lighter = Math.max(luminance(foreground), luminance(background))
  const darker = Math.min(luminance(foreground), luminance(background))
  return (lighter + 0.05) / (darker + 0.05)
}

describe('global.css 거래처 단가 변경행 다크모드 대비', () => {
  it('dark 본문색과 강조행 surface-selected 배경이 WCAG AA 4.5:1 이상이다', () => {
    const css = readFileSync(join(process.cwd(), 'src/renderer/styles/global.css'), 'utf8')
    const darkRule = css.match(/html\[data-theme=["']dark["']\],\s*body\[data-theme=["']dark["']\]\s*\{([^}]*)\}/s)?.[1]
    const selected = darkRule?.match(/--surface-selected\s*:\s*(#[0-9a-f]{6})/i)?.[1]

    expect(selected, 'global.css dark override에 --surface-selected가 필요함').toBeTruthy()
    expect(contrast('#F5F5F5', selected!)).toBeGreaterThanOrEqual(4.5)
  })
})
