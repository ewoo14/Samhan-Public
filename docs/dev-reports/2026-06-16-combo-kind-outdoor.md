# 상업 combo 모듈 OUTDOOR + 구성품 sync 트랜잭션 근본 수정 — 개발 리포트 (PR #489)

> 2026-06-16 세션. 사양 후속 #3 후속(combo kind). 개발책임자 결정 OUTDOOR. 다모델(Opus 계획/리뷰 ↔ Codex 개발/교차).

## 1. 배경·결정
판넬 오라벨 수정(PR #488) 후 남은 #3 데이터 정리 항목 = 상업 combo 모듈 kind. 상업멀티 구성의 AM* 실외기 combo 모듈이 소스 시트 '구분' 미지정으로 `mapComponentKind` 폴백 `ACCESSORY` → 세트 구성품 화면 `[부속]`. 실데이터: ACCESSORY 구성품 중 43개가 자식 product_category=COMMERCIAL_MULTI(DVM S2 ...HP 실외기), 8개는 진짜 부속. **개발책임자 결정: OUTDOOR([실외기])**.

## 2. 근본 수정 — 구성품 sync self-invocation @Transactional 우회
구현 후 재sync 검증 중 **combo OUTDOOR 가 적용 안 됨**을 실증 → 원인 추적:
- `syncAll()`(@Transactional 아님)이 `this.syncComponentTab(cm)` self-invocation → Spring 프록시 우회로 `syncComponentTab` 의 `@Transactional` 미적용 → `findByIdForUpdate`(PESSIMISTIC_WRITE 락) 트랜잭션 에러(`TransactionRequiredException`) → **구성품 sync 전체 실패(HTTP 트리거)**.
- **IT 가 못 잡은 이유**: IT 클래스-@Transactional 라 ambient 트랜잭션 존재 → 버그 미발현(IT green ≠ 운영). HTTP(OSIV, 트랜잭션 없음)에서만 발현. 라이브 재sync 단독 적발. [[self-invocation-transactional-bypass]].
- **수정**: `@Lazy ProductSheetSyncService self` 자기주입 + `syncAll` 에서 `self.syncComponentTab(cm)` 프록시 경유 → @Transactional 적용. `syncTab` 미변경(pessimistic 락 미사용, blast radius 최소). 개발책임자 결정으로 근본 수정 채택(데이터 마이그레이션 band-aid 대신).

## 3. combo kind override
`syncComponentTab`: `kind == ACCESSORY`(폴백) && 자식 `product_category == COMMERCIAL_MULTI` → `OUTDOOR`. 명시 구분/비-COMMERCIAL_MULTI 자식 미영향. enum `==` null-safe.

## 4. 검증 (실 시트 재동기화 → 실 product_db → /components API)
- 구성품 sync 정상화: 트랜잭션 에러 0, 구성품 linked 1603(싱글 1447·상업 156)·bundlesMarked 343.
- blast radius 0: bundle_component active 1584/deleted 26 = baseline 동일, softDeleted 0(멱등 재링크, seenByParent 스코핑→대량삭제 구조적 불가).
- combo 43 OUTDOOR, 진짜 부속(SINGLE_PART) 8 ACCESSORY 유지.
- /components API: AM* combo 135행 전부 kind=OUTDOOR(ComponentRow.kind=BundleComponent.componentKind → estimate-app [실외기] 렌더).
- IT 추가, CI green(product-service IT PASS). QA `docs/qa/combo-kind-outdoor/`.

## 5. 듀얼리뷰 (Opus BE + Codex)
둘 다 결함 0. @Lazy self-reference 표준 패턴·순환 위험 0, 단일 트랜잭션 의도된 설계, override null-safe, soft-delete 스코프. (Codex 메모: IT 클래스-@Transactional 라 트랜잭션 버그 자체는 미재현 → combo 로직만 IT, 트랜잭션 수정은 운영 실증.)

## 6. 부수 효과
구성품 sync 트랜잭션 수정으로 **BundleComponent 링킹 전반이 HTTP 트리거에서 정상 작동**(기존 구조적 버그 해소 — combo 외에도 향후 세트 구성 변경이 sync 반영됨).
