---
name: jpa-joinfetch-cartesian-dedup
description: JPA JOIN FETCH 컬렉션은 root가 부모당 다행이면 카르테시안 중복 — getChildren() 소비 시 id-distinct 필수, 라이브 QA 다행 데이터로만 노출
metadata:
  type: feedback
---

JPA `@Query` 에서 컬렉션을 `JOIN FETCH` 하면서 root 엔티티가 **한 부모당 여러 행**을 반환하면, fetch된 컬렉션이 **카르테시안 곱으로 중복**된다. `SELECT DISTINCT l` 은 **root(l)만** de-dup하고 fetch된 컬렉션(`j.lines`)은 중복 제거하지 않는다 → `parent.getChildren()` 이 각 자식을 (root 행 수)배로 반환.

**Why:** 2026-06-23 회계 슬B(현금흐름 입출금내역). `findPostedCashEquivalentLines` = `SELECT DISTINCT l FROM JournalLine l JOIN FETCH l.journal j LEFT JOIN FETCH j.lines WHERE l.accountCode IN :cashCodes`. 한 분개에 현금성 라인이 2개(101 차변 + 102 대변)면 root `l` 이 2행 → `j.lines` 컬렉션이 각 라인을 2번 보유. 상대계정 분배 로직이 110(외상매출)을 [110,110]로 보아 counterTotal 2배 → `min` cap 무력화 → 110에 7M 대신 10M 이중분배 → reconciled=False(3M 과대). **IT(단일 현금성 라인 픽스처라 카르테시안 미발생)·Opus R1·Codex R2·Opus R3·바이트코드 분석이 전부 통과**, **per-round 라이브 Docker 실QA(다중 현금성 라인 실 분개)만 단독 적발**.

**How to apply:**
- 컬렉션을 `JOIN FETCH` 하는 쿼리의 root가 부모당 다행일 수 있으면(다중 매칭 라인/조인), 소비 코드는 **`getChildren()` 을 id 기준 distinct 로 정규화**(`distinctById` 헬퍼) 후 처리. 또는 컬렉션을 `Set` 으로, 또는 fetch 분리(별도 lazy/쿼리), 또는 root가 1행만 나오게 쿼리 재설계.
- **`SELECT DISTINCT` 가 fetch 컬렉션까지 de-dup한다고 가정 금지** — root 엔티티만이다.
- **테스트 함정**: 단일 매칭-행 픽스처 IT 는 카르테시안을 못 만든다. 부모당 다행(다중 현금성 라인 등) 케이스를 IT에 반드시 시드. 정적 리뷰·바이트코드도 데이터 분기를 못 보면 false-green.
- **[[per-round-live-qa]] 가치 실증**: 다행 실 데이터 라이브 QA가 IT+정적리뷰가 못 잡는 JPA 집계 버그를 단독 적발. 리뷰 라운드마다 실 데이터(엣지 포함) 라이브 QA 필수.

**참조:** [[per-round-live-qa]] / [[realqa-run-and-false-red]] / [[restclient-contract-test-false-green]] / [[changed-module-full-test-before-push]]
