import { describe, expect, it, vi } from 'vitest'
import {
  createDocCoeditProvider,
  type DocCoeditProvider,
} from '../realtime/createCoeditProvider'
import {
  bundleComponentLineIds,
  coeditHeaderValues,
  coeditLinesToEditLines,
  partnerRepriceBannerText,
  partnerRepriceMarkerText,
} from './SlipDetailPage'
import { toServerLineIdSet } from '../realtime/coeditLineIds'
import type { SlipDetail } from '../api/slip'

/**
 * #809 R8-FE-2 — lineId 왕복 계약의 FE 가드.
 *
 * <p>이 PR 의 간판 계약(상세 응답 `id` → 수정 payload `lineId` 왕복)은 도입 커밋
 * `34f978ec9` 의 FE diff 가 18줄·테스트 0 이었다. 그 결과 R8-FE-1(=R8-QA-2 · BLOCKING)
 * 이 desktop vitest 749건을 그대로 통과했고, 라이브 2창 coedit 에서만 잡혔다.
 *
 * <p>여기서 잠그는 것은 <b>"lineId 는 위치가 아니라 라인 자신을 따라간다"</b> 는 불변식이다.
 */

const SERVER_LINE_1 = '11111111-1111-1111-1111-111111111111'
const SERVER_LINE_2 = '22222222-2222-2222-2222-222222222222'
const SERVER_LINE_3 = '33333333-3333-3333-3333-333333333333'

const PRODUCT_1 = 'aaaaaaaa-0000-0000-0000-000000000001'
const PRODUCT_2 = 'aaaaaaaa-0000-0000-0000-000000000002'
const PRODUCT_3 = 'aaaaaaaa-0000-0000-0000-000000000003'

/** 서버 상세 3라인 — 1행이 세트 head, 2·3행이 그 구성품인 전형적 세트 전표. */
const serverLines = [
  { id: SERVER_LINE_1, productId: PRODUCT_1 },
  { id: SERVER_LINE_2, productId: PRODUCT_2 },
  { id: SERVER_LINE_3, productId: PRODUCT_3 },
]

const knownServerLineIds = toServerLineIdSet(serverLines)

async function makeProvider(): Promise<DocCoeditProvider> {
  return createDocCoeditProvider({
    documentId: 'slip-1',
    basePath: '/slips/slip-1',
    initialUpdates: async () => ({ updates: [] }),
    postUpdate: vi.fn(),
    postAwareness: vi.fn(),
    subscribe: () => ({ abort: vi.fn() }) as unknown as AbortController,
  })
}

/** SlipDetailPage.toPurchaseEditLines 와 동일한 seed 형태 (서버 line.id → Y.Doc lineId). */
function seedRows(provider: DocCoeditProvider, rows: typeof serverLines) {
  provider.replaceItems(
    rows.map((line, index) => ({
      lineId: line.id,
      productId: line.productId,
      productName: `품목${index + 1}`,
      modelName: `MODEL-${index + 1}`,
      specification: '',
      quantity: 1,
      unitPrice: String((index + 1) * 1000),
      note: '',
    })),
  )
}

/** 폼 state 라인 (PurchaseEditLine) — seed 직후의 로컬 스냅샷. */
function editLinesFrom(rows: typeof serverLines) {
  return rows.map((line, index) => ({
    key: `key-${index + 1}`,
    lineId: line.id,
    productId: line.productId,
    productName: `품목${index + 1}`,
    modelName: `MODEL-${index + 1}`,
    specification: '',
    quantity: 1,
    unitPrice: String((index + 1) * 1000),
    note: '',
  }))
}

