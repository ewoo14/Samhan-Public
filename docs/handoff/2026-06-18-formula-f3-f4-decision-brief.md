# 수식 빌더 F3/F4 착수 결정 브리프 (PM 제안 — 개발책임자 확정 필요)

> 2026-06-18 야간. G1(#502 specDetailMap DB) 머지 후, 수식 빌더 **잔여 작업이 전부 설계 결정 게이트**임을 정찰로 확인 → PM 자율 진행 불가. 본 브리프는 오전 결정 가속용(구현 아님, 결정 프레이밍 + 권고).

## 0. 왜 멈췄나 (게이트 확인)
- **F2(거래처 DC UI)**: 이미 구현됨(`SalesPartnerDcConfigPage`). 불요.
- **F3(옵션 설정)+F4(자동매칭)**: 옵션 자동매칭은 **GAS에 없는 신규 동작**(스펙 §4 D1) + 견적 결과 영향 → 개발책임자 설계 확정 필요.
- **homeDefaults/singleDefaults(estimate-app 마지막 시트 의존 잔여)**: `getSingleDefaults`가 판넬변경·**360판넬(원형/사각)**·유선리모컨·자재포함 등 **옵션 default**를 담음 = F3 소관과 엉킴 → F3 결정 전 마이그 시 rework. **F3에 귀속해 함께 진행**해야 함.
- **F7(VAT/배분), 멀티 동적가격 #19, Phase 우선순위**: 비대상/정책/미확정.

## 1. 핵심 결정 ① — F3/F4 옵션 자동매칭 설계

### 현황
- **GAS**: 자동매칭 수식 **없음**. 시트 Row2 default(예 360판넬='원형') + 사용자 수동 선택. 실내기↔구성품 연결 = `setModel`(세트 모델명) **데이터 관계**.
- **우리 코드**: `BundleOptionRow`(견적/전표 라인별 옵션 수동 선택: 판넬/공청판넬/360원형·사각/리모컨/자재) + `BundleExpander`(수동 입력 기반 세트 전개·6:4 배분) **기구현**. 자동 선택 룰 엔진은 없음.
- **개발책임자 요구(스펙 §3 #3)**: "옵션 변경 시 자동 적용 — 판넬→'공청판넬' 설정 시 해당 실내기에 맞는 공청판넬 자동 선택(공청판넬 여러 개 중)."

### 설계 옵션 (난이도·유연성)
| 안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| **A. 속성+룰 엔진** (스펙§4 D1) | Product attribute(panelType/remoteType) DB 분류 + `OptionBundleRule`(setModel+조건→자동선택) 테이블 | 최대 유연·신규 품목 코드 무 편입 | 구현 대(룰 CRUD·평가), 과投資 우려 |
| **B. 경량 휴리스틱** (PM 권고) | 품목 attribute 1회 분류(panelType 등) + 옵션 토글 시 **setModel 그룹 내 attribute 매칭 자동선택**(코드 고정, 룰테이블 없음) | 단순·즉시·GAS parity 유지하며 자동매칭만 추가 | 새 매칭 *패턴* 출현 시 코드 수정 |
| **C. 현행 유지 + default만** | 자동매칭 없이 옵션 **default 설정 UI**(F3=옵션 기본값 설정)만 | 최소·무위험 | 개발책임자 "자동 적용" 요구 미충족 |

**PM 권고 = B**. 근거: 개발책임자 요구(자동 적용)를 충족하되, GAS엔 없던 룰엔진(A)은 변동 빈도 낮은 매칭에 과投資. attribute 분류(F1 catL/M/S 인프라 재사용 가능)는 1회성. 신규 패턴은 드물어 코드 수정 수용 가능. (B로 부족함이 실증되면 A로 승급.)

### 확인 필요 (개발책임자)
1. **설계 = A / B / C 중?** (PM 권고 B)
2. **자동매칭 정확 동작**: "공청판넬 ON" → setModel 그룹 구성품 중 `panelType=공청` 자동 선택. 후보 여러 개면 선택 규칙(용량 매칭? 첫번째? 사용자 재선택?).
3. **360판넬 원형/사각**: default(현 '원형') + 사용자 오버라이드 유지(GAS 동일)? F3에서 거래처/품목별 설정화?

## 2. 핵심 결정 ② — homeDefaults/singleDefaults (F3 귀속)
`getHomeDefaults`/`getSingleDefaults`(code.js:1101/1131)는 옵션 default(유연호스 제외·리모컨·판넬변경·360판넬·자재포함·할인·1WAY할인). **F3 착수 시 DB 승격**(estimate_configs 또는 dc-config 확장) → estimate-app 3탭 prefetch 완전 제거(시트 의존 0 완결). G1 패턴(reshape endpoint) 재사용. **단독 마이그 금지**(F3 설계가 default 구조 결정).

## 3. 핵심 결정 ③ — Phase 우선순위 (기획서 §8)
| Phase | 옵션 | PM 권고 |
|---|---|---|
| 1 | A(파라미터 설정 18종: 변동DC 공통율·카드수수료·조합비경고·안내문구) | **착수 권고**(저위험·즉시가치, 변동DC 토글이 첫 조각). card_fee_rate 손실 방지 P2. |
| 2 | B(계산규칙 템플릿) | F3/F4(B안) 와 통합 진행 가능 |
| 3 | C(노코드 수식빌더) | **보류**(과投資 경계, 진짜 임의수식 필요 시만) |

**확인 필요**: Phase1 파라미터 설정 착수 여부 + 범위(카드수수료·조합비경고 우선?).

## 4. 결정 후 PM 진행 (예상 슬라이스)
1. (Phase1) 파라미터 설정 UI + estimate_configs — 저위험 선행 가능.
2. (F1.5) 품목 attribute 분류(panelType/remoteType) DB — B안 토대.
3. (F3) 옵션 default 설정 UI + homeDefaults/singleDefaults DB 승격 + 3탭 prefetch 제거.
4. (F4) 옵션 토글 자동매칭(B 휴리스틱).
5. (F5) estimate-app 설정 기반 계산 전환(golden parity 회귀).

→ **개발책임자 결정 1·2·3 회신 시 PM 즉시 착수.** 회신 전까지 수식 빌더 진행 보류(rework 방지).

## ✅ 개발책임자 결정 (2026-06-18 야간 확정)
1. **F3/F4 설계 = B 경량 휴리스틱** — 품목 attribute(공청/일반/360원형·사각 등) 1회 분류 + 옵션 토글 시 같은 세트(setModel) 그룹 내 매칭 구성품 자동선택. 룰 테이블 없이 코드 고정.
2. **자동매칭 후보 다수 시 = 세트 기본 구성품(isDefault) 우선**.
3. **Phase 1(가격 파라미터 설정 메뉴) 착수** — F3/F4와 별개 선행.

### PM 진행 순서 (확정)
1. **Phase 1 — 가격 파라미터 설정 UI + `estimate_configs`** (전역) + dc-config 확장(카드수수료·선금할인 per-partner). ← **다음 슬라이스**
2. F1.5 — 품목 attribute 분류(panelType/remoteType, F1 catL/M/S 인프라 재사용) = B안 토대.
3. F3 — 옵션 default 설정 UI + homeDefaults/singleDefaults DB 승격 + estimate-app 3탭 prefetch 완전 제거.
4. F4 — 옵션 토글 자동매칭(B 휴리스틱, isDefault 우선, setModel 그룹 내).
5. F5 — estimate-app 설정 기반 계산 전환(golden parity 회귀).
