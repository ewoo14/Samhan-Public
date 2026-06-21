# A2-3 — 입고전표 accept/inspect 결재자 enforcement 설계

> A2 결재 에픽 확장. **개발책임자 지시(2026-06-22)**: "입고도 적용. 타전표는 순차적으로." A2-2(출고 enforcement)를 **입고(INBOUND)에 미러링**한다. 타 전표(회계/주문/견적/배차/그룹웨어)는 후속 슬라이스.
>
> 선행: [A2-2 spec](2026-06-21-approval-outbound-enforcement-a2-2-design.md)(authorize 엔드포인트·ApprovalLineAuthorizeClient·B 게이트). A2-1c(action_key·approval_line_approver).

## 목표

입고전표(`SlipType.INBOUND`)의 `accept`(입고 수령)·`inspect`(검수)를, 결재라인 설정의 SLIP_INBOUND 역할(입고인/검수인) 결재자(그룹∪개인)만 수행하게 한다. **A2-2 출고와 동일 모델**(동적 config 조회·opt-in·system bypass·B 게이트 자동채움 유지). 기존 `inbound.inspection` 가드(INBOUND inspect)는 별개로 유지(공존).

## 설계 (A2-2 미러링)

### 데이터 — V63 마이그레이션 (SLIP_INBOUND config 시드)
A2-1c `approval_line_config`(action_key 컬럼 존재) 재사용. **신규 documentType=SLIP_INBOUND 3역할 시드**(fresh seed — rename/reorder 이력 없어 action_key 직접 지정):
- 작성자(CREATOR, seq0, action_key=NULL)
- 입고인(GROUP, seq1, **action_key=`INBOUND_RECEIVE`**)
- 검수인(GROUP, seq2, **action_key=`INBOUND_INSPECT`**)
- INSERT 시 action_key 직접 포함(V62 row_number 불요 — 신규 seed). approverGroupId/approver=0(미지정, opt-in 기본). V61 불변, V62 불변.

### slip 게이트 — slipType 일반화
`SlipService.accept/inspect` 의 OUTBOUND 전용 게이트를 **slipType→(documentType, actionKey) 매핑으로 일반화**:
- accept: OUTBOUND→(SLIP_OUTBOUND, OUTBOUND_DISPATCH) / **INBOUND→(SLIP_INBOUND, INBOUND_RECEIVE)**.
- inspect: OUTBOUND→(SLIP_OUTBOUND, OUTBOUND_INSPECT) / **INBOUND→(SLIP_INBOUND, INBOUND_INSPECT)**.
- 기존 `enforceOutboundApprovalLine` → `enforceSlipApprovalLine`(slipType 인자로 documentType·actionKey 결정) 또는 분기. opt-in·system bypass·실사용자 조건·자동채움/reserve 전 위치·`configured&&!allowed→FORBIDDEN`("입고 수령 권한이 없습니다 — 입고인 결재자(그룹/개인)만 …" / "입고 검수 권한이 없습니다 — 검수인 결재자(그룹/개인)만 …") 모두 A2-2 동일.
- auth `authorize`(documentType, actionKey, userId)·ApprovalLineAuthorizeClient 변경 없음(generic).
- INBOUND inspect 의 기존 `inbound.inspection` 컨트롤러 가드(A2-2 에서 INBOUND 전용 분기)는 **유지** — approval-line 게이트와 공존(additive, opt-in).

### FE — 입고전표 전표종류
`api/approvalLineConfigApi.ts` `DOC_TYPES` 에 `{ value:'SLIP_INBOUND', label:'입고전표' }` 추가. 결재라인 설정 메뉴 전표종류 드롭다운에 입고전표 노출 → 입고인/검수인 결재자(칩)·순서·라벨·필수 설정(A2-1~A2-1c UI 그대로 generic 동작).

## opt-in 무중단
SLIP_INBOUND 결재자 미지정(시드 기본) → authorize `{configured:false}` → 게이트 skip → 기존 `slip.transfer.process`(+inspect 의 inbound.inspection) 권한자가 그대로 처리. 결재자 지정 시 그 그룹∪개인만.

## 테스트
- **auth**: V63 fresh probe(SLIP_INBOUND 3역할·action_key INBOUND_RECEIVE/INSPECT·이관 0). ApprovalLineAuthorizationServiceTest 는 generic(추가 불요, SLIP_INBOUND 케이스 1건 선택).
- **slip 단위/IT**(실HTTP·@MockBean 격리 + ClientTest 계약): **INBOUND accept**(결재자 지정 후 비결재자 403·결재자 200·자동채움 유지)·**INBOUND inspect** 동일. **OUTBOUND 회귀 무변**(A2-2 게이트 보존). **A2-2 의 `inboundAcceptAndInspect_skipOutboundGate`(INBOUND verifyNoInteractions) 테스트는 INBOUND 가 이제 INBOUND_RECEIVE/INSPECT authorize 호출하므로 갱신**(미설정 시 opt-in 200, 지정 시 403).
- **🐳 라이브 QA**(매 라운드): 입고전표 입고인=결재자 지정→그 사용자 INBOUND accept 200·비결재 403·검수 동일·출고 무회귀.

## 범위 밖
- 타 전표/문서(회계/주문/견적/배차/그룹웨어) 결재라인(순차 후속), process/ship/deliver/confirm 등 타 전이, inbound.inspection 가드 자체 변경, 4-eye.

## 워크플로우
Codex 구현 → 🔵Opus 5-agent+QA(순차) → 🟣Codex 5-agent+QA(cross-check) → 라운드 fix(Opus=Opus직접/Codex=Codex) → **양쪽 0 수렴까지**(병렬 금지) → 머지. 매 라운드 라이브 캡처. **변경 모듈(slip+auth) 전체 test 완주 후 push**([[changed-module-full-test-before-push]]).
