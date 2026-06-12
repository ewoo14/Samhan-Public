---
name: 개발책임자 결정은 진행 중 PR에 누적 기록
description: 슬라이스 진행 중 개발책임자가 내린 결정·지시·정정을 그때그때 해당 PR에 리뷰 코멘트로 누적 게시(추적용). 채팅에만 두지 말 것
metadata:
  type: feedback
---
2026-06-12 PR #471(#2) 개발책임자 지시: "이런 내용을 PR에 계속 리뷰로 추가하기 바람."

- 슬라이스 진행 중 개발책임자가 내린 **설계 결정·정책·정정·옵션 축소** 등을 **그때그때 해당 PR 에 리뷰 코멘트("📌 개발책임자 결정 기록")로 누적 게시**. 채팅(나-개발책임자)에만 남기지 말 것.
- AskUserQuestion 답변, 자유 지시(예 "차종 X 제외", "그룹 단위 발송 = 미배차도 동일") 모두 대상. 결정 번호·문제·결정·구현 방침 간결 기록.
- 다모델 리뷰 코멘트(Opus·Codex·Fable5 각 별도)와 별개로, **결정 로그**도 PR 에 남겨 팀/추적 가시화.

**Why:** PR 이 결정의 단일 진실원이 되어 추후 회고·팀 검토 시 "왜 이렇게 했는가" 가 PR 에 박제됨.
**How to apply:** 개발책임자 결정 수신 즉시 해당 PR 에 `gh api .../issues/<pr>/comments` 로 결정 기록 게시. dev-report 박제와 병행. 관련: [[review-posting-and-zero-skip]] [[temp-multimodel-workflow]] [[continuous-docs-sync]].
