/**
 * Mock 모드 (dev-only) — 백엔드 미부팅 환경에서 11 화면 시연 + 자동 캡처용.
 *
 * 활성화 조건: 빌드 시 환경변수 `VITE_MOCK_MODE=1` 설정.
 * 프로덕션 빌드에는 본 모듈이 import 되지만 인터셉터가 no-op 으로 통과한다 (환경변수 미설정).
 *
 * 본 모듈은 PR #18 의 QA 스크린샷 자동 캡처를 위해 추가된 dev-only 도구이며,
 * 실제 운영 시점에는 사용되지 않는다.
 *
 * 본 슬라이스 (slip-output-format) 갱신:
 * - `GET /slips/lookup-product?modelName=...` mock 추가 (onBlur lookup)
 * - `GET /slips/{id}` mock 추가 (상세 라인 포함)
 * - `POST /slips/{id}/{action}` 라이프사이클 transition mock (status 진행)
 * - `GET /inventory/transfers` + `POST` + `GET /{id}` + transition mock
 */
import type { AxiosRequestConfig } from 'axios'

/** ApiResponse envelope 형태 — `shared/common/dto/ApiResponse.java` 와 동일. */
function envelope<T>(data: T) {
  return {
    success: true,
    code: 'OK',
    message: null as string | null,
    data,
    timestamp: new Date().toISOString(),
  }
}

/** 본 환경이 mock 모드인지 — Vite import.meta.env 기반 컴파일 타임 결정. */
export function isMockMode(): boolean {
  return import.meta.env['VITE_MOCK_MODE'] === '1'
}

/** Mock token snapshot — AuthGuard 자동 인증 우회 + 헤더 chip 표시용. */
export const MOCK_AUTH = {
  token: 'mock-jwt-token',
  userId: '00000000-0000-0000-0000-000000010001',
  role: 'MANAGER',
  fullName: '오병승',
}

/** 시드 4 창고 (V2 시드와 동일) */
const MOCK_WAREHOUSES = [
  {
    id: '11111111-1111-1111-1111-000000000001',
    code: 'HQ-001',
    name: '본사창고',
    type: 'HEADQUARTERS',
    address: '서울시 강남구 본사',
    displayOrder: 1,
    description: '본사 보유 메인 창고',
  },
  {
    id: '11111111-1111-1111-1111-000000000002',
    code: 'VH-001',
    name: '1호차 차량재고',
    type: 'VEHICLE',
    address: null,
    displayOrder: 2,
    description: '출장 차량 이동 재고 (창고원/기사 단위)',
  },
  {
    id: '11111111-1111-1111-1111-000000000003',
    code: 'CS-001',
    name: '거래처 위탁창고',
    type: 'CONSIGNMENT',
    address: null,
    displayOrder: 3,
    description: '거래처에 위탁한 재고 (소유권은 자사)',
  },
  {
    id: '11111111-1111-1111-1111-000000000004',
    code: 'VR-001',
    name: '가상창고',
    type: 'VIRTUAL',
    address: null,
    displayOrder: 4,
    description: '삼성 직배/반품/서비스 인보이스 등 비물리',
  },
]

/** noUncheckedIndexedAccess 회피용 — 4 시드 명시 참조 */
const HQ_ID = MOCK_WAREHOUSES[0]!.id
const VH_ID = MOCK_WAREHOUSES[1]!.id

/**
 * 시연용 mock 전표 7건.
 * Slice A 신규 필드: `dispatcher` / `inspector` / `ownerDepartment` / `ownerFullName`
 * / `shippingAddress` / `contactPhone` 모두 포함 (Designer README.md § 2.3).
 *
 * Slice A 신규: INSPECTING status mock 2건 (slip-006, slip-007).
 */
