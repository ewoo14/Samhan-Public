---
name: 전표 용어 — 슬립 금지
description: 사용자 표시·산문·커밋·답변에서 한글 "슬립"(slip 음차) 금지, "전표" 사용. 영문 코드 식별자는 별개
metadata:
  type: feedback
---
개발책임자 지시(2026-06-11): **한글 "슬립" 표현을 쓰지 말고 "전표"로 통일**.

**Why**: "슬립"은 영어 slip 의 음차 콩글리시. 한국 업무 도메인 정식 용어는 "전표"(출고전표·입고전표·회계전표). 사용자 화면·문서·커밋·PR·Claude 발화 전부 "전표".

**How to apply**:
- 모든 신규 UI 라벨·주석·문서·커밋·PR·답변 = "전표". 한글 "슬립" 금지.
- **영문 코드 식별자(slipId, slipNo, slipNumber, Slip, SlipRepository, SlipDetailModal, slip-service …)는 본 규칙 대상 아님** — Hangul "슬립" 문자열만 해당. 영문 식별자 대량 rename 은 별도 대규모 리팩터(반응적 수행 금지).
- 기존 repo 잔존 한글 "슬립" = **895회 / 250파일**(2026-06-11 집계, 산문·문서·주석 전반). 슬라이스가 건드리는 파일에서 점진 치환 + 신규는 처음부터 전표. 전수 정규화는 배차 에픽(dispatch 파일 rework 동반) + 별도 문서 패스로 분리.
- 관련: [[item-exposure-and-menu-5cat]](전표 노출구분), [[slip-order-number-format]](전표번호 형식 — 내부 파일/필드명은 slip 이나 **표시는 "전표번호"**).
