---
name: feedback_accounting_enrichment_failclosed_policy
description: 표시명(거래처 이름) enrichment 조회가 일시장애(UNAVAILABLE)일 때 — read 리포트=502 fail-closed, write/detail=공란 성사(롤백 금지). 회계 원장 무결성 정책 (2026-07-24 개발책임자 결정, PR #924).
metadata:
  type: feedback
---

# 회계 enrichment 실패 처리 정책 (2026-07-24 개발책임자 결정)

partner-service 등 외부 조회로 **표시명(거래처 이름)을 붙이는 enrichment** 가 일시장애(UNAVAILABLE = 5xx/timeout)일 때, 그 실패를 어떻게 다룰지는 **경로 성격**으로 갈린다. #831/PR #924 에서 개발책임자가 확정.

## 규칙

- **read 리포트 = 502 fail-closed** — 파트너 신원이 **곧 행의 의미**인 조회(에이징·매출집계·원장·자금현황·미수미지급 목록 등). 장애 시 전 거래처가 "(미조회)"/"-" 로 붕괴한 리포트를 HTTP 200 으로 위장하면 사용자가 오데이터를 신뢰한다 → `PARTNER_IDENTITY_LOOKUP_UNAVAILABLE`(502) throw.
- **write·detail = 공란 성사(롤백 금지)** — 표시명이 **부수 정보**인 오퍼레이션(저널 생성/게시/역분개·입금보고서 확정/취소·전표 상세 조회). write 자체는 `partnerId` 를 요청에서 저장하므로 **partner-service 불필요** — 이름 하나 못 가져온다고 회계 원장 write 가 롤백되면 안 된다. 표시명은 공란/미조회로 두고 오퍼레이션은 성사.

**Why**: 무음 붕괴(장애를 200 으로 위장)를 없애려고 공유 lookup 을 무차별 throw 로 바꾸면, 표시명이 부수인 write 경로까지 502+@Transactional 롤백으로 막혀 **더 나쁜 결함**(저널 생성 실패·상세 조회 시 금액도 못 봄)이 된다 — "위치를 옮기는 fix" 함정. #924 근본 fix 가 이걸 냈고 재수렴이 CI RED(`TaxInvoiceControllerIT` 실 client IT)로 실증.

**How to apply**: enrichment lookup 을 fail-closed 로 바꿀 땐 소비처를 **read(신원=의미) vs write/detail(표시명=부수)** 로 분류하라. read 만 throw, write/detail 은 공란 fallback. 회계 원장·감사 write 거동 변경은 [[feedback_integrity_domain_policy_preconfirm]] 상 **개발책임자 선확인 대상**이다. ⚠️ write IT 가 외부 client 를 `@MockBean` 하면 실 502 를 못 잡는 false-green — 실 client 를 타는 IT(`@MockBean` 안 함)로 검증해야 과잉 502 가 드러난다.
