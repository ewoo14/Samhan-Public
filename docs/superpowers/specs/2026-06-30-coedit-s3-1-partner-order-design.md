# 코-에디팅 S3-1 — 주문(partner-order) 메모 coedit (설계)

> 2026-06-30 야간(회사PC 정찰 → 집PC 구현). 협업 S3 문서별 롤아웃 1번. S3-0 공용화 토대(`shared/collab-core` `CollabCoeditService` + FE `makeCoeditApi`/`createCoeditProvider`) 위에 slip 패턴 1:1 이식. 정찰 ad695464c250d0ed4.

## Goal
주문(partner-order) 상세/편집 화면에 **단일 협업 메모 필드(실시간 동시편집)** 를 slip 패턴 1:1로 추가한다. 1차=메모 단일필드(저위험·additive). 폼 전체 셀 동시편집(`createDocCoeditProvider`)은 S3-1b 후속.

## 배경 — 정찰 결과
- S3-0으로 BE relay(`CollabCoeditService`)·FE provider(`createCoeditProvider`)·`CoeditApi` 팩토리·`CollaborativeTextField`(documentId/basePath/fieldName)가 도메인 무관 공용화됨.
- **partner-order는 토대 이미 충족**: `build.gradle` `:shared:collab-core`+`:shared:realtime-abstraction` 의존 보유, `PartnerOrderRealtimeBroker`(RealtimeBroker 빈) 존재 → `CollabCoreAutoConfiguration`의 `CollabCoeditService` 빈 **자동 주입 가능(신규 빈 불요)**. SSE `/collab/stream` + comment/edit/presence 엔드포인트 이미 동작.
- slip 메모 coedit = `SlipCollaborationPanel`의 `CollaborativeTextField`(fieldName="memo", editMode 무관 상시) + `SlipCollabController` coedit 3엔드포인트. 메모는 `createCoeditProvider` **default API**(`makeCoeditApi(basePath)`)를 타므로 `slipCollab.ts` coedit 함수는 미사용 잉여 래퍼.

## 비목표 (후속)
- 폼 전체 셀 동시편집(`createDocCoeditProvider`+셀 바인딩) = S3-1b.
- redline = revision 보유 문서 독립 하위트랙.
- 견적·회계·결재·배차 = S3-2~.

## 컴포넌트

### 1. BE — `PartnerOrderCollabController` coedit 3엔드포인트
파일: `services/partner-order-service/.../web/collab/PartnerOrderCollabController.java`(`@RequestMapping "/api/v1/partner-orders/{orderId}/collab"`, **풀패스 — slip의 StripPrefix와 다름**).
- 생성자에 `CollabCoeditService coeditService` 주입(+`import ...collab.coedit.CollabCoeditService`). 빈은 autoconfig 가용 → **신규 @Bean/@Configuration 불요**.
- coedit 3엔드포인트(slip L247-278 복제, **단 키는 `resolveOrderId(orderId)`→UUID** — 기존 `stream`의 `broker.subscribe(resolveOrderId)` 채널과 일치):
  - `GET /coedit` → `@RequirePermission(READ="sales.partner-order.list", VIEW)` → `ApiResponse.ok(new PartnerOrderCoeditUpdatesResponse(coeditService.listUpdates(resolveOrderId(orderId))))`
  - `POST /coedit/update` → `@RequirePermission(WRITE="sales.partner-order.edit", UPDATE)` → `coeditService.appendUpdate(resolveOrderId(orderId), req==null?null:req.update())`
  - `POST /coedit/awareness` → `@RequirePermission(READ, VIEW)` → `coeditService.publishAwareness(resolveOrderId(orderId), req.awareness())`
- **DTO 3종 신설**(`.../web/collab/dto/`, slip 1:1 로컬 미러 — cross-service import는 안티패턴): `PartnerOrderCoeditUpdateRequest(String update)`·`PartnerOrderCoeditAwarenessRequest(String awareness)`·`PartnerOrderCoeditUpdatesResponse(List<String> updates)`.
- **Flyway/엔티티/리포지토리 변경 0**(coedit=in-memory relay).

