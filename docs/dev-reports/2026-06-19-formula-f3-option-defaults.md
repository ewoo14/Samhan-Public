# F3 — 옵션 default DB 승격 (dev-report)

> 2026-06-19. 수식 빌더 시퀀스 F1.5✅→**F3**→F4→F5. PR #505.

## 요약
estimate-app 종합견적서의 옵션 default(homeDefaults/singleDefaults — 시트 Row1-2 런타임 read)를 **estimate_configs(Phase1 싱글톤, dc-config-service) 확장**으로 DB 승격 + 데스크톱 설정 UI + estimate-app DB 모드 시트 의존 완전 제거. parity 완전 보존(라이브 Row2 검증 = 코드 fallback = 시드).

## 변경
### BE — dc-config-service
- `EstimateConfig`: home/single 옵션 default 12필드(homeNoHose/homeNoBranch/homeWithFoot Boolean, homeDefaultPanel String; singleDefaultWiredRemote/singleDefaultPanel/singlePanelShape/singleMaterialInclusion String, singleNoRemote/singleWithBase Boolean, singleDiscount/singleOneWayDiscount BigDecimal). update() null-coalescing(PATCH 의미).
- `V5__add_estimate_option_defaults.sql`: estimate_configs ADD COLUMN(nullable+default) + 싱글톤 행 UPDATE = 검증 시드(home 전 false/판넬'', single 받침대 false·판넬''·360판넬'원형'·할인/1WAY 0·자재'별도'). fresh PG probe(V1-V5 체인) 통과.
- `EstimateConfigController`(GET/PUT) + `InternalDcConfigController`(/internal/estimate-config) DTO 자동 확장(record). `UpdateEstimateConfigRequest`: @Digits(12,2)·@Size(64/16) 입력 검증.
- IT: default 직렬화·PUT 왕복·400(상한/길이)·Phase1 회귀 0.

### estimate-app
- `getHomeDefaults(config?)`/`getSingleDefaults(config?)`: useDb 시 DB config 에서 **기존 한글-key shape 그대로** 반환(시트 read 안 함), sheet 모드 fallback 유지. `normalizeEstimateConfig_` default 정규화 + DEFAULT_ESTIMATE_CONFIG fallback.
- bootstrap: DB 모드 `dbCatalog.estimateConfig()` 1회 read → t.homeDefaults/t.singleDefaults DB 주입.
- **DB 모드 3탭 prefetch 전부 제거**(sheetsToPreload=[]): HOME/SINGLE Row2 default→DB, COMM 잔여=sheet-mode getter(getCommercialMulti/getPriceIncData_/getSpecDetailMap_)뿐·DB 모드는 dbCatalog → DB 모드 시트 read 0.
- `views/index.ejs`: ss_p360(360판넬) 초기+reset 경로 `SINGLE_DEFAULTS['360판넬']||'원형'` 읽음(기존 하드코딩 '원형' → 설정 반영 완결, parity-safe).

### FE — desktop
- `EstimatePricingConfigPage` 옵션 기본값 섹션(home 체크박스3+판넬 select, single 유선리모컨/제외/받침대/판넬/360판넬/할인/1WAY/자재). canAccess('sales.estimate-config') 재사용. sales.ts/mock.ts DTO 타입 정합.

## parity / 검증
- 🚨 라이브 Row2 SA키 read 검증 → 전 항목 코드 fallback 정확 일치 → 시드=검증값 → 신규 견적 초기 옵션 불변.
- 다모델: Opus 3-agent(P2 360판넬·amount 상한)→Codex fix→Codex 교차(PASS)→Opus 수렴. prefetch 제거 grep 교차(db-catalog.js 시트 read 0).
- dc-config 컴파일·V5 fresh probe·estimate-app jest 92·desktop typecheck/vitest 88.
- Docker 실QA: 신규 견적 초기 옵션=검증 시드 불변 + 설정(공청판넬/자재포함/360사각)→반영.

## 다음
F4 — 옵션 토글 자동매칭(B 휴리스틱·isDefault 우선, F1.5 panelType/remoteType + BundleExpander.pickPanel attribute 전환). F5 — estimate-app 설정 기반 계산 전환.
