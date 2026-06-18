# F4 — 옵션 토글 자동매칭 (dev-report)

> 2026-06-19. 수식 빌더 시퀀스 F1.5✅→F3✅→**F4**→F5. PR #506. 개발책임자 "재개"=go.

## 요약
`BundleExpander`의 판넬/리모컨 옵션 매칭을 F1.5 `Product.panelType`/`remoteType` attribute 기반으로 전환 + 다중후보 isDefault 결정화 + **attribute null 시 기존 정규식 backstop**(parity). 견적 출력 영향 첫 슬라이스 — 단일후보=parity, 다중후보=isDefault(현 findFirst rownum 의존 개선), null/혼재=regex backstop.

## 변경 (BundleExpander, BE-only)
- **Part**: panelType/remoteType 필드. `expand()`서 구성품 Product attribute batch-fetch(`findByModelCodeInAndIsDeletedFalse`, N+1 제거; DB UNIQUE `ux_products_model_code_active` 로 동명 안전).
- **pickPanel**: 옵션→panelType(공청/블랙/승강) attribute-match → `pickPreferred`(isDefault 우선) → **attribute-miss 시 항상 기존 regex backstop**(noneMatch 게이트 제거 = 복합명칭/혼재/null parity) → 360(panelType '360'+variant+isDefault 3-tier) → basePanel.
- **resolveRemotes**: `matchOptionRemoteByType`(remoteType '유선'/'컬러유선', `!컬러` 배제) → regex fallback. isDefault 그룹 drop+add 교체 보존.
- 헬퍼: `pickPreferred`(isDefault→first, else first), `attributeOf`(blank→null).
- 무변경: estimate-app/desktop FE·가격 계산·견적 금액·expand HTTP 계약·옵션 전달. classifyRemoteType variant 보강=F5(전역 attribute 부작용 회피).

## 다모델 사이클
Opus 3-agent(P1 패널 fallback 게이트 parity 위반)→Codex fix(noneMatch 게이트 제거 backstop + 유선 컬러 배제 + IT)→Codex 교차(리모컨 self-match 쟁점)→Opus 수렴+적격 판정(**리모컨 self-match=정당 no-op·legacy 동치**, blocking=0)→synced self-match IT 박제.

## 검증 / parity
- **🚨 라이브 실QA 가 prod-breaking 결함 적발**: docs fix 가 적용된 V21 마이그 주석 수정 → 기존 DB Flyway checksum mismatch → product-service crash loop. CI fresh-DB 미검출. → V21 origin/main 복원([[feedback_applied_migration_immutable]]).
- **fallback parity 실증(deployed F4, dev null)**: `공청판넬`→PC6EUCK1NW "판넬(360CST/원형/공기청정)", `블랙판넬`→PC6NBNK1NW "판넬(360CST/원형/블랙/WIFI)". attribute null→regex backstop=legacy.
- **attribute-match**: BundleExpanderIT(Testcontainers Linux CI) seeded(panelType='블랙'/remoteType='유선') + 복합명칭/혼재/공청·승강/360/컬러/synced self-match. 로컬 Windows Docker skip→Linux CI green 게이트.
- product-service main+test 컴파일 OK. 소비자 무회귀(Part private·expand HTTP 디커플). CI green(state=CLEAN).

## 다음
F5 — estimate-app 설정 기반 계산 전환(golden parity) + classifyRemoteType variant 보강(componentVariant 반영).