### 2. FE — `PartnerOrderCollaborationPanel` 메모 필드
파일: `clients/desktop/.../components/collab/PartnerOrderCollaborationPanel.tsx`.
- `CollaborativeTextField` import + 메모 블록 추가(slip `SlipCollaborationPanel` L283-290 복제, detail-grid 위·editMode 무관 상시):
  ```tsx
  const collabBasePath = useMemo(() => `/partner-orders/${encodeURIComponent(orderId)}`, [orderId])
  <CollaborativeTextField documentId={orderId} basePath={collabBasePath}
    fieldName="memo" label="협업 메모" rows={4} readOnly={!canWriteComments} />
  ```
  - `canWriteComments = canAccess('sales.partner-order.edit','update')`(기존 L164).
  - `makeCoeditApi`의 `normalizeCoeditBasePath`가 `/api/v1` prepend → `/api/v1/partner-orders/{orderId}/collab/coedit`; SSE=`.../collab/stream`(기존 일치).
- `partnerOrderCollab.ts` 신규 함수 **불요**(메모는 default API 경유, slip 동일).

## Data flow
편집 → Y.Text → `createCoeditProvider` default `makeCoeditApi('/partner-orders/{enc(orderId)}').postUpdate` → BE `coeditService.appendUpdate(resolveOrderId(orderId))` → `PartnerOrderRealtimeBroker` → SSE `/collab/stream` → 타 클라이언트. awareness 동일. **resolveOrderId로 주문번호 path형↔UUID 정규화**해 동일 주문 클라이언트가 같은 채널.

## Error handling / edge (정찰 C4 리스크)
- **orderId 형(최우선)**: route `id`=UUID 아닌 **주문번호 하이픈 path형** → basePath에 `encodeURIComponent(orderId)` 필수(게이트웨이 %2F 차단 회피, 기존 comment/presence가 이 경로로 검증됨). BE `resolveOrderId`(`PartnerOrderIdResolver`)가 UUID 변환.
- **게이트웨이 prefix 비대칭**: coedit를 기존 `PartnerOrderCollabController`(풀패스 `/api/v1/...`)에 추가 → 정합. slip 경로 복붙 시 `/api/v1` 누락/중복 금지.
- **coedit 메모 ≠ 도메인 요청사항**: coedit "협업 메모"(in-memory 비영속 실시간 스크래치)는 editMode 폼의 persisted `memo`("요청사항", commitEdit)와 **별개**. 라벨 "협업 메모"로 구분.
- 패널 2마운트(`isMobile` 삼항 상호배타) → 동시 2 provider 없음. editMode 무관 상시 마운트=상세 진입 시 viewer당 SSE 1개(slip 동일, 수용). `collabCurrentValues?` 가드 하위.
- awareness=VIEW → read-only 열람자도 커서 브로드캐스트(slip 의미 동일).

## Testing
- BE: `PartnerOrderCollabIT`(slip `SlipCollabIT` coedit 케이스 복제) — GET/POST `/coedit[/update|/awareness]` 실 HTTP + resolveOrderId 키 + VIEW/UPDATE 가드 + relay 누적/awareness 미저장. `CollabCoeditServiceTest`는 shared(S3-0)에서 커버.
- FE: `CollaborativeTextField.test.tsx` 참조; 패널 메모 렌더/readOnly.
- **라이브 QA**: 주문 편집 화면 2세션 메모 동시 타이핑(공유 relay 경유) 또는 partner-order standalone boot coedit round-trip(POST update→다른 GET 누적+SSE) 실증. 가짜 금지.

→ 본 슬라이스 후 **S3-1b(주문 폼 셀 동시편집)** 또는 **S3-2(견적)**.
