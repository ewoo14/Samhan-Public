# Claude BE Review — SP-D4 cycle 1

head: `6d141002`

---

## P0 결함

### [P0-1] ArologisAdminController — 22 endpoint 중 2개만 guard 적용, 20개 누락

`ArologisAdminController.java` 는 `@PostMapping("/dispatches/parse-kakao")` 와 `@PostMapping("/dispatches")` 두 endpoint 에만 `arologisAdminPermissionGuard.checkEdit(...)` 호출이 존재하고, 나머지 endpoint 모두 guard 미적용 상태이다.

누락된 endpoint 목록 (19개):
- `POST /dispatches/manual` (manualCreate)
- `POST /dispatches/manual/preview` (manualPreview)
- `GET /dispatches` (list)
- `GET /dispatches/{id}` (findById)
- `POST /dispatches/{id}/auto-match` (autoMatch)
- `POST /dispatches/{id}/vehicles/{seq}/match-external` (matchExternal)
- `POST /dispatches/{id}/vehicles/{seq}/assign-driver` (assignDriver)
- `PUT /dispatches/{id}/vehicles/{seq}/stops/{stopSeq}/status` (updateStopStatus)
- `GET /drivers` (listDrivers)
- `PUT /dispatches/{id}/delete` (softDelete)
- `GET /dispatches/pre-classify` (preClassify)
- `GET /dispatches/unassigned` (unassigned)
- `GET /dispatches/regional` (regional)
- `GET /dispatches/{id}/audit-logs` (listAuditLogs)
- `GET /dispatches/{id}/realtime` (subscribeRealtime)
- `POST /dispatches/{id}/edit-requests` (createEditRequest)
- `GET /edit-requests/pending` (listPending)
- `POST /edit-requests/{requestId}/approve` (approveEditRequest)
- `POST /edit-requests/{requestId}/reject` (rejectEditRequest)

Plan §3 BE 항목과 Plan §7 위험 완화에 "arologis 22 endpoint annotation 누락 → controller class 레벨 `@RequiredArgsConstructor` + helper injection 1회, method 별 호출 검증 100% IT 커버" 라고 명시되어 있지만 실제 구현에서 대부분이 누락되었다. 이 상태로 머지되면 arologis.admin 페이지 동적 RBAC 가 사실상 비활성 상태가 된다.

**영향 범위**: 운영 배포 시 arologis 도메인 전체의 동적 권한 제어 불가 → 보안 통제 결함.

---

### [P0-2] ProductController — PATCH/PUT/DELETE write endpoint 3개 checkEdit 누락

`ProductController.java` 에서 Javadoc 주석에는 "POST/PATCH/DELETE write → checkEdit" 라고 명시되어 있지만, 실제로 `checkEdit` 호출이 있는 것은 `POST /products` (create) 1개 뿐이다.

누락된 write endpoint:
- `PATCH /products/{id}` (update) — roleHeader 파라미터 자체 없음
- `PATCH /products/{id}/price` (updatePrice) — roleHeader 파라미터 자체 없음
- `PUT /products/{id}/tags` (replaceTags) — roleHeader 파라미터 자체 없음
- `POST /products/{id}/discontinue` (discontinue) — roleHeader 파라미터 자체 없음
- `POST /products/{id}/reactivate` (reactivate) — roleHeader 파라미터 자체 없음
- `DELETE /products/{id}` (delete) — roleHeader 파라미터 자체 없음

`products.admin` PageCode 가 seed 되어 있어도 위 6개 endpoint 는 동적 RBAC 우회 가능 상태다.

**영향 범위**: 상품 관리 카테고리 편집(수정/단종/재활성화/삭제) 권한 통제 불가.

---

### [P0-3] WarehouseController — PATCH/DELETE/revert write endpoint 4개 checkEdit 누락

`WarehouseController.java` 에서 `POST /inventory/warehouses` (create) 에만 `checkEdit` 가 있고, 나머지 write endpoint 는 누락되었다.

누락된 write endpoint:
- `PATCH /inventory/warehouses/{id}` (update) — roleHeader 파라미터 없음, checkEdit 없음
- `DELETE /inventory/warehouses/{id}` (delete) — roleHeader 파라미터 없음, checkEdit 없음
- `POST /inventory/warehouses/{id}/audit/revert/{revisionNo}` (revertAudit) — roleHeader 파라미터 없음, checkEdit 없음
- `POST /inventory/warehouses/{id}/restore` (restore) — roleHeader 파라미터 없음, checkEdit 없음

Javadoc 주석에는 "POST/PATCH/DELETE write → checkEdit" 라고 명시되어 있으나 실제 구현과 불일치한다.

**영향 범위**: 창고 수정/삭제/복구/이력 롤백 동적 권한 우회 가능.

---

## P1 결함

### [P1-1] PartnerOrderConfirmController — @PreAuthorize 에 SALES role 누락 (매트릭스 불일치)

Plan §2 매트릭스에서 `sales.partner-order.confirm` 은 SALES: V/E (조회 + 편집 가능) 로 정의되어 있다. 그러나 `PartnerOrderConfirmController.java` 의 `@PreAuthorize("hasAnyRole('MASTER','MANAGER','PARTNER')")` 에 SALES 가 포함되지 않아 SALES 역할은 RoleGuard 단계에서 403이 발생한다.

SP-D3 이전 `@PreAuthorize` 보존 정책이지만, seed 매트릭스가 SALES:V/E 를 선언하고 있으므로 동적 RBAC 가 grant 해도 RoleGuard 가 차단하는 구조적 모순이다. SP-D5 RoleGuard 제거 전에는 사용자가 SALES 역할로 주문 확정 화면을 사용할 수 없다.

