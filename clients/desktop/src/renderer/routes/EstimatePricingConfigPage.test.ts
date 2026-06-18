import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, test } from 'vitest'

const root = process.cwd()
const read = (path: string) => readFileSync(join(root, path), 'utf8')

describe('EstimatePricingConfigPage contract', () => {
  test('판매 전역 견적 설정 route/menu/API/page-code 계약을 등록한다', () => {
    const route = read('src/renderer/routes/index.tsx')
    const subNav = read('src/renderer/components/sales/SalesSubNav.tsx')
    const layout = read('src/renderer/components/AppLayout.tsx')
    const api = read('src/renderer/api/sales.ts')
    const page = read('src/renderer/routes/EstimatePricingConfigPage.tsx')
    const mock = read('src/renderer/api/mock.ts')

    expect(route).toContain("path: '/sales/estimate-config'")
    expect(route).toContain('pageCode="sales.estimate-config"')
    expect(subNav).toContain("'/sales/estimate-config'")
    expect(subNav).toContain('견적 가격 설정')
    expect(layout).toContain("dynamicCanAccess('sales.estimate-config', 'view')")
    expect(api).toContain('getEstimateConfig')
    expect(api).toContain('updateEstimateConfig')
    expect(page).toContain("canAccess('sales.estimate-config', 'update')")
    expect(mock).toContain('/api/v1/estimate-config')
    expect(mock).toContain('cardFeeRate: 0.03')
  })
})