const MOCK_SLIPS = [
  {
    id: 'slip-001',
    slipNo: '2026/05/04-1',
    slipType: 'OUTBOUND',
    slipDate: '2026-05-04',
    seqNo: 1,
    status: 'PROCESSING',
    partnerId: 'p001',
    partnerName: '주식회사 윌리-정현수',
    sourceWarehouseId: HQ_ID,
    destinationWarehouseId: null,
    deliveryTag: 'DAY',
    memo: '9시까지배송요망',
    ownerDepartment: '영업1팀',
    ownerFullName: '오병승',
    shippingAddress: '서울특별시 강남구 테헤란로 152',
    contactPhone: '010-1234-5678',
    driverName: '홍지수',
    driverPhone: '010-1234-5678',
    dispatcher: {
      userId: '00000000-0000-0000-0000-000000020001',
      fullName: '홍지수',
      signedAt: '2026-05-04T14:32:18+09:00',
    },
    inspector: null,
  },
  {
    id: 'slip-002',
    slipNo: '2026/05/04-2',
    slipType: 'OUTBOUND',
    slipDate: '2026-05-04',
    seqNo: 2,
    status: 'CONFIRMED',
    partnerId: 'p002',
    partnerName: '○○종합건설',
    sourceWarehouseId: HQ_ID,
    destinationWarehouseId: null,
    deliveryTag: 'STACK',
    memo: '[야적] 05/04 상차 05/05 하차',
    ownerDepartment: '영업1팀',
    ownerFullName: '오병승',
    shippingAddress: '경기도 성남시 분당구 판교로 235',
    contactPhone: '031-987-6543',
    dispatcher: {
      userId: '00000000-0000-0000-0000-000000020001',
      fullName: '홍지수',
      signedAt: '2026-05-04T10:12:00+09:00',
    },
    inspector: {
      userId: '00000000-0000-0000-0000-000000020002',
      fullName: '김기철',
      signedAt: '2026-05-04T11:45:30+09:00',
    },
  },
  {
    id: 'slip-003',
    slipNo: '2026/05/03-7',
    slipType: 'INBOUND',
    slipDate: '2026-05-03',
    seqNo: 7,
    status: 'COMPLETED',
    partnerId: 'p003',
    partnerName: '삼성전자',
    sourceWarehouseId: null,
    destinationWarehouseId: HQ_ID,
    deliveryTag: 'RETURN_TRIP',
    memo: '회차 입고',
    ownerDepartment: '구매팀',
    ownerFullName: '이정훈',
    shippingAddress: null,
    contactPhone: null,
    dispatcher: null,
    inspector: null,
  },
  {
    id: 'slip-004',
    slipNo: '2026/05/03-3',
    slipType: 'OUTBOUND',
    slipDate: '2026-05-03',
    seqNo: 3,
    status: 'ACCEPTED',
    partnerId: 'p001',
    partnerName: '주식회사 윌리-정현수',
    sourceWarehouseId: VH_ID,
    destinationWarehouseId: null,
    deliveryTag: 'DAY',
    memo: '',
    ownerDepartment: '영업1팀',
    ownerFullName: '오병승',
    shippingAddress: '서울특별시 송파구 올림픽로 300',
    contactPhone: '010-9876-5432',
    dispatcher: {
      userId: '00000000-0000-0000-0000-000000020001',
      fullName: '홍지수',
      signedAt: '2026-05-03T09:15:00+09:00',
    },
    inspector: null,
  },
  {
    id: 'slip-005',
    slipNo: '2026/05/02-12',
    slipType: 'OUTBOUND',
    slipDate: '2026-05-02',
    seqNo: 12,
    status: 'DRAFT',
    partnerId: 'p004',
    partnerName: '한일냉동기술',
    sourceWarehouseId: HQ_ID,
    destinationWarehouseId: null,
    deliveryTag: 'GYEONGDONG_FREIGHT',
    memo: '경동화물',
    ownerDepartment: '영업2팀',
    ownerFullName: '박서연',
    shippingAddress: null,
    contactPhone: null,
    dispatcher: null,
    inspector: null,
  },
  // Slice A 신규: INSPECTING status mock (검수 단계 시연용)
  {
    id: 'slip-006',
    slipNo: '2026/05/04-3',
    slipType: 'OUTBOUND',
    slipDate: '2026-05-04',
    seqNo: 3,
    status: 'INSPECTING',
    partnerId: 'p001',
    partnerName: '주식회사 윌리-정현수',
    sourceWarehouseId: HQ_ID,
    destinationWarehouseId: null,
    deliveryTag: 'DAY',
    memo: '검수 진행 중',
    ownerDepartment: '영업1팀',
    ownerFullName: '오병승',
    shippingAddress: '서울특별시 마포구 양화로 45',
    contactPhone: '010-2222-3333',
    dispatcher: {
      userId: '00000000-0000-0000-0000-000000020001',
      fullName: '홍지수',
      signedAt: '2026-05-04T13:00:00+09:00',
    },
    inspector: {
      userId: '00000000-0000-0000-0000-000000020002',
      fullName: '김기철',
      signedAt: '2026-05-04T16:45:02+09:00',
    },
  },
  {
    id: 'slip-007',
    slipNo: '2026/05/04-4',
    slipType: 'OUTBOUND',
    slipDate: '2026-05-04',
    seqNo: 4,
    status: 'INSPECTING',
    partnerId: 'p002',
    partnerName: '○○종합건설',
    sourceWarehouseId: HQ_ID,
    destinationWarehouseId: null,
    deliveryTag: 'STACK',
    memo: '검수 대기 → 시작',
    ownerDepartment: '영업2팀',
    ownerFullName: '박서연',
    shippingAddress: '인천광역시 연수구 송도과학로 32',
    contactPhone: '032-555-7777',
    dispatcher: {
      userId: '00000000-0000-0000-0000-000000020001',
      fullName: '홍지수',
      signedAt: '2026-05-04T14:00:00+09:00',
    },
    inspector: {
      userId: '00000000-0000-0000-0000-000000020002',
      fullName: '김기철',
      signedAt: '2026-05-04T17:20:00+09:00',
    },
  },
]

