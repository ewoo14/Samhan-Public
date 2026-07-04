---
name: feedback_mock_value_format_be_parity
description: 리뷰·QA 시 mock/시드 데이터의 값 형식이 실 BE 채번 규칙과 parity 인지 대조 — 필드 존재뿐 아니라 값 포맷(전표/분개번호=슬래시 YYYY/MM/DD-N)까지
metadata:
  node_type: memory
  type: feedback
---

mock/시드/QA 데이터는 필드 shape 뿐 아니라 **값 형식**도 실 BE 채번·포맷 규칙과 parity 여야 한다. 리뷰(FE 계약·BE 계약·Design·QA)는 "필드가 있다/컬럼이 맞다"에 그치지 말고 **값이 실 BE가 생성하는 형식인지** 대조할 것.

**Why:** #727 E3 S4a 에서 Codex mock 이 전표번호 `SLP-202605-021`·분개번호 `JRN-202605-49` placeholder 를 썼는데, 실 BE 는 `SlipNumberService`/`JournalNumberService` 로 **슬래시 `yyyy/MM/dd-N`**(예 `2026/05/19-3`)를 채번한다([[feedback_slip_order_number_format]]). Opus 5-agent(BE contract·Design)+Codex 두 리뷰 라운드가 필드/컬럼/UUID 비노출은 대조했으나 **값 형식 parity 를 전원 놓침** → 개발책임자가 QA 스샷에서 직접 적발. (Codex 는 같은 부류인 `amount:'0'` BE-불가 값은 잡았으나 식별자 형식은 놓침.)

**How to apply:**
- mock/시드에 식별자(전표·주문·분개·세금계산서 번호)를 넣을 때 반드시 실 BE 생성기 형식 확인(테스트 리터럴 `startsWith("2026/..")`·`*NumberService.next()` 포맷). placeholder prefix(SLP-/JRN-/PAS-/SAS- 등) 금지.
- mock 계약 테스트에 **형식 parity 가드** 추가(정규식 `^\d{4}/\d{2}/\d{2}-\d+$` 등) — 값 오류를 unit 에서 고정. (#727 mock.test.ts 선례)
- 리뷰 체크리스트: 표시되는 비즈니스 식별자는 실 BE 채번 형식과 1:1 대조. → [[feedback_no_fake_data_ever]] · [[feedback_slip_order_number_format]] · [[feedback_jeonpyo_not_slip]]