**제안**: 비범위 이연 또는 SP-D4 내 `@PreAuthorize` 에 `'SALES'` 추가 (Plan §2 매트릭스 준수).

---

### [P1-2] PartnerAdminController — `partners.block`, `partners.edit-request` PageCode guard 미적용

`PartnerAdminController.java` 는 `partners.list` 와 `partners.detail` 에 대한 guard 만 존재하고, `partners.block` / `partners.edit-request` 에 해당하는 controller 메서드가 본 PR 에 포함되지 않았다.

Plan §3 BE 항목에서 "PartnerAdminController" 를 수정 대상으로 명시하였으나 4개 PageCode 중 2개(list/detail) 만 guard 적용되었다. `PartnerBlockAdminController` / `PartnerEditRequestController` 신규 guard 연결이 누락되었다.

**영향 범위**: 거래처 차단 / 편집 결재 화면 동적 권한 미적용.

---

### [P1-3] arologis-service DynamicPermissionClientImpl — `@Qualifier("loadBalancedRestClientBuilder")` 미적용

`arologis-service/client/DynamicPermissionClientImpl.java` 는 SP-D3 패턴을 그대로 이식하여 기본 `RestClient.Builder` 를 사용하고 있다. 다른 4개 신규 서비스 (partner-order / inventory / user / partner / product) 모두 `@Qualifier("loadBalancedRestClientBuilder")` 를 사용하는 것과 다르다.

arologis-service 는 Spring Cloud LoadBalancer 를 사용하지 않아 `loadBalancedRestClientBuilder` 가 등록되지 않을 수 있으므로 이 자체가 런타임 오류를 유발하지는 않는다. 그러나 SP-D3 이후 서비스 간 일관성 위반이며, Eureka 통합 시 직접 URL 하드코딩(`http://auth-service`)이 LoadBalancer 를 통하지 않아 DNS 해석 실패 가능성이 있다.

**제안**: arologis-service 에도 `loadBalancedRestClientBuilder` 등록 또는 동일 패턴 정합 확인 필요.

---

### [P1-4] ArologisAdminController 일부 write endpoint — `@RequestHeader X-User-Role` 파라미터 자체 누락

`manualCreate`, `manualPreview`, `autoMatch`, `matchExternal`, `assignDriver`, `updateStopStatus`, `softDelete`, `listPending`, `approveEditRequest`, `rejectEditRequest` 등의 메서드에 `@RequestHeader(value = "X-User-Role", required = false) String roleHeader` 파라미터가 선언되지 않았다. P0-1 수정 시 roleHeader 파라미터 추가도 함께 필요하다.

---

## P2 결함 / 개선 제안

### [P2-1] arologis-service DynamicPermissionClientImpl — log 태그 `[SP-D3]` 잔류

`arologis-service/client/DynamicPermissionClientImpl.java` 내 모든 log 메시지에 `[SP-D3]` 태그가 남아 있다. SP-D4 이식 코드이므로 `[SP-D4]` 로 갱신 필요.

---

### [P2-2] PageCode.java 클래스 Javadoc — SP-D3 카운트 오기재

`PageCode.java` 의 클래스 Javadoc:
```
<p>SP-D2 회계 카테고리 7개 추가 ... — 총 19개.
<p>SP-D4 잔여 7 도메인 22개 추가 ... — 총 41개.
```
SP-D3 slice (dispatch.board + notification.dispatch-sms.send-audit) 2개 추가로 총 21개가 기존 정수이고, SP-D4 +22 로 총 43개여야 하는데 Javadoc 는 41개로 기재되어 있다. 경미한 문서 오류지만 코드 내 문서이므로 수정 권장.

---

### [P2-3] PartnerOrderHistoryController — Javadoc 에 `@PreAuthorize` 보존 명시 없음

다른 controller 는 SP-D4 가드 추가 관련 Javadoc 에 "기존 @PreAuthorize 보존 (regression 0)" 을 명시하는데, `PartnerOrderHistoryController.java` 는 해당 항목이 누락되어 있다. 경미하지만 일관성을 위해 추가 권장.

---

### [P2-4] WarehouseController `/search` endpoint — checkView 미적용 (의도적 비범위 여부 불명확)

`GET /inventory/warehouses/search` 는 `checkView` 없이 열려있다. 이 endpoint 가 동적 가드 범위에서 의도적으로 제외된 것인지 Plan §6 비범위 섹션에 언급이 없어 불명확하다. 의도적 제외라면 Javadoc 에 명시 필요.

---

## 종합

**FIX 요청**

P0 결함 3건이 머지 차단 수준이다:
- P0-1: ArologisAdminController 22 endpoint 중 20개 guard 완전 누락 — 아로로지스 동적 RBAC 사실상 비활성
- P0-2: ProductController write endpoint 6개 checkEdit 누락 — 상품 관리 권한 통제 불가
- P0-3: WarehouseController write endpoint 4개 checkEdit 누락 — 창고 수정/삭제 권한 통제 불가

P1 결함 2건(P1-1 SALES 역할 모순, P1-2 partner guard 2개 미적용)은 기능 정합성에 영향을 준다.

core 패턴(enum 정합성, V10 SQL 154 row, Guard 의미 로직, DynamicPermissionClientImpl 구조, IT 4-case 패턴)은 모두 올바르게 구현되어 있어 패턴 자체는 승인 가능 수준이나, 적용 범위 불완전으로 FIX 후 재검토가 필요하다.

---

## BE 일관성 점수

**3 / 5** — Guard 클래스 설계, V10 SQL, IT 4-case 패턴, DynamicPermissionClientImpl 구조는 SP-D3 패턴과 완전히 일관하나, ArologisAdminController 20 endpoint 누락 및 ProductController/WarehouseController write endpoint guard 부분 누락으로 적용 범위가 크게 미달.
