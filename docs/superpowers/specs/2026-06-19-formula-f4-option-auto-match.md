# F4 — 옵션 토글 자동매칭 (attribute 기반 + isDefault 우선 + regex fallback)

> 2026-06-19 개발책임자 "재개"=F4 go. 결정 시퀀스 F1.5✅→F3✅→**F4**→F5. B 경량 휴리스틱(개발책임자 결정): 옵션 토글 시 setModel 그룹 내 attribute 매칭 자동선택, 다중후보=isDefault 우선. [[project_formula_builder_epic]]

## 0. 목표
`BundleExpander.pickPanel`/`resolveRemotes`의 판넬/리모컨 옵션 매칭을 **F1.5 Product.panelType/remoteType attribute 기반**으로 전환(현 런타임 정규식 → DB attribute) + **다중후보 isDefault 우선**(현 `findFirst()` rownum 의존 → 결정적). 🚨 **attribute null 시 기존 정규식 fallback**(parity 보존).

## 1. parity 정의 (중요 — 견적 출력 영향 첫 슬라이스)
- **단일후보**: 변경 없음(attribute든 regex든 같은 1개 매칭). parity.
- **다중후보**: 현 `findFirst()`(DB rownum 의존·비결정적) → **isDefault 우선**(결정적). = 개발책임자 결정 B 의도된 개선(parity 아닌 개선). 기존이 비결정적이라 "동치"가 아닌 "결정화".
- **attribute null**(dev DB·미sync): regex fallback → 현 동작 = parity.
- estimate-app/desktop/계산·견적 금액 무변경(매칭 메커니즘만). FE 무변경.

## 2. 정찰 결론 (BundleExpander)
- `pickPanel`(~151-188): panelOption(공청판넬/블랙판넬/승강판넬/판넬제외)+360 형상(원형/사각, variant). 다중후보 `findFirst()`. basePanel=isDefault fallback.
- `resolveRemotes`/`matchOptionRemote`(~190-228): remoteOption(유선/컬러유선) 매칭, defaults=isDefault 그룹, 교체(drop+add).
- `Part`(~443-469): isDefault 보유, **panelType/remoteType 미보유**(추가 필요). `expand()`(~89-99)서 component Product 의 panelType/remoteType 할당 필요(repository 조회/join 확인).
- `isPanel`/`isRemote`(~330-366) 정규식 유지(종류 판정).

## 3. 구현 (Codex)
### BE — product-service `BundleExpander`
- **Part 확장**: `panelType`/`remoteType` 필드 추가. `expand()`서 각 component 의 Product panelType/remoteType 할당(componentRepository 조회에 Product attribute 포함 — join/batch-fetch by modelCode; 누락 시 null).
- **pickPanel attribute 전환**: 옵션→panelType 매핑(공청판넬→'공청'·블랙판넬→'블랙'·승강판넬→'승강'·360→'360'). 매칭: ① attribute(panelType==target) 후보 중 **isDefault 우선** → ② attribute 후보 비기본 → ③ **attribute 전무(null) 시 기존 정규식 fallback**(isDefault 우선 적용) → ④ basePanel/first. 360 형상=panelType '360' + variant(원형/사각) + isDefault 우선.
- **resolveRemotes attribute 전환**: matchOptionRemoteByType(remoteType=='유선'/'컬러유선' 우선, null/미매칭 시 기존 matchOptionRemote 정규식 fallback). isDefault 그룹 우선 유지. 교체 로직(drop+add) 보존.
- **classifyRemoteType variant 보강**(P2): F1.5 classifyRemoteType 가 name만 봄. F4에서는 Product attribute 전역 부작용을 피하기 위해 BundleExpander의 유선 매칭에서 컬러 텍스트를 배제하고, componentVariant 기반 재분류는 **F5 이관**으로 명시한다.

### 무변경
estimate-app/desktop FE·가격 계산·견적 금액. 옵션 전달(panelOption/remoteOption/panelShape360) 인터페이스 동일.

## 4. 검증 (golden parity)
- **IT(BundleExpanderIT, Testcontainers)**:
  - 단일후보 parity(기존 케이스 동치).
  - **다중후보 isDefault 우선**(공청/블랙 기본+비기본 → 기본 선택).
  - **attribute 기반**(seeded panelType='블랙'/remoteType='유선' → 매칭).
  - **fallback**(panelType=null → 정규식 매칭 = 현 동작).
  - 360 형상 panelType+variant+isDefault. 리모컨 교체 isDefault 그룹.
- **Docker 실QA**: dev DB attribute **null**(미sync) → 라이브는 **fallback(regex)=parity** 실증(신규 견적 옵션 선택 = 현 동작). attribute-match 자체는 IT(seeded)로 검증 + 정직 보고(전 카탈로그 attribute-match 라이브는 다음 credentialed sync 의존).

## 5. 리뷰 워크플로우
Opus 5-agent(BE 매칭 parity/isDefault/attribute·QA·DevOps) → Codex 교차 → Opus 수렴 → Docker 실QA(fallback parity) + IT(attribute-match) → CI green → 머지. golden parity 최우선.

## 6. 리스크
- 🚨 견적 출력 영향(다중후보 isDefault 변경) — golden IT 로 단일=parity·다중=isDefault 명시 검증. 라이브 fallback parity.
- attribute 미populate(dev) → attribute-match 라이브 미검증(IT 보완·정직 보고).
- Part 에 Product attribute 할당 시 repository 조회 비용(N+1 주의·batch).
- classifyRemoteType variant 보강은 F1.5 재sync 및 Product attribute 전역 의미 변경이 필요할 수 있어 F5에서 별도 설계한다.
