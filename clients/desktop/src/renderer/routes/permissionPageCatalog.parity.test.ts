import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { PAGE_GROUPS, PAGES_ORDER } from './permissionPageCatalog'

const PAGE_CODE_ENUM_PATH = resolve(
  process.cwd(),
  '../../services/auth-service/src/main/java/com/samhanair/logis/auth/domain/PageCode.java',
)

function readPageCodeEnumSource(): string {
  if (!existsSync(PAGE_CODE_ENUM_PATH)) {
    throw new Error(
      `BE PageCode enum 파일을 읽을 수 없습니다: ${PAGE_CODE_ENUM_PATH}. ` +
        'desktop vitest는 clients/desktop cwd에서 실행되어야 합니다.',
    )
  }

  return readFileSync(PAGE_CODE_ENUM_PATH, 'utf8')
}

function extractBackendPageCodes(source: string): Set<string> {
  const enumConstantCodePattern = /^\s*[A-Z0-9_]+\s*\(\s*"([a-z0-9.-]+)"\s*,/gm
  return new Set(Array.from(source.matchAll(enumConstantCodePattern), (match) => match[1]))
}

function extractFrontendPageCodes(): Set<string> {
  const groupedPages = PAGE_GROUPS.flatMap((group) => group.pages)
  return new Set([...groupedPages, ...PAGES_ORDER])
}

describe('permission page catalog parity', () => {
  it('keeps every desktop permission page-code registered in BE PageCode enum', () => {
    const backendPageCodes = extractBackendPageCodes(readPageCodeEnumSource())
    const frontendPageCodes = extractFrontendPageCodes()

    expect(
      backendPageCodes.size,
      `PageCode.java에서 page-code를 0건 추출했습니다. enum 상수 생성자 포맷 변경 여부를 확인하세요: ${PAGE_CODE_ENUM_PATH}`,
    ).toBeGreaterThan(0)
    expect(frontendPageCodes.size, 'FE PAGE_GROUPS/PAGES_ORDER page-code가 비어 있습니다.').toBeGreaterThan(0)

    const frontendOnlyOrphans = Array.from(frontendPageCodes)
      .filter((pageCode) => !backendPageCodes.has(pageCode))
      .sort()

    expect(
      frontendOnlyOrphans,
      `FE 권한 카탈로그가 BE PageCode enum에 없는 page-code를 참조합니다: ${frontendOnlyOrphans.join(', ')}`,
    ).toEqual([])
  })
})