/** 시연용 mock 이동전표 5건 */
const MOCK_TRANSFERS = [
  {
    id: 'tr-001',
    transferNo: 'T-2026/05/04-1',
    sourceWarehouseId: HQ_ID,
    sourceWarehouseCode: 'HQ-001',
    destinationWarehouseId: VH_ID,
    destinationWarehouseCode: 'VH-001',
    reason: 'REBALANCE',
    reasonDetail: '5월 1주차 차량 재배치',
    status: 'APPROVED',
    requesterId: '00000000-0000-0000-0000-000000010001',
    approverId: '00000000-0000-0000-0000-000000010002',
    requestedAt: '2026-05-04T09:10:00',
    approvedAt: '2026-05-04T09:30:00',
    shippedAt: null,
    receivedAt: null,
    confirmedAt: null,
  },
  {
    id: 'tr-002',
    transferNo: 'T-2026/05/03-2',
    sourceWarehouseId: VH_ID,
    sourceWarehouseCode: 'VH-001',
    destinationWarehouseId: HQ_ID,
    destinationWarehouseCode: 'HQ-001',
    reason: 'CONSOLIDATE',
    reasonDetail: '차량 잔여재고 본사 회수',
    status: 'CONFIRMED',
    requesterId: '00000000-0000-0000-0000-000000010001',
    approverId: '00000000-0000-0000-0000-000000010002',
    requestedAt: '2026-05-03T08:00:00',
    approvedAt: '2026-05-03T08:15:00',
    shippedAt: '2026-05-03T10:00:00',
    receivedAt: '2026-05-03T15:30:00',
    confirmedAt: '2026-05-03T16:00:00',
  },
  {
    id: 'tr-003',
    transferNo: 'T-2026/05/04-3',
    sourceWarehouseId: HQ_ID,
    sourceWarehouseCode: 'HQ-001',
    destinationWarehouseId: VH_ID,
    destinationWarehouseCode: 'VH-001',
    reason: 'URGENT',
    reasonDetail: '긴급 출장 보충',
    status: 'REQUESTED',
    requesterId: '00000000-0000-0000-0000-000000010001',
    approverId: null,
    requestedAt: '2026-05-04T11:00:00',
    approvedAt: null,
    shippedAt: null,
    receivedAt: null,
    confirmedAt: null,
  },
  {
    id: 'tr-004',
    transferNo: 'T-2026/05/04-4',
    sourceWarehouseId: HQ_ID,
    sourceWarehouseCode: 'HQ-001',
    destinationWarehouseId: VH_ID,
    destinationWarehouseCode: 'VH-001',
    reason: 'MAINTENANCE',
    reasonDetail: '점검 후 회수',
    status: 'SHIPPED',
    requesterId: '00000000-0000-0000-0000-000000010001',
    approverId: '00000000-0000-0000-0000-000000010002',
    requestedAt: '2026-05-04T07:00:00',
    approvedAt: '2026-05-04T07:30:00',
    shippedAt: '2026-05-04T09:45:00',
    receivedAt: null,
    confirmedAt: null,
  },
  {
    id: 'tr-005',
    transferNo: 'T-2026/05/02-7',
    sourceWarehouseId: HQ_ID,
    sourceWarehouseCode: 'HQ-001',
    destinationWarehouseId: VH_ID,
    destinationWarehouseCode: 'VH-001',
    reason: 'OTHER',
    reasonDetail: null,
    status: 'CANCELED',
    requesterId: '00000000-0000-0000-0000-000000010001',
    approverId: null,
    requestedAt: '2026-05-02T14:00:00',
    approvedAt: null,
    shippedAt: null,
    receivedAt: null,
    confirmedAt: null,
  },
]

