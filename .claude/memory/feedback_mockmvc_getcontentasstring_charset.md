---
name: mockmvc-getcontentasstring-charset
description: MockMvc IT 에서 한글 JSON 응답 단언 시 getContentAsString()(인자 없음)은 ISO-8859-1 로 읽어 한글 깨짐→false-RED. getContentAsString(StandardCharsets.UTF_8) 사용.
metadata:
  type: feedback
---

2026-06-20 PR #544 회계 자금현황 회고.

## 🪤 함정
MockMvc IT 에서 `result.getResponse().getContentAsString()`(인자 없음)은 응답 Content-Type 의 charset 이 없으면 서블릿 기본 **ISO-8859-1** 로 바이트를 읽는다. 실 JSON 응답은 UTF-8 이므로 **한글(거래처명/계정명 등)이 깨진 문자열**로 변환되고, 이후 `objectMapper.readTree(body)` 파싱 결과의 `.asText()` 비교가 불일치 → AssertionError(false-RED). 기능/서비스/시드는 정상인데 테스트만 실패.
- CI 로그에서 깨진 한글 mojibake(`ì^Y¸ì^C^Aë§¤ì¶...` = "외상매출금")로 나타남 → 인코딩 문제 신호.

## How to apply
한글이 포함된 응답을 문자열로 읽어 단언할 때는 **`getContentAsString(java.nio.charset.StandardCharsets.UTF_8)`** 명시. (영문/숫자만이면 무관하나 한글 도메인은 항상 UTF_8.) 컨트롤러가 `produces=application/json` 에 charset 미지정 시 특히 발생. 관련: [[powershell-utf8-writes]] [[x-user-name-header-charset-mockmvc]] [[realqa-run-and-false-red]].
