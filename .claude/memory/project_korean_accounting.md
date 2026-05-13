---
name: Korean Standard Account Code Seed
description: 일반기업회계기준 표준 계정과목 코드 체계 — required as seed data in accounting_db
type: project
---

**Why critical**: 회계 처리에서 매우 중요. 코드 체계는 한국 세무서(국세청)가 인정하는 일반기업회계기준 표준. 시스템 초기 세팅 시 시드 데이터로 전체 삽입 필수. 회사 운영 중 추가/세분화는 가능하나 **코드 체계 자체는 유지**.

**Code ranges**:
- 100s — 자산 (자산: 당좌자산 101-116, 재고자산 120-127, 투자자산 131-133, 유형자산 141-148, 무형자산 151-154, 기타비유동자산 161-163)
- 200s — 부채 (유동부채 201-211, 비유동부채 221-226)
- 300s — 자본 (자본금 301-302, 자본잉여금 311-312, 자본조정 321, 기타포괄손익누계액 331, 이익잉여금 341-343)
- 400s — 매출 수익 (401-405)
- 500s — 매출원가 (상품 501-503, 제품 510-512)
- 800s — 판매비와관리비 (801-822)
- 900s — 영업외수익(901-907) / 영업외비용(951-956) / 법인세비용(991)

**How to apply**: When the Accounting service comes online (Phase 4), include a Flyway/Liquibase seed migration that inserts all of the above. Reference the full table in `docs/PM/project_plan.md` §3.6 — it has the canonical Korean names per code. Do not invent codes; follow the table verbatim.
