# Product Service First Slice QA 보고서

> **팀명**: Team-Product (BE/FE/DevOps/QA 4-team parallel)  |  **QA 담당**: QA-AGENT-PRODUCT-01  |  **작성일**: 2026-05-04
> **테스트 유형**: 🔵 내부 QA (initial slice — 4-worktree 통합 직후)
> **대상 서비스**: `services/product-service` (Plan §3.4 / §6.3 기준 first slice)

---

## 1. 테스트 환경

| 항목 | 내용 |
|------|------|
| 서버 환경 | Docker Compose (postgres:16-alpine, eureka, api-gateway, auth-service, user-service, product-service) |
| JDK | 17 (Temurin) |
| 빌드 | Gradle 8.10.2, `:services:product-service:test` |
| DB | PostgreSQL 16 (Testcontainers, JVM 1회 부팅) |
| HTTP 클라이언트 | VS Code REST Client / IntelliJ HTTP Client (`fixtures/products.http`) |
| 게이트웨이 | api-gateway 가 X-User-Id / X-User-Role 헤더 주입 (이미 구현됨) |
| 인증 | auth-service `/auth/login` 으로 JWT 발급 → 게이트웨이 통과 시 헤더로 변환 |
| 시드 | `V2__seed_product_categories.sql` (HVAC + 6 자식, UUID `0...001001` ~ `0...001007`) |

> 본 리포트의 "실제 결과" 칸은 PM 이 4-worktree 통합 직후 실제 IT 실행 + curl 시연으로 채웁니다 (`[PM 통합 후 채움]` 표시).

## 2. 테스트 시나리오 및 결과