describe('SlipDetailPage — lineId 왕복 계약 (R8-FE-2)', () => {
  it('원격 피어가 1행을 삭제해도 잔여 행이 자기 lineId 를 유지한다 (R8-FE-1 = R8-QA-2 BLOCKING)', async () => {
    const provider = await makeProvider()
    seedRows(provider, serverLines)
    const current = editLinesFrom(serverLines)

    // 원격 피어(A창)가 1행(세트 head)을 삭제 — Y.Doc 은 즉시 2행으로 당겨진다.
    // 로컬(B창) `current` 는 이 시점에 아직 3행짜리 구 스냅샷이다. 이것이 라이브 재현 조건이다.
    provider.removeItem(SERVER_LINE_1)

    const next = coeditLinesToEditLines(provider, current, knownServerLineIds)

    expect(next).toHaveLength(2)
    // 위치복원이면 next[0].lineId 가 삭제된 1행의 SERVER_LINE_1 이 된다 =
    // 단품이 세트 head 계보를 상속하고 사용자 단가가 증발하는 경로.
    expect(next[0]!.lineId).toBe(SERVER_LINE_2)
    expect(next[1]!.lineId).toBe(SERVER_LINE_3)
    // lineId 와 productId 가 같은 행을 가리켜야 한다 — 라이브에서 깨진 바로 그 짝이다.
    expect(next[0]!.productId).toBe(PRODUCT_2)
    expect(next[1]!.productId).toBe(PRODUCT_3)

    provider.destroy()
  })

  it('hydrate → update 왕복에서 lineId 가 보존된다', async () => {
    const provider = await makeProvider()
    seedRows(provider, serverLines)

    const next = coeditLinesToEditLines(provider, editLinesFrom(serverLines), knownServerLineIds)

    expect(next.map((line) => line.lineId)).toEqual([
      SERVER_LINE_1,
      SERVER_LINE_2,
      SERVER_LINE_3,
    ])
    provider.destroy()
  })

  it('Y.Doc 이 모르는 lineId(클라 랜덤 UUID)는 null 로 강등한다 — 전 라인 400 방지 (R8-FE-9)', async () => {
    const provider = await makeProvider()
    // seed 가 lineId 를 싣지 않으면 replaceItems 가 클라 랜덤 UUID 를 채운다 —
    // 이것이 견적의 기존 Y.Doc 상태이며, 직독값을 그대로 보내면 서버 소유검증에서 전 라인 400.
    provider.replaceItems(
      serverLines.map((line, index) => ({
        productId: line.productId,
        productName: `품목${index + 1}`,
        modelName: `MODEL-${index + 1}`,
        specification: '',
        quantity: 1,
        unitPrice: '1000',
        note: '',
      })),
    )

    const next = coeditLinesToEditLines(provider, editLinesFrom(serverLines), knownServerLineIds)

    expect(next.map((line) => line.lineId)).toEqual([null, null, null])
    provider.destroy()
  })

  it('신규 라인(lineId 미보유)은 null 로 전송한다', async () => {
    const provider = await makeProvider()
    seedRows(provider, serverLines.slice(0, 1))
    // coedit 중 추가된 신규 행 — addItem 이 클라 랜덤 lineId 를 부여한다.
    provider.addItem({ productId: PRODUCT_2, productName: '신규품목', quantity: 1, unitPrice: '500' })

    const next = coeditLinesToEditLines(
      provider,
      editLinesFrom(serverLines.slice(0, 1)),
      knownServerLineIds,
    )

    expect(next).toHaveLength(2)
    expect(next[0]!.lineId).toBe(SERVER_LINE_1)
    expect(next[1]!.lineId).toBeNull()
    provider.destroy()
  })

  it('coedit 헤더가 partnerId 를 싣는다 — 상대 피어가 구 거래처로 저장하지 않도록 (D-R8-7)', () => {
    const slip = {
      partnerId: '44f0cfc1-4a5f-4206-85cd-04ad5fa70922',
      partnerName: '한울냉열시스템',
      partnerCode: '000011111111',
      businessNumber: '000011111111',
      memo: null,
      deliveryAddress: null,
      supervisionAddress: null,
      projectName: null,
      recipientPhone: null,
      paymentDueDate: null,
    } as unknown as SlipDetail

    // partnerId 가 CRDT 헤더에 없으면 거래처 재선택이 상대 피어에 전파되지 않아, 상대는
    // 화면에 새 거래처를 보면서 구 partnerId 로 저장한다 → 가격기억이 원 거래처에 각인(R8-QA-3).
    expect(coeditHeaderValues(slip, 'OUTBOUND')['partnerId']).toBe('44f0cfc1-4a5f-4206-85cd-04ad5fa70922')
    expect(coeditHeaderValues(slip, 'OUTBOUND')['partnerName']).toBe('한울냉열시스템')
    // 거래처 미보유 전표도 빈 문자열로 키를 실어야 한다 — 키 부재 시 상대 피어의
    // getHeaderValue('partnerId') 가 ''를 돌려주는 것과 구분되지 않는다.
    expect(coeditHeaderValues({ ...slip, partnerId: null } as unknown as SlipDetail, 'INBOUND')['partnerId']).toBe('')
  })

  it('품목을 교체해도 그 행의 lineId 는 자기 것을 유지한다 (BE productId 게이트가 계보를 끊는다)', async () => {
    const provider = await makeProvider()
    seedRows(provider, serverLines)
    const current = editLinesFrom(serverLines)

    // 2행의 품목을 무관한 단품으로 교체.
    provider.setItemValueById(SERVER_LINE_2, 'productId', PRODUCT_3)

    const next = coeditLinesToEditLines(provider, current, knownServerLineIds)

    // lineId 는 위치·품목과 무관하게 자기 행을 따라간다. 계보 승계 거부는 BE
    // BundleLineageResolver 의 productId 동일성 게이트(D-R8-8) 책임이다.
    expect(next[1]!.lineId).toBe(SERVER_LINE_2)
    expect(next[1]!.productId).toBe(PRODUCT_3)
    provider.destroy()
  })
})

