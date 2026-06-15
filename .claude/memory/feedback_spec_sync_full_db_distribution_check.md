---
name: spec-sync-full-db-distribution-check
description: 사양/시드 sync 회귀는 실 DB 전수 분포 검사로만 잡힘 — IT 단일 픽스처는 탭 간 nuke 상호작용 못 잡음(false-green)
metadata:
  type: feedback
---
2026-06-15 PR #487(사양 후속 #1) 회귀 회고.

`ProductSheetSyncService` 사양 적재를 매핑전용으로 바꾸며 `syncTab` 의 `headerCells` 가드(`isSpecBearing(productCategory) ? toStringRow : null`)를 제거 → 비사양 탭(싱글 구성품/상업멀티 구성/구형)도 `loadSpecsForProduct` 호출 → 사양 보유 제품이 비사양 탭에서 재처리되며 그 탭의 soft-delete(seenKeys=blocklist, 매핑 사양 미포함)가 V17 매핑 사양을 **전부 nuke**. 결과 SINGLE 276/276·COMMERCIAL 251/338 사양 0.

**왜 IT 가 못 잡았나**: `ProductSheetSyncServiceIT` 사양 테스트는 **단일 홈 탭 픽스처**만 검증 → 여러 탭이 같은 제품을 순차 재처리하는 **탭 간 상호작용**을 재현 안 함. gradlew test BUILD SUCCESSFUL(+Windows Testcontainers IT skip)인데도 실 sync 는 파손.

**How to apply**: 사양/시드 sync 변경(매핑·soft-delete·헤더 가드·탭 처리) 후엔 **실 Docker product_db 전수 분포 검사 필수** — 카테고리별 `count(*) FILTER (WHERE n=0)` 으로 0-사양 제품 급증 탐지. 단건 제품 spot-check 만으로 불충분(나는 첫 제품만 보다 놓칠 뻔). 실 시트 재동기화(POST /api/v1/products/admin/sync, SA키 컨테이너 주입 `/etc/samhan/sa-key.json`) → 즉시 분포 query. **연속 sync 금지**(수동 POST + 크론/타임아웃 겹침 → soft-delete vs upsert 경합으로 데이터 손상). 재기동(rowHash 캐시 클리어)→**1회만** 완전 대기→검증. 관련 [[ci-test-filter-false-green]] [[standalone-boot-real-qa]] [[no-fake-data-ever]] [[product-master-registration]].
