# 전표/주문번호 표준 = 슬래시 `YYYY/MM/DD-{번호}` (전 영역 통일)

2026-05-31 개발책임자 정정 (D2 #334 회고).

**규칙**: 모든 영역의 전표·주문번호는 **슬래시 포맷 `YYYY/MM/DD-{번호}`** (예: `2026/05/31-8`) 로 통일한다. 화면 표시·DB 저장·API 요청 본문 전부 슬래시. BE 채번은 `DateTimeFormatter.ofPattern("yyyy/MM/dd")`.

**Why**: 회계·전표 식별자 일관성. 개발책임자가 "모든 영역의 전표번호는 YYYY/MM/DD-{전표번호} 통일하기로 했잖아" 로 명시 정정. 슬래시를 하이픈으로 "정규화/변환"한다고 표현하거나 표준 자체를 바꾸면 안 됨.

**How to apply**:
- 슬래시 포맷을 honor. 번호를 하이픈으로 치환하는 것은 **오직 URL 경로 세그먼트 한정** (게이트웨이/Spring StrictHttpFirewall 가 경로의 인코딩 슬래시 `%2F` 를 차단하기 때문).
- URL 경로 변환은 공용 `clients/desktop/src/renderer/utils/orderNo.ts` 의 **`toOrderPathId(슬래시→하이픈)`** 단일 헬퍼 사용 (목록 페이지·병합 모달 공유). 새 호출부 추가 시 별도 변환 함수 만들지 말고 이걸 재사용.
- BE `PartnerOrderIdResolver.findByIdentifier` 가 하이픈/슬래시 모두 처리(하이픈→`toSlashOrderNo` 역변환)하므로 경로 하이픈은 안전. API **본문(body)** 에는 슬래시 그대로 전송(경로 아님 → `%2F` 무관).
- ⚠️ 게이트웨이 `%2F` 차단은 mock/Playwright(게이트웨이 미경유)가 못 잡음 → 경로 파라미터에 번호 쓰는 신규 화면은 Docker 실 QA 필수([[feedback_no_fake_data_ever]]). D2 FE-BUG-1(병합 모달이 슬래시 주문번호를 그대로 경로에 → 400)이 실 QA에서만 검출됨.

관련: [[project_order_slip_conversion]], [[feedback_no_fake_data_ever]], [[project_local_stack_qa_gotchas]].
