---
name: project-formula-builder-epic
description: "수식 빌더 에픽 — 종합견적서/주문서 하드코딩 수식 → 메뉴 설정 기반 계산 전환. F1·F6·G1·Phase1 완결, 결정 시퀀스 F1.5→F3→F4→F5"
metadata: 
  node_type: memory
  type: project
  originSessionId: b9ce13e8-ed39-4a45-89a5-ff3c53f85914
---

수식 빌더 에픽 = estimate-app(종합견적서)·order-app(주문서)의 BE 하드코딩 수식 → **메뉴 설정 기반 계산** 전환 (개발책임자 방향).

## 완결 (2026-06-18)
- **F1 (#499, `ab0093ea`)**: 품목 분류 3단계(catL/M/S — classifyHome/classifyCommercial/classifySingleSet GAS 1:1 포팅) + 고정DC% 인라인 컬럼(변동DC 옆, 빈칸=전역DC, blur 자동저장·저장버튼 없음) + 변동DC 체크박스-only + 분류 관리 메뉴(ProductClassificationsPage) + V20 classification 테이블. 싱글 분류 GAS parity fix(SINGLE_SET이 classifyHome 오라우팅 → 부자재 88%→1%). 종합견적서 시뮬 811품목 단가 차이 0.
- **F6 (#501, `68b0d634`)**: 주문서 product_db 적용(EstimateCatalogClient+BootstrapService, **inc map=인상후 catalog**[price-baseline 2000-01-01 인상전 아님], modelCode→model/hasVariableDiscount→useK2/fixedDiscountRate→고정DC 변환) + DC율 + gateway bootstrap 공개 route + dc-config 단건 @EntityGraph + **비번 4자리 PIN**(@Pattern \d{4}, 거래처코드/사업자번호 10자리는 로그인 ID 별개) + 분기계산/모바일서랍 데스크톱 숨김. 노션 거래처 DC율 재시드 259. 실QA 제이시스템(8428102605).

- **G1 (#502, `a2d36319`)**: estimate-app specDetailMap 런타임 Google Sheets → product-service DB endpoint(`/products/internal/estimate-catalog/spec-detail-map`, 이미 적재된 ProductSpec reshape·신규 스크랩/sync 0). specKey(한글 라벨, ProductSheetSyncService 저장형식)→getSpecDetailMap_ JS 필드명 매핑(home 18/single 21/comm 17/ERV 23 + **판넬 overlay** 타공사이즈/전산볼트간격→cool_kw·cool_power). estimate-app 마지막 런타임 시트 의존 제거(homeDefaults/singleDefaults 잔존→후속). 다모델 Opus5→Codex fix(canary)→Codex 교차(**판넬 회귀 P1 단독 적발**)→판넬fix→Opus 수렴 blocking0. 라이브QA 733모델·판넬 타공860/볼트798. 🪤 #488이 판넬 타공/볼트를 전용 specKey로 정규화 → reshape가 렌더 legacy 필드로 안 돌리면 시트모드 대비 회귀(교차리뷰 적발).
- **Phase1 (#503, `162b9f9d`)**: `estimate_configs` 싱글톤(dc-config-service, 전역 가격 파라미터: 변동DC공통율0.45·구형DC0.5·VAT0.1·**카드수수료0.03**·선금할인0·조합비경고0·footer) + V4(CHECK·partial unique singleton·시드) + admin GET/PUT(`/api/v1/estimate-config`) + internal endpoint + 데스크톱 EstimatePricingConfigPage(`/sales/estimate-config`, 권한 V58 sales.estimate-config MASTER/MANAGER) + estimate-app 통합(상수→DB: 변동DC공통율·구형DC·VAT `splitVatAmount_`·카드 `applyCardFeeLogic`·선금 `applyEstimateTotalAdjustments_`·footer). 🚨 **카드수수료 현행 3% parity**(정찰 '미구현' 오인 → applyCardFeeLogic 3% 기존재 → 개발책임자 '현행 복원': seed 0.03·구 동작·요율만 설정화). 다모델 Opus5(카드 P1 2-agent 적발)→Codex fix→교차(골든 자기참조 P2)→fix2(CI-robust 골든·git show 제거)→Opus 수렴 blocking0(VAT split 571K값 전수동일). 라이브QA BE PUT200 persist·estimate-app t.config 반영. 🪤 카드 정찰오류(신규 전 현행 grep 필수)·골든 ground-truth=origin/main 동결(런타임 git show는 CI shallow서 RED)·estimate-app 재기동 EADDRINUSE(5183 점유 PID netstat 종료 후 fresh).

- **F1.5 (#504, `ecdb78b8`)**: Product panel_type/remote_type(V21 nullable+partial index) + `ProductAttributeClassifier`(panelType=옵션 매칭 단일 버킷 공청[공기청정|공청 전부]/블랙/승강/360/일반/null — F4 pickPanelRow 정합, **classifyHome_ catM 아님**; remoteType 유선/컬러유선/무선/null) + ProductSheetSyncService 통합(productCategory guard 내 — 교차탭 stomp 방지). parity-safe(컬럼 write-only, 견적 출력 무변경). dual-model: Opus(P1 taxonomy F4-misalign)→Codex fix→Codex 교차(**P1 cross-tab stomp 단독 적발**)→stomp fix→Opus 수렴 blocking0(실 테스트 실행 검증). 🪤 라이브 분포는 dev product-service SA키 부재로 미populate(Testcontainers IT로 메커니즘 실증·정직 보고). **P2(remoteType variant 미반영=name만 봄)·실 카탈로그 분포·컬러리모컨 누수 = F4 소비 시 검증**.

## 다음 (개발책임자 결정 확정 시퀀스 — 자율 진행)
**개발책임자 결정(2026-06-18 야간)**: F3/F4 설계=**B 경량 휴리스틱**(품목 attribute 분류 + 옵션 토글 시 setModel 그룹 내 매칭 자동선택, 룰테이블 없음)·자동매칭 후보다수=**세트 기본 구성품(isDefault) 우선**·Phase1 착수(완료)·카드수수료 **현행 3% 유지**.
**진행 순서**: ~~F1.5(완료 #504)~~ → **F3 (다음)**: 옵션 default 설정 UI + homeDefaults/singleDefaults(판넬변경/360판넬/유선리모컨/자재포함 등 옵션 default, code.js:1101/1131) DB 승격 + estimate-app 3탭 prefetch 완전 제거. → **F4**(옵션 자동매칭 B·isDefault 우선·setModel 그룹, **F1.5 panelType/remoteType + BundleExpander.pickPanel attribute 기반 전환**; classifyRemoteType 가 variant 도 읽도록 보강 필요) → **F5**(estimate-app 설정 기반 계산 전환·golden parity 회귀).
**F2 = ✅ 이미 구현됨**(SalesPartnerDcConfigPage). F7(VAT/배분)=기획서§7 비대상. 멀티 동적가격#19=🔒견적금액 변동 정책. 브리프 [[]] `docs/handoff/2026-06-18-formula-f3-f4-decision-brief.md`.

기획서 `.claude/tmp/estimate-formula-builder-plan.md`. [[project_quotation_estimate_app_state]] [[project_estimate_spec_data_sources]] [[feedback_stacked_pr_ci_false_green]]