| # | 시나리오 | 입력/조건 | 기대 결과 | 실제 결과 | 상태 | 스크린샷 |
|---|---------|----------|----------|----------|------|---------|
| 1 | 카테고리 트리 조회 (SALES) | `GET /categories` + `X-User-Role: SALES` | 200, HVAC 루트 1개 + 직속 자식 4개 (INDOOR/OUTDOOR/PIPING/CONTROL) + INDOOR 자식 2개 = 총 7 노드 | [PM 통합 후 채움] | ⬜ | [캡처1](#캡처1-카테고리-트리-조회) |
| 2 | 품목 등록 happy path (MANAGER) | `POST /products` body=`{name:"무풍 18평", modelName:"AR18T9170WCN", categoryId:"INDOOR_WALL UUID", sellingPrice:1500000, purchasePrice:1100000, currency:"KRW", tags:{"전압":"220V"}, description:"..."}` | 201, body 에 신규 UUID + 입력 필드 echo + status="ACTIVE" + 7 audit 필드 | [PM 통합 후 채움] | ⬜ | [캡처2](#캡처2-품목-등록-성공) |
| 3 | 모델명 중복 차단 (CONFLICT) | 시나리오 2 직후 동일 modelName 으로 재 POST | 409 CONFLICT, body `{code:"DUPLICATE_MODEL_NAME", message:"이미 등록된 모델명입니다"}` | [PM 통합 후 채움] | ⬜ | [캡처3](#캡처3-모델명-중복-차단) |
| 4 | 단종 후 신규 견적 노출 차단 | 시나리오 2 제품 → `PATCH /products/{id}/discontinue` → `GET /products?status=ACTIVE` | discontinue 200, 이후 ACTIVE 목록에서 해당 modelName 미포함 | [PM 통합 후 채움] | ⬜ | [캡처4](#캡처4-단종-후-active-필터) |
| 5 | soft-delete 후 동일 modelName 재등록 | 시나리오 2 제품 → `DELETE /products/{id}` → 동일 modelName 으로 새 POST | 첫 DELETE 204, 재등록 POST 201 (partial unique index `WHERE is_deleted = FALSE` 덕분) | [PM 통합 후 채움] | ⬜ | [캡처5](#캡처5-soft-delete-후-재등록) |
| 6 | 태그 contains 검색 | `GET /products?tagKey=전압&tagValue=220V` | 200, 220V 태그를 가진 제품만 반환 (jsonb GIN 인덱스 + `@>` 연산) | [PM 통합 후 채움] | ⬜ | [캡처6](#캡처6-태그-contains-검색) |
| 7 | 권한 검증 (SALES POST → 403) | `POST /products` + `X-User-Role: SALES` | 403 FORBIDDEN, body `{code:"FORBIDDEN", message:"등록 권한이 없습니다"}` | [PM 통합 후 채움] | ⬜ | [캡처7](#캡처7-sales-403) |
| 8 | 가격 음수 차단 (400) | `POST /products` body 의 sellingPrice = `-1000` | 400 BAD_REQUEST, validation error message "가격은 0 이상이어야 합니다" | [PM 통합 후 채움] | ⬜ | [캡처8](#캡처8-음수-가격-400) |
| 9 | 카테고리 자식 존재 시 삭제 차단 (409) | `DELETE /categories/{HVAC UUID}` (자식 4개 존재) | 409 CONFLICT, body `{code:"CATEGORY_HAS_CHILDREN", message:"하위 카테고리가 있어 삭제할 수 없습니다"}` (CategoryService 가 application-level 검증) | [PM 통합 후 채움] | ⬜ | [캡처9](#캡처9-카테고리-자식-존재-409) |
| 10 | lookup batch (3 ids → 3 summary, 미존재 ID 무시) | `POST /products/lookup` body=`{ids:[id1, id2, "00000000-0000-0000-0000-999999999999"]}` (마지막은 미존재) | 200, results 배열 길이 2 (id1, id2 의 ProductSummary), 미존재 ID 는 silently 누락 (404 아님) | [PM 통합 후 채움] | ⬜ | [캡처10](#캡처10-lookup-batch) |

## 3. 스크린샷

> 본 슬라이스는 4-worktree 분업 패턴: BE/FE/DevOps 가 별도 worktree 에서 동시 작업했고, 본 QA worktree 에는 BE 산출물이 없습니다. 따라서 IT 컴파일 검증 + 스크린샷 생성은 PM 통합 시점에 일괄 수행됩니다.
> 파일은 `docs/qa/product-service-first-slice/screenshots/` 에 PNG 로 저장. Edge headless 또는 직접 캡처.

### 캡처1: 카테고리 트리 조회
![캡처1](screenshots/01_category_tree.png)

### 캡처2: 품목 등록 성공
![캡처2](screenshots/02_product_create_success.png)

### 캡처3: 모델명 중복 차단
![캡처3](screenshots/03_duplicate_model_name_409.png)

### 캡처4: 단종 후 ACTIVE 필터
![캡처4](screenshots/04_discontinue_then_active_filter.png)

### 캡처5: soft-delete 후 재등록
![캡처5](screenshots/05_softdelete_reuse_modelname.png)

### 캡처6: 태그 contains 검색
![캡처6](screenshots/06_tags_contains_query.png)

### 캡처7: SALES 403
![캡처7](screenshots/07_sales_post_forbidden.png)

### 캡처8: 음수 가격 400
![캡처8](screenshots/08_negative_price_400.png)

### 캡처9: 카테고리 자식 존재 409
![캡처9](screenshots/09_category_has_children_409.png)

### 캡처10: lookup batch
![캡처10](screenshots/10_lookup_batch.png)

## 4. 버그 목록

| # | 심각도 | 제목 | 재현 단계 | 스크린샷 | 상태 |
|---|--------|------|----------|---------|------|
| - | - | [PM 통합 후 채움] | - | - | - |

## 5. 종합 판정

| 항목 | 결과 |
|------|------|
| 전체 시나리오 | 10건 |
| 통과 | [PM 통합 후 채움] |
| 실패 | [PM 통합 후 채움] |
| **최종 판정** | [PM 통합 후 채움 — ✅ PASS / ❌ FAIL] |

## 6. PM 통합 시 검증 권고 순서

1. **BE worktree 머지** → product-service main 코드 + Flyway V1/V2 + build.gradle 가 갖춰진다.
2. **본 QA worktree 머지** → IT java 4종 + qa_report.md + fixtures/products.http.
3. **컴파일 확인**: `gradle :services:product-service:compileTestJava` (worktree 내부 한글 path 시 assemble 만 수행 — 메모리 §Korean Path JDK Trap 참고).
4. **IT 실행**: Docker 가용 환경에서 `gradle :services:product-service:test`. Repository IT 6개 + Controller IT 5개 = 총 11개 테스트.
5. **수동 시나리오 (스크린샷)**: docker-compose 풀스택 부팅 후 `fixtures/products.http` 의 10개 요청 순차 실행. Edge headless 또는 IntelliJ HTTP Client UI 로 캡처.
6. **본 리포트 갱신**: "실제 결과" / "스크린샷" / "버그 목록" / "종합 판정" 칸을 채우고 commit.

---

| createdAt | createdBy | modifiedAt | modifiedBy |
|-----------|-----------|------------|------------|
| 2026-05-04 | QA-AGENT-PRODUCT-01 | [PM 통합 후 갱신] | [PM] |