describe('bundleComponentLineIds — 거래처 변경 재조회의 세트 구성품 제외 (R8 재fix 회귀 교정)', () => {
  it('parentSetModel 비공백 라인만 제외한다 — setHead 무관(head 도 구성품, BE isBundleComponent 미러)', () => {
    const ids = bundleComponentLineIds([
      // 평면(단품) 라인 — 재가격 대상 유지.
      { id: SERVER_LINE_1, parentSetModel: null },
      // 세트 head — 첫 구성품(setHead=true 지만 parentSetModel 비공백) → 제외.
      // 라이브 실증: 재조회가 닿으면 배분가 88,000 → 80,000(−9.09%) 변형(전표 2026/07/16-94).
      { id: SERVER_LINE_2, parentSetModel: 'SET-HM2WAY' },
      // 세트 tail 구성품 → 제외.
      { id: SERVER_LINE_3, parentSetModel: 'SET-HM2WAY' },
    ])
    expect(ids.has(SERVER_LINE_1)).toBe(false)
    expect(ids.has(SERVER_LINE_2)).toBe(true)
    expect(ids.has(SERVER_LINE_3)).toBe(true)
  })

  it('공백 parentSetModel·undefined·id 없는 라인은 제외 집합에 넣지 않는다 (BE isBlank 미러)', () => {
    const ids = bundleComponentLineIds([
      { id: SERVER_LINE_1, parentSetModel: '  ' }, // 공백 = 계보 아님(BE isBlank)
      { id: SERVER_LINE_2 },                        // 필드 부재 = 평면
      { id: null, parentSetModel: 'SET-X' },        // id 없는 라인은 집합화 불가
    ])
    expect(ids.size).toBe(0)
  })
})

describe('SlipDetailPage — 거래처 재조회 출처 마커와 배너', () => {
  it('hit/miss/미확보를 서로 다른 사용자 문구로 표현한다', () => {
    expect(partnerRepriceMarkerText({ source: 'REMEMBERED', updatedAt: '2026-07-16T10:00:00' }))
      .toEqual({ label: '거래처 최근단가', description: '이 거래처에 마지막으로 저장된 단가 · 2026-07-16 저장' })
    expect(partnerRepriceMarkerText({ source: 'CATALOG', updatedAt: null }))
      .toEqual({ label: '판매가', description: '이 거래처에 저장된 최근단가가 없어 판매가를 적용했습니다' })
    expect(partnerRepriceMarkerText({ source: 'UNAVAILABLE', updatedAt: null }))
      .toEqual({ label: '단가 확인 필요', description: '카탈로그 판매가를 확인할 수 없어 단가를 비웠습니다. 직접 입력해 주세요' })
  })

  it('배너가 miss를 최근단가 재적용으로 오인하지 않고 출처별 건수를 알린다', () => {
    expect(partnerRepriceBannerText([
      { source: 'REMEMBERED' },
      { source: 'CATALOG' },
      { source: 'CATALOG' },
      { source: 'UNAVAILABLE' },
    ], 3)).toBe('거래처 변경 단가 확인 완료 · 최근단가 1건 · 판매가 2건 · 단가 확인 필요 1건 · 변경 3행')
  })
})
