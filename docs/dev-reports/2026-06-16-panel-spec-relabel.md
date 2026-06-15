# 판넬 사양 오라벨 수정 — 판넬-aware remap (사양 후속 #3 데이터) — 개발 리포트 (PR #488)

> 2026-06-16 세션. 사양 후속 큐(#1/#2/#3) 머지 완료 후 **#3 데이터 정리(사양 오라벨)** 슬라이스. 개발책임자 선택. 다모델(Opus 계획/리뷰 ↔ Codex 개발/교차).

## 1. 배경·근본 원인
사양 후속 #3 후속으로 남겨둔 "싱글 판넬/리모컨 일부 DB spec_key 오라벨"(예 냉방능력=1020=실제 타공사이즈)을 조사·수정.

- **근본**: legacy GAS 시트는 판넬 행도 AC 기기와 **동일 헤더**(냉방성능/소비전력 컬럼)를 쓰되 그 컬럼에 **타공사이즈/전산볼트간격** 값을 담는 혼용 구조. `ProductSheetSyncService` 가 legacy `getSpecDetailMap_` 위치 매핑을 1:1 포팅(사양 후속 #1)하며 판넬도 `냉방능력`/`냉방소비전력` 키로 저장돼 오라벨됨.
- **노출 지점**: estimate-app 은 UI 판넬 분기(`renderSingleSpec_`/`renderCommSpec_`, index.ejs)로 `cool_cap_kcal`→'타공사이즈' 재라벨해 **시트 기반 보정**. 그러나 **DB spec_key 자체가 틀려** 데스크톱 품목 폼·#3 세트 구성품 사양 표시(DB specs)에서 오라벨 그대로 노출.
- **실데이터 범위**: 판넬(PC*) HOME_MULTI 32 + COMMERCIAL_MULTI 13. PC6NUNK1NW = 냉방능력,kcal/h=1020(타공), 냉방소비전력,kW=645(전산볼트). 별도 타공/전산볼트 spec 없음(능력 컬럼이 실제 타공/볼트).

## 2. 수정 (`ProductSheetSyncService`)
- `isPanelRow(name, modelCode)` — 이름 `판넬/판널/패널` OR model `(?i)PC[0-9].*`(전체 PC* 59개가 PC+숫자, 비판넬 PC* 0건 검증).
- `loadPanelSpecs` 분기(HOME/SINGLE/COMMERCIAL): 판넬 행은 냉방성능 컬럼(`firstNonBlankColumn`)→**타공사이즈, mm**, 소비전력 컬럼→**전산볼트간격, mm** remap. **AC 전용 키 미생성**(능력/난방/소비전력/배관경/냉매가스/효율/전원선/차단기). 제품크기/제품중량/포장 유지(HOME/COMM, SINGLE 판넬은 타공/전산볼트만). displayOrder = V17 template(HOME 16/17·SINGLE 22/23·COMMERCIAL 19/20). 구 오라벨 키는 seenKeys 부재로 soft-delete 자동 정리.

## 3. 검증 (실 시트 재동기화 → 실 product_db)
- 판넬 PC6NUNK1NW: 타공사이즈,mm=1020 + 전산볼트간격,mm=645 + 제품크기 — 냉방능력/냉방소비전력 오라벨 제거.
- 판넬(PC*) 전수: 냉방능력/냉방소비전력 active **0**, 타공사이즈 48·전산볼트간격 48.
- **비판넬 회귀 0**: HOME 6/119·COMMERCIAL 13/338·SINGLE 2/276 0-사양 분포 동일, AM320 능력 4 유지.
- **실 QA 캡처**: 판넬 PC1BWCK3N 편집 폼 — 타공사이즈 1380·전산볼트간격 1260 정합(`docs/qa/panel-spec-relabel/01-panel-edit.png`). real-qa spec 4/4 통과.

## 4. 듀얼리뷰 (Opus BE + Codex)
- remap 로직·displayOrder·soft-delete·회귀 정확(양 모델). Codex 결함 0.
- Opus P1 2건 반영: ① `isPanelRow` `PC.*`→`PC[0-9].*` 좁히기(미래 비판넬 PC 오탐 방지) ② 판넬 remap **BE IT 회귀가드** 추가(`sync_판넬행_타공사이즈전산볼트간격_매핑_능력키_미생성`: 판넬행→타공/전산볼트+능력키 미생성, 비판넬행→냉방능력 유지).

## 5. 후속(별도)
- **상업 combo 모듈 kind=ACCESSORY→[부속]**: AM* 상업 실외기 combo 모듈이 상업멀티 구성 시트 구분 미지정으로 ACCESSORY 폴백. 올바른 kind(OUTDOOR vs 전용 combo) 분류는 모델링 결정 — 개발책임자 확인 후 처리.
- 리모컨(AR-*) 개별사양 거의 없음(2건) — 오라벨 낮음, 미대상.