/** 모델명 lookup 시연용 — 5개 mock product (대소문자 구분 없음). */
const MOCK_PRODUCTS_BY_MODEL: Record<
  string,
  { productId: string; modelName: string; productName: string; sellingPrice: string }
> = {
  AJ040RXH4BC1: {
    productId: 'p-aj040',
    modelName: 'AJ040RXH4BC1',
    productName: '시스템에어컨 4Way 4HP',
    sellingPrice: '1850000',
  },
  AJ052RXH5BC1: {
    productId: 'p-aj052',
    modelName: 'AJ052RXH5BC1',
    productName: '시스템에어컨 4Way 5HP',
    sellingPrice: '2120000',
  },
  AJ036NCH3CH: {
    productId: 'p-aj036',
    modelName: 'AJ036NCH3CH',
    productName: '천장형 1Way 3HP',
    sellingPrice: '1450000',
  },
  AJ100NCDKH: {
    productId: 'p-aj100',
    modelName: 'AJ100NCDKH',
    productName: '실외기 10HP',
    sellingPrice: '4200000',
  },
  MWR_WE10N: {
    productId: 'p-mwr10',
    modelName: 'MWR-WE10N',
    productName: '유선 리모컨 (WE10N)',
    sellingPrice: '85000',
  },
}

/**
 * 라인 시연용 — 상세 화면 라인 표시.
 * Slice A: `specification` 필드 추가 (피드백 #4 / Designer components.md § 3).
 */
const SAMPLE_LINES = [
  {
    id: 'line-001',
    productId: 'p-aj040',
    productName: '시스템에어컨 4Way 4HP',
    modelName: 'AJ040RXH4BC1',
    specification: '4HP', // Slice A
    quantity: 2,
    unitPrice: '1850000',
    lineTotal: '3700000',
    note: null,
  },
  {
    id: 'line-002',
    productId: 'p-mwr10',
    productName: '유선 리모컨 (WE10N)',
    modelName: 'MWR-WE10N',
    specification: '220V', // Slice A
    quantity: 2,
    unitPrice: '85000',
    lineTotal: '170000',
    note: null,
  },
  {
    id: 'line-003',
    productId: 'p-pc1nw',
    productName: 'WIFI 판넬',
    modelName: 'PC1NWSK3NW',
    specification: null, // Slice A — 빈 값 허용 ('-' 표시)
    quantity: 1,
    unitPrice: '120000',
    lineTotal: '120000',
    note: null,
  },
]

const SAMPLE_TRANSFER_LINES = [
  {
    id: 'tline-001',
    productId: 'p-aj040',
    requestedQuantity: 5,
    shippedQuantity: 0,
    receivedQuantity: 0,
  },
  {
    id: 'tline-002',
    productId: 'p-mwr10',
    requestedQuantity: 5,
    shippedQuantity: 0,
    receivedQuantity: 0,
  },
]

