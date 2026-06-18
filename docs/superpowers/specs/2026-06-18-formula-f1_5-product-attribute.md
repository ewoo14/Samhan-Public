# F1.5 — 품목 attribute 분류 (panelType/remoteType) — F4 자동매칭 토대

> 2026-06-18 개발책임자 결정 시퀀스(F1.5→F3→F4→F5). F4 옵션 자동매칭(B 경량 휴리스틱·isDefault 우선)의 정보 토대. [[project_formula_builder_epic]]

## 0. 목표
품목(Product)에 **panelType/remoteType 속성**을 GAS 품명 정규식으로 1회 분류·적재 → F4 가 옵션 토글 시 setModel 그룹 내 attribute 매칭으로 자동선택(현 런타임 문자열 매칭 → DB attribute). 🚨 **parity-safe: 견적 출력 무변경**(F1.5는 DB attribute만 추가, F4 가 소비). estimate-app·계산 무변경.

## 1. 정찰 결론
- GAS `classifyHome_`(`clients/web/estimate-app/lib/code.js:542-560`): 판넬 분류(`/공기청정|공청/`·`/WIFI/`·`/미내장/`·`/인피니트/`) + 부자재(리모컨/분기관/유연호스). `BundleExpander.matchOptionRemote`(`services/product-service/.../BundleExpander.java:221-228`): 유선/컬러유선. `isPanel/isRemote`(330-366): 런타임 정규식.
- `Product`(product-service): panelType/remoteType **없음**(신규 필요). `tags`(jsonb)·F1 `Classification`(catL/M/S, 별개) 존재.
- `BundleComponent`: componentKind(PANEL/REMOTE/...)·componentVariant(원형/사각/WIFI/기본, 비정규화 문자열)·isDefault **존재**. 360 원형/사각은 componentVariant 가 이미 보유 → F1.5 범위 외(유지).
- `ProductSheetSyncService`: 구성품 sync 하나 panelType/remoteType 미분류.

## 2. 핵심 설계 (PM, 비정책 — 개발책임자 B 결정 토대)
- **분류 = 코드(서비스)**, 룰테이블 아님(B 경량). `ProductAttributeClassifier`(신규 서비스)에 GAS `classifyHome_` 판넬 분기 + `matchOptionRemote` 정규식 **1:1 포팅**(parity).
- **attribute = Product 컬럼**(panelType/remoteType, VARCHAR nullable). 판넬/리모컨 제품에만 set(비해당 null).
- **360 원형/사각은 componentVariant 유지**(F1.5 미변경).

## 3. 구현 (Codex)
### BE — product-service
- `Product` 신규 컬럼: `panel_type VARCHAR(32) NULL`, `remote_type VARCHAR(32) NULL` + Flyway `V_`(fresh PG probe 검증). 엔티티 getter/세터(seed/sync 경유).
- `ProductAttributeClassifier`(신규): 
  - `classifyPanelType(name, model)`: 공청(공기청정/공청)·일반(WIFI/미내장)·인피니트·블랙·승강·360 등 — GAS `classifyHome_` 판넬 분기(code.js:542-551) 정규식 1:1. 비판넬=null.
  - `classifyRemoteType(name)`: 유선·컬러유선·무선 — `matchOptionRemote`(BundleExpander:221-228) + 무선 fallback. 비리모컨=null.
- `ProductSheetSyncService` 통합: Product 적재/업데이트 시 classifier 호출 → panelType/remoteType set. 기존 sync 흐름 무변경(추가만). 재sync 시 백필.

### 무변경
- estimate-app·BundleExpander 매칭 로직·계산 **무변경**(F1.5는 attribute 적재만; F4 가 BundleExpander.pickPanel 을 attribute 기반으로 전환). FE 무변경.

## 4. 검증
- **classifier 단위 테스트**: GAS 정규식 parity — 대표 품명(공청판넬/WIFI판넬/360판넬/유선리모컨/컬러유선/무선) → 기대 panelType/remoteType. (가능하면 GAS classifyHome_ 출력과 교차.)
- **sync IT**(Testcontainers): 시드 Product(판넬/리모컨 포함) sync → panelType/remoteType 자동 채움 + 비해당 null + 회귀 0(기존 카탈로그/구성품 sync 무영향).
- 변경 모듈 전체 test 완주. 마이그 fresh PG probe.
- **실QA**: product-service 재빌드 후 실 DB Product 의 panelType/remoteType 분포 query(판넬 N건 공청/일반 분류, 리모컨 유선/무선) — 실데이터 검증. 견적 출력 불변(parity) 확인.

## 5. 리뷰 워크플로우 (BE-only·저위험 → 3-agent 포커스)
Opus 3-agent(BE classifier parity / QA 테스트·sync / DevOps CI·마이그) → Codex 교차 → Opus 수렴 → 실QA(분포 query) → CI green → 머지. FE 없음(estimate-app/desktop 무변경).

## 6. 리스크
- GAS 정규식 누락 시 분류 실패(F4 매칭 실패) → 단위 테스트 parity + 실 DB 분포 query 로 검증.
- 컬럼 추가 마이그(fresh PG probe). 기존 sync 회귀 0.
- 360 원형/사각 componentVariant 유지(혼동 주의 — F1.5 panelType 은 공청/일반 등 제품종류, 형상은 componentVariant).
