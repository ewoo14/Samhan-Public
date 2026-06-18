# 수식 빌더 에픽 기획 (종합견적서·주문서 수식 설정화)

> 2026-06-17 개발책임자 방향. 슬3-1(변동DC) 머지(#498) 후 대단위 에픽. 정찰 2건(estimate-app 수식 인벤토리 + GAS 번들 매칭) 기반.

## 1. 목표
종합견적서(estimate-app) + 주문서의 가격/수식 계산을 **백엔드 하드코딩 → 프론트(메뉴) 사용자 설정 기반**으로 전환. **신규 품목 유입 시 코드 변경 없이 메뉴에서 견적 수식 정의**. 🚨 **GAS parity 유지 최우선**(설정 기본값=GAS 시트값/분류, 사용자 변경 시에만 달라짐).

## 2. 수식 인벤토리 (정찰 결과)
### 2.1 하드코딩 수식 27개 (4개 파일)
| 수식 | 위치 | 현 하드코딩 | 설정 단위 |
|---|---|---|---|
| 변동DC율(홈/상업멀티) | `estimate-app/lib/code.js:137-138` | 0.45 | 거래처/카테고리 |
| 고정DC 6종(360판넬/4Way/스탠드/1Way/디럭스/1등급) | `code.js:140-145` | 0(정액) | 거래처/옵션 |
| 단위반올림(천/만원·모드) | `code.js:146-147` | 1원·ROUND | 거래처 |
| VAT | estimate-app/slip(미확인) | 0.1 | 전역 |
| 실내:실외 배분(가정 6:4/비가정 4:6) | `BundleExpander.java` | 6:4/4:6 | 카테고리/거래처 |
| 번들옵션(패널5+/리모컨3+/자재) | `BundleExpander.java` | 분기 | 옵션 |

계산 경로: estimate-app(code.js) → **dc-config-service**(`DcConfig` 거래처별·`PriceCalculationService`) → **partner-order**(`DcConfigClient` 주문확정 server-side, 클라가격 무시).

### 2.2 🔑 GAS 번들 매칭 핵심 발견 (a8ab 정찰)
- **GAS에 자동 매칭 수식 없음**. = 시트 기본값(Row 2) + 사용자 선택.
- 실내기↔구성품 = `setModel`(세트 모델명) **시트 데이터 관계**. 클라가 setModel 같은 구성품 필터 → kind=판넬/리모컨 옵션 목록 → 사용자 선택.
- 판넬 분류 = `classifyHome_()` **품명 정규식**(`공기청정|공청`+WIFI/미내장/인피니트). 공청판넬=품명 패턴, 용량 매칭 없음.
- 360판넬 원형/사각 = `getSingleDefaults()` Row2 기본값('원형')+사용자 오버라이드.
- 추천실외기 = `추천실외기` 시트(냉방용량 kW→HP, comm/home/homeEx).
- estimate-app 이식 ~70%(기본값 Row2 endpoint 미완).

## 3. 개발책임자 요구
1. **변동DC**(boolean 토글, 전역할인율 영향없이 기초납품가 그대로) — 슬3-1 완료.
2. **고정DC %단위**(각 품목 고정 DC율) 신규 — 변동DC 옆 컬럼. **기초값=시트값**.
3. **옵션 변경 시 적용 설정**: 판넬→'공청판넬' 설정 시 품목구분 '판넬'+특징 '공청판넬' 매칭 → **해당 실내기에 맞는 공청판넬 선택**(공청판넬 여러 개). 리모컨 등 동일.
4. **360판넬 '원형'/'사각'** 2가지 따로 설정(GAS처럼).
5. **종합견적서+주문서 모든 수식** 설정화.

## 4. 핵심 설계 결정 (개발책임자 확인 필요)
### D1. 옵션 자동 매칭 = 신규 설계
GAS엔 자동 매칭 없음(사용자 선택). 개발책임자 "옵션 변경 시 자동 적용"은 **신규**:
- 품목 attribute(`panelType`=공청/일반/360원형/360사각, `remoteType`, catL/catM) DB 저장 — GAS 품명 정규식 → DB attribute 1회 분류.
- 옵션 토글(공청판넬 ON) → 해당 setModel 구성품 중 `panelType=공청` 자동 선택. "해당 실내기에 맞는" = setModel 그룹 내 필터.
- 매칭 룰 엔진(`OptionBundleRule`): setModel + attribute + (필요시 용량 조건).

### D2. DC 체계 정리
- 변동DC = boolean(전역할인 제외 품목, 슬3-1).
- 고정DC = %단위 품목별(신규, 기초값 시트).
- code.js 변동DC율 0.45 = 멀티 카테고리 공통 할인율 → **거래처 DC(dc-config homeDiscountRate)** 로 귀속(별개 체계).

### D3. 설정 단위
- 전역(VAT) / 카테고리(배분비율) / 거래처(변동DC율·고정DC) / 품목(변동DC boolean·고정DC%·attribute) 계층.

## 5. 데이터 모델 (신규/확장)
- `Product` attribute 확장: panelType, remoteType, fixedDiscountRate(%), catL/catM(정규식→DB).
- `OptionBundleRule`(신규): setModel/카테고리 + 조건 → 자동 옵션.
- `DcConfig` 확장(이미 변동DC율·고정DC 6종·단위 보유) → UI 노출.
- `FormulaConfig`/`CompanyConfig`(신규): VAT·배분비율·반올림 전역.

## 6. 관계수식 표현 + GAS parity 보장 (개발책임자 핵심 요청 2026-06-17)
### 6.1 parity 보장 원칙
- **계산식 구조=코드(불변), 값·룰=설정** → 수식 구조 차이 원천 0(=parity 자동).
- **설정 기본값=현 하드코딩·시트값 시드**(마이그 1회) → 사용자 변경 전 입력→출력 완전 동일.
- 관계수식(분류/매칭/수량)=선언적 룰 테이블, 기본 룰=현 GAS 로직 1:1 시드.
### 6.2 관계수식 표현 3종
1. **산술 파라미터**(DC/VAT/배분): 변동DC율·고정DC%·VAT·실내외 6:4 = 카테고리/품목별 값. 계산식 코드.
2. **분류 계층**: catL→catM→catS Classification 트리, 품목 참조.
3. **번들 매칭 룰**(선언적): `조건(카테고리+실내기 catM/용량) → 구성품(catL/catM 선택 + 수량비율)`. 예 `{홈멀티,4Way}→{판넬 공청×1,리모컨 유선×1,실외기 받침대×1}`.
### 6.3 검증 (추후 자동)
1. **Golden master 회귀**: 현 estimate-app 견적(거래처×품목×옵션 매트릭스 N건) golden JSON snapshot → 설정 계산 byte/금액 동일 단언(기본 상태 diff 0).
2. **Shadow 비교 IT**: 동일 입력 하드코딩∥설정 diff 0.
3. **운영 shadow 로깅**(전환기): 양쪽 병렬→diff 로그→0 확인 후 하드코딩 제거.
- GAS 원본 `tools/legacy-gas/종합견적서-live/Code.js`(3204라인) 대조.

## 7. 슬라이스 분할 (제안)
- **F1**: 품목 attribute DB(panelType/remoteType/catL/catM, GAS 품명 정규식→DB 1회 분류) + 고정DC% 컬럼(변동DC 옆, 기초값 시트).
- **F2**: DC 설정 UI(거래처별 변동DC율·고정DC·단위반올림 — dc-config Admin).
- **F3**: 옵션 설정 UI(판넬 종류[공청/일반/360원형·사각]/리모컨/유연호스 + 360 분기).
- **F4**: 번들 매칭 룰 엔진(옵션 토글 → setModel attribute 자동 선택).
- **F5**: estimate-app 적용(설정 기반 계산, GAS parity 회귀).
- **F6**: 주문서 적용(partner-order 설정 반영).
- **F7**: VAT/배분비율 전역 설정 중앙화.

## 8. 리스크
- 변수 매우 많음(개발책임자) → F1(attribute 분류) 정확도가 전체 좌우. GAS 품명 정규식 누락 시 매칭 실패.
- GAS parity 회귀 필수(설정 기본=GAS, 변경 시에만 차이).
- 대단위 → F1~F2 먼저 머지하며 점진.