/**
 * URL + method 매칭으로 mock 응답을 반환. 매칭 실패 시 null.
 */
export function getMockResponse(config: AxiosRequestConfig): unknown | null {
  const method = (config.method ?? 'get').toUpperCase()
  const url = config.url ?? ''

  // POST /auth/login → 토큰 응답
  if (method === 'POST' && url.endsWith('/auth/login')) {
    return envelope({
      token: MOCK_AUTH.token,
      userId: MOCK_AUTH.userId,
      role: MOCK_AUTH.role,
      fullName: MOCK_AUTH.fullName,
    })
  }

  // GET /inventory/warehouses
  if (method === 'GET' && url.endsWith('/inventory/warehouses')) {
    return envelope(MOCK_WAREHOUSES)
  }

  // POST /inventory/warehouses → 신규 창고 1건
  if (method === 'POST' && url.endsWith('/inventory/warehouses')) {
    const body = (config.data ? JSON.parse(config.data as string) : {}) as Record<string, unknown>
    return envelope({
      id: 'new-' + Date.now(),
      code: body['code'],
      name: body['name'],
      type: body['type'],
      address: body['address'],
      displayOrder: body['displayOrder'] ?? 0,
      description: body['description'],
    })
  }

  // GET /slips/lookup-product?modelName=...
  if (method === 'GET' && url.includes('/slips/lookup-product')) {
    const modelName = (config.params?.['modelName'] ?? '') as string
    const found = MOCK_PRODUCTS_BY_MODEL[modelName.toUpperCase()]
      ?? MOCK_PRODUCTS_BY_MODEL[modelName]
    if (found) {
      return envelope(found)
    }
    // 미존재는 mock 환경에서도 404 시뮬레이션 — null 반환 시 axios 가 실제 호출 시도
    // 하지만 백엔드 미부팅이라 실패. 간단히 envelope 안에 not-found 표시 대신
    // 호출자에서 에러 처리하도록 빈 객체 + status 200 으로 진행 (mock 한계).
    // → 화면 동작 확인용으로는 sample 1건 항상 반환:
    return envelope({
      productId: 'p-fallback',
      modelName,
      productName: `(샘플) ${modelName}`,
      sellingPrice: '1000000',
    })
  }

  // GET /slips/{id} (단건 상세) — UUID-like 또는 'slip-001' 패턴
  const slipDetailMatch = url.match(/\/slips\/([^/?]+)$/)
  if (method === 'GET' && slipDetailMatch && !url.includes('lookup-product')) {
    const id = slipDetailMatch[1]!
    const found = MOCK_SLIPS.find((s) => s.id === id) ?? MOCK_SLIPS[0]!
    return envelope({
      ...found,
      lines: SAMPLE_LINES,
    })
  }

  // GET /slips (페이지) — lookup-product / {id} 가 아닌 경우
  if (
    method === 'GET'
    && url.includes('/slips')
    && !url.includes('/slips/lookup-product')
    && !slipDetailMatch
  ) {
    return envelope({
      content: MOCK_SLIPS,
      totalElements: MOCK_SLIPS.length,
      totalPages: 1,
      number: 0,
      size: 20,
      first: true,
      last: true,
    })
  }

  // POST /slips → 신규 전표 1건 (라인 포함)
  if (method === 'POST' && url.endsWith('/slips')) {
    return envelope({
      id: 'new-slip-' + Date.now(),
      slipNo: '2026/05/04-99',
      slipType: 'OUTBOUND',
      slipDate: '2026-05-04',
      seqNo: 99,
      status: 'DRAFT',
      partnerId: null,
      partnerName: '신규 거래처',
      sourceWarehouseId: HQ_ID,
      destinationWarehouseId: null,
      deliveryTag: 'DAY',
      memo: null,
      lines: SAMPLE_LINES,
    })
  }

  // POST /slips/{id}/{action} — 라이프사이클 transition (Slice A: inspect 신규)
  const slipTransitionMatch = url.match(
    /\/slips\/([^/]+)\/(save|send|accept|process|inspect|complete|ship|deliver|confirm|reject|cancel)$/,
  )
  if (method === 'POST' && slipTransitionMatch) {
    const id = slipTransitionMatch[1]!
    const action = slipTransitionMatch[2]!
    const found = MOCK_SLIPS.find((s) => s.id === id) ?? MOCK_SLIPS[0]!
    const nextStatus: Record<string, string> = {
      save: 'SAVED',
      send: 'SENT',
      accept: 'ACCEPTED',
      process: 'PROCESSING',
      inspect: 'INSPECTING', // Slice A 신규
      complete: 'COMPLETED',
      ship: 'SHIPPING',
      deliver: 'DELIVERED',
      confirm: 'CONFIRMED',
      reject: 'REJECTED',
      cancel: 'CANCELED',
    }
    // accept 트랜지션 시 dispatcher 자동 채움 (Designer ux-flow.md § 2.1)
    const dispatcher
      = action === 'accept'
        ? {
          userId: '00000000-0000-0000-0000-000000020001',
          fullName: '홍지수',
          signedAt: new Date().toISOString(),
        }
        : found.dispatcher
    // inspect 트랜지션 시 inspector 자동 채움 (Designer ux-flow.md § 2.2)
    const inspector
      = action === 'inspect'
        ? {
          userId: '00000000-0000-0000-0000-000000020002',
          fullName: '김기철',
          signedAt: new Date().toISOString(),
        }
        : found.inspector
    return envelope({
      ...found,
      status: nextStatus[action] ?? found.status,
      dispatcher,
      inspector,
      lines: SAMPLE_LINES,
    })
  }

  // ==========================================================================
  // notification-slice-B: 배송 묶음 (delivery-batch) mock
  // LinkDispatchListPage 시연용 — 4 배치 (sent 2 / unsent 2)
  // ==========================================================================
  const MOCK_BATCHES = [
    {
      id: 'batch-001',
      deliveryDate: '2026-05-04',
      driverName: '홍지수',
      driverPhone: '010-1234-5678',
      slipCount: 3,
      signUrl: 'https://sign.samhan-air.com/b/abcd1234',
      smsSentAt: '2026-05-04T08:30:15+09:00',
    },
    {
      id: 'batch-002',
      deliveryDate: '2026-05-04',
      driverName: '김기철',
      driverPhone: '010-9876-5432',
      slipCount: 2,
      signUrl: 'https://sign.samhan-air.com/b/efgh5678',
      smsSentAt: null,
    },
    {
      id: 'batch-003',
      deliveryDate: '2026-05-04',
      driverName: '박서연',
      driverPhone: '010-2222-3333',
      slipCount: 5,
      signUrl: 'https://sign.samhan-air.com/b/ijkl9012',
      smsSentAt: '2026-05-04T09:15:42+09:00',
    },
    {
      id: 'batch-004',
      deliveryDate: '2026-05-04',
      driverName: '이정훈',
      driverPhone: '010-5555-7777',
      slipCount: 1,
      signUrl: 'https://sign.samhan-air.com/b/mnop3456',
      smsSentAt: null,
    },
  ]

  // GET /delivery-batches/{id}
  const batchDetailMatch = url.match(/\/delivery-batches\/([^/?]+)$/)
  if (method === 'GET' && batchDetailMatch && !url.includes('auto-group')) {
    const id = batchDetailMatch[1]!
    const found = MOCK_BATCHES.find((b) => b.id === id) ?? MOCK_BATCHES[0]!
    return envelope({
      ...found,
      tokenIssuedAt: '2026-05-04T07:00:00+09:00',
      tokenExpiresAt: null,
      slips: [
        {
          slipId: 'slip-001',
          slipNo: '2026/05/04-1',
          partnerName: '주식회사 윌리-정현수',
          shippingAddress: '서울특별시 강남구 테헤란로 152',
          lineCount: 3,
        },
        {
          slipId: 'slip-006',
          slipNo: '2026/05/04-3',
          partnerName: '주식회사 윌리-정현수',
          shippingAddress: '서울특별시 마포구 양화로 45',
          lineCount: 2,
        },
      ],
    })
  }

  // POST /delivery-batches/auto-group?date=...
  if (method === 'POST' && url.includes('/delivery-batches/auto-group')) {
    return envelope([
      {
        id: 'batch-new-' + Date.now(),
        deliveryDate: (config.params?.['date'] ?? '2026-05-04') as string,
        driverName: '신규 자동그룹',
        driverPhone: '010-0000-0000',
        slipCount: 2,
        signUrl: 'https://sign.samhan-air.com/b/newauto',
        smsSentAt: null,
      },
    ])
  }

  // POST /delivery-batches/{id}/sms — SMS 발송
  const batchSmsMatch = url.match(/\/delivery-batches\/([^/]+)\/sms$/)
  if (method === 'POST' && batchSmsMatch) {
    const id = batchSmsMatch[1]!
    const found = MOCK_BATCHES.find((b) => b.id === id) ?? MOCK_BATCHES[0]!
    return envelope({
      ...found,
      smsSentAt: new Date().toISOString(),
    })
  }

  // POST /delivery-batches/{id}/regenerate-token — 토큰 재발행
  const batchRegenMatch = url.match(/\/delivery-batches\/([^/]+)\/regenerate-token$/)
  if (method === 'POST' && batchRegenMatch) {
    const id = batchRegenMatch[1]!
    const found = MOCK_BATCHES.find((b) => b.id === id) ?? MOCK_BATCHES[0]!
    return envelope({
      ...found,
      signUrl: `https://sign.samhan-air.com/b/regen-${Date.now().toString(36)}`,
      tokenIssuedAt: new Date().toISOString(),
      tokenExpiresAt: null,
      slips: [],
    })
  }

  // POST /delivery-batches/{id}/slips — 슬립 추가
  const batchAddSlipMatch = url.match(/\/delivery-batches\/([^/]+)\/slips$/)
  if (method === 'POST' && batchAddSlipMatch) {
    const id = batchAddSlipMatch[1]!
    const found = MOCK_BATCHES.find((b) => b.id === id) ?? MOCK_BATCHES[0]!
    return envelope({
      ...found,
      tokenIssuedAt: '2026-05-04T07:00:00+09:00',
      tokenExpiresAt: null,
      slips: [],
    })
  }

  // GET /delivery-batches (목록)
  if (method === 'GET' && url.includes('/delivery-batches')) {
    return envelope(MOCK_BATCHES)
  }

  // PATCH /slips/{id}/driver — 기사 정보 부분 갱신
  const driverPatchMatch = url.match(/\/slips\/([^/]+)\/driver$/)
  if (method === 'PATCH' && driverPatchMatch) {
    const id = driverPatchMatch[1]!
    const body = (config.data ? JSON.parse(config.data as string) : {}) as {
      driverName?: string | null
      driverPhone?: string | null
    }
    const found = MOCK_SLIPS.find((s) => s.id === id) ?? MOCK_SLIPS[0]!
    return envelope({
      ...found,
      driverName: body.driverName ?? null,
      driverPhone: body.driverPhone ?? null,
      lines: SAMPLE_LINES,
    })
  }

  // POST /inventory/balances/batch — 다건 재고 조회 (sales-form-polish 슬라이스)
  if (method === 'POST' && url.endsWith('/inventory/balances/batch')) {
    const body = (config.data ? JSON.parse(config.data as string) : {}) as {
      productIds?: string[]
    }
    const ids = body.productIds ?? []

    /**
     * 시연용 mock — 모든 product 에 대해 본사/차량/위탁/가상 4 창고 mock 수량.
     * 실제 BE 는 stock_balance 테이블에서 PIVOT 하여 응답.
     */
    const mockPerProduct: Record<string, Record<string, number | null>> = {
      'p-aj040': { 'HQ-001': 12, 'VH-001': 3, 'CS-001': 0, 'VR-001': null },
      'p-aj052': { 'HQ-001': 5, 'VH-001': 2, 'CS-001': 0, 'VR-001': null },
      'p-aj036': { 'HQ-001': 8, 'VH-001': 0, 'CS-001': 1, 'VR-001': null },
      'p-aj100': { 'HQ-001': 2, 'VH-001': 0, 'CS-001': 0, 'VR-001': null },
      'p-mwr10': { 'HQ-001': 45, 'VH-001': 10, 'CS-001': 2, 'VR-001': null },
    }

    const productNameById: Record<string, { modelName: string; productName: string }> = {
      'p-aj040': { modelName: 'AJ040RXH4BC1', productName: '시스템에어컨 4Way 4HP' },
      'p-aj052': { modelName: 'AJ052RXH5BC1', productName: '시스템에어컨 4Way 5HP' },
      'p-aj036': { modelName: 'AJ036NCH3CH', productName: '천장형 1Way 3HP' },
      'p-aj100': { modelName: 'AJ100NCDKH', productName: '실외기 10HP' },
      'p-mwr10': { modelName: 'MWR-WE10N', productName: '유선 리모컨 (WE10N)' },
    }

    const rows = ids.map((pid) => {
      const meta = productNameById[pid] ?? {
        modelName: '(샘플)' + pid,
        productName: '(샘플 품목)',
      }
      const per = mockPerProduct[pid] ?? {
        'HQ-001': 0,
        'VH-001': 0,
        'CS-001': 0,
        'VR-001': null,
      }
      const total = Object.entries(per).reduce(
        (sum, [code, qty]) =>
          sum + (qty ?? 0) * (code === 'VR-001' ? 0 : 1),
        0,
      )
      return {
        productId: pid,
        modelName: meta.modelName,
        productName: meta.productName,
        perWarehouse: per,
        total,
      }
    })

    return envelope({ rows })
  }

  // GET /inventory/transfers/{id}
  const transferDetailMatch = url.match(/\/inventory\/transfers\/([^/?]+)$/)
  if (method === 'GET' && transferDetailMatch) {
    const id = transferDetailMatch[1]!
    const found = MOCK_TRANSFERS.find((t) => t.id === id) ?? MOCK_TRANSFERS[0]!
    return envelope({
      ...found,
      lines: SAMPLE_TRANSFER_LINES,
    })
  }

  // GET /inventory/transfers (페이지)
  if (method === 'GET' && url.includes('/inventory/transfers')) {
    return envelope({
      content: MOCK_TRANSFERS,
      totalElements: MOCK_TRANSFERS.length,
      totalPages: 1,
      number: 0,
      size: 20,
      first: true,
      last: true,
    })
  }

  // POST /inventory/transfers → 신규 이동전표 1건
  if (method === 'POST' && url.endsWith('/inventory/transfers')) {
    return envelope({
      id: 'new-tr-' + Date.now(),
      transferNo: 'T-2026/05/04-99',
      sourceWarehouseId: HQ_ID,
      sourceWarehouseCode: 'HQ-001',
      destinationWarehouseId: VH_ID,
      destinationWarehouseCode: 'VH-001',
      reason: 'REBALANCE',
      reasonDetail: null,
      status: 'REQUESTED',
      requesterId: MOCK_AUTH.userId,
      approverId: null,
      requestedAt: new Date().toISOString(),
      approvedAt: null,
      shippedAt: null,
      receivedAt: null,
      confirmedAt: null,
      lines: SAMPLE_TRANSFER_LINES,
    })
  }

  // POST /inventory/transfers/{id}/{action} — 라이프사이클 transition
  const trTransitionMatch = url.match(
    /\/inventory\/transfers\/([^/]+)\/(approve|reject|ship|receive|confirm|cancel)$/,
  )
  if (method === 'POST' && trTransitionMatch) {
    const id = trTransitionMatch[1]!
    const action = trTransitionMatch[2]!
    const found = MOCK_TRANSFERS.find((t) => t.id === id) ?? MOCK_TRANSFERS[0]!
    const nextStatus: Record<string, string> = {
      approve: 'APPROVED',
      reject: 'REJECTED',
      ship: 'SHIPPED',
      receive: 'RECEIVED',
      confirm: 'CONFIRMED',
      cancel: 'CANCELED',
    }
    return envelope({
      ...found,
      status: nextStatus[action] ?? found.status,
      lines: SAMPLE_TRANSFER_LINES,
    })
  }

  return null
}
