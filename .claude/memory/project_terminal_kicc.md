---
name: project_terminal_kicc
description: 단말기(카드 결제 단말) 승인 = KICC 확정 — 향후 결제/단말 VAN 연동 기준
metadata:
  type: project
---

2026-07-01 개발책임자 확정: **단말기 승인 = KICC**.

향후 결제/카드 단말 연동(POS·VAN) 진행 시 **KICC**(한국정보통신) 기준으로 설계·연동한다. 카드 단말 승인 경로가 KICC VAN 으로 확정됨.

- 아직 구현 착수 전(결정만 박제). 결제/단말 연동 에픽 진입 시 KICC 규격·API 리서치부터.
- 관련: 전자세금계산서 ASP(비즈니스온/스마트빌)·법인계좌 연동 리서치([[project_external_integration_research]])와 별개 트랙(카드 단말 결제).
