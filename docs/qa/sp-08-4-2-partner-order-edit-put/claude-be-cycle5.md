## backend-engineer 사이클 5 리뷰 (head `86842c67`)

### 사이클 4 BE 결함 해소 표

| 항목 | 결함 | 해소 여부 | 근거 |
|---|---|---|---|
| BE-5 | `currentModifiedAt` null 시 NPE | **해소** | IT L306-312: `modifiedAt != null ? modifiedAt : createdAt` 삼항 fallback 확인. `testVerifyVersionAllowsFirstUpdateWhenModifiedAtIsNull` (L162) 가 `clearModifiedAt` → createdAt 일치 → 200, 불일치 → 409 두 경로 모두 커버 |
| C4-N2 | `orphanRemoval = false` Javadoc 미비 | **해소** | PartnerOrder.java L102 애너테이션 + `replaceLines` Javadoc (L192-201) 에 "soft-delete 전략을 지키기 위한 명시 설정" + `@SQLRestriction` 동작 설명 명기 확인 |

서비스 레이어 `verifyVersion` (L79-83) 도 동일 패턴 (`modifiedAt == null → createdAt fallback`) 으로 정합성 유지.

### 사이클 5 신규 발견

**Nit-1 (LOW)**: `replaceLines` 의 `deletedAt` null 체크가 `isDeleted` 플래그와 이중화

PartnerOrder.java L207: `if (line.getDeletedAt() == null)` 로 soft-delete 여부를 판단하나, BaseEntity 가 `isDeleted` 플래그를 별도 관리. `@SQLRestriction("is_deleted = false")` 로 조회되는 컬렉션은 이미 active 라인만 포함하므로 해당 조건은 방어 코드로서 유효하나, `line.isDeleted()` 와의 의미 정합 Javadoc 한 줄 보강 권장.

**Nit-2 (LOW)**: `testConcurrentUpdateRejectsStaleVersion` (L232) `@WithMockUser` 누락

MockMvc 를 사용하지 않는 순수 JPA 테스트이므로 기능상 영향 없으나, 다른 9개 케이스와 어노테이션 일관성 차이가 있음.

### 종합

**APPROVE** — 사이클 4 BE-5 / C4-N2 양쪽 결함 모두 정합하게 해소됨. 발견된 신규 항목 2건 모두 LOW Nit 수준으로 기능 회귀 없음.

**backend-engineer agent — 2026-05-17**
