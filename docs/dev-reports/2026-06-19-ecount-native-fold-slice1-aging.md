# dev-report: 이카운트 네이티브 편입 슬1 — 잔액 스냅샷 silo 폐기

> 2026-06-19 · PR #518 · 브랜치 `feat/ecount-native-fold-slice1`
> 에픽: 이카운트 이관 자료 네이티브 편입 + "회계 관리자(MIG-14)" silo 폐기
> spec `docs/superpowers/specs/2026-06-19-ecount-native-fold.md` · 정찰 `docs/research/2026-06-19-ecount-native-fold-recon.md`

## 1. 목표
이관 silo 화면 **"회계 관리자 ▸ 잔액 스냅샷"**(page-code `ecount.mig14.aging-snapshot`)을 완전 폐기한다. 거래처 잔액의 SoT 는 `journals`(POSTED)이며, 네이티브 보고서 **`/accounting/reports/partner-aging`**(`PartnerAgingController` — 110 외상매출금 / 201 외상매입금 직접 집계)가 동일 데이터를 제공한다. silo 는 MV `partner_aging_snapshot` 파생 **중복 화면**일 뿐이므로 폐기하고 page-code 를 시스템 전체에서 제거한다.

## 2. 변경 manifest
### FE (clients/desktop)
- `AppLayout.tsx`: `잔액 스냅샷` 메뉴 + `showAccountingAdminAging` 게이트 제거(그룹 OR-체인 정합 유지)
- `routes/index.tsx`: `/accounting/admin/aging-snapshot` route + import 제거
- `PartnerAgingSnapshotPage.tsx` 삭제(263줄)
- `api/accountingAdminApi.ts`: `listPartnerAgingSnapshots`/`refreshPartnerAgingSnapshot` + 타입 3종 제거
- `PermissionMatrixPage.tsx`·`permissionsApi.ts`·`mock.ts`: page-code 참조/seed 제거(MIG-14 4→3 화면)
- Playwright `mig-14-aging-snapshot-admin.spec.ts` 삭제, `pagecodes.json`·`menu-5category` 참조 제거

### BE (accounting-service)
- `AccountingAdminQueryController`: `GET /accounting/aging-snapshot` + AGING 상수·헬퍼 제거
- `Mig9CashJournalController`: `POST /admin/accounting/aging-snapshot/refresh` 제거
- `AccountingAdminQueryService.listAgingSnapshot` + 상수 제거, DTO `PartnerAgingSnapshotResponse`/`AgingSnapshotRefreshResult` 삭제, IT/ServiceTest aging 케이스 제거
- **유지(LINEAGE)**: MV `partner_aging_snapshot` DDL, `Mig9AgingSnapshotRefreshService`(EcountReimportService 재import wiring) — cutover 후 물리 제거(D3)

### BE (auth-service)
- `PageCode.ECOUNT_MIG14_AGING_SNAPSHOT` enum 제거 + `PageCodeTest` `isValid(...).isFalse()` 박제
- **V59**: page-code 를 권한 모델 5개 테이블 전체에서 제거 — `role_page_permissions`(hard delete, 레거시) + `role_page_permission_templates`/`account_page_permissions`/`group_page_permissions`/`account_permission_overrides`(soft delete, 부분 unique 정합)

## 3. 듀얼 모델 리뷰 (라운드1)
- **Opus 5-agent + Codex 5-agent 가 독립적으로 V59 불완전(P2) 동시 적발**: page-code 가 V39 권한모델 개편으로 `role_page_permissions`(구·DEPRECATED) → `role_page_permission_templates`/`account_page_permissions`, V42/V43 → `group_page_permissions`(현 enforcement 진실원, `EffectivePermissionMaterializer` 소비)로 전파됨. 초안 V59 는 구 테이블만 삭제 → orphan grant 재materialize 위험. **fix: 5개 테이블 전체 정리.** (Opus 의 적대적 검증이 Codex 제안 fix 가 누락한 `group_page_permissions` 를 추가 적발 — dual-model 교차 가치.)
- **개발책임자 라이브 QA 단독 적발(P1급, 하드룰)**: 네이티브 partner-aging 보고서의 거래처코드 컬럼에 **UUID 노출**. 근본 원인 = eCount 이관 journal 의 `partner_id`(합성 v3 UUID)가 partner-service 와 미정합 → `PartnerLookupClient.findByPartnerId` 404 → `PartnerAgingService` fail-soft fallback 이 `partnerId.toString()` 노출. **fix: fallback 을 "미등록"으로 교체 + `PartnerAgingLine.partnerId` payload 제외(null).** ([[uuid-no-user-visibility]] 준수)
- **P3 docs drift**: README×3 + cutover guide 의 제거 endpoint stale 참조 → 본 슬라이스 doc-sync 에서 갱신.

## 4. 검증
- `auth-service` + `accounting-service` `compileTestJava` **BUILD SUCCESSFUL**
- `PageCodeTest` + `AccountingAdminQueryServiceTest` + `PartnerAgingServiceTest`(UUID 비노출 회귀 단언 추가) **green**
- desktop `npm run typecheck` + `vitest` 97 green, **mock suite 517 pass**(estimate-version-history 1건은 선재 flaky — 본 변경 견적 파일 0건, main 동일 코드)
- **V59 실 auth_db 트랜잭션 probe**: aging active 7/7/12/5/0 → 적용 후 5개 테이블 전부 **0**, 타 mig14(cash/order/ledger) 36 active **무손상**, ROLLBACK 원복
- **Docker 실QA**(실 게이트웨이 :8080 + dev_master, accounting-service 재빌드): silo 메뉴 미노출/형제 유지, 네이티브 partner-aging 도달·렌더(거래처코드='미등록' UUID 미노출), 구 route 미렌더 — `docs/qa/ecount-fold-slice1/T1~T3.png`

## 5. 후속 (별도 슬라이스)
- **G2 거래처 배선**: eCount 이관 journal `partner_id` ↔ partner-service 거래처 신원 정합(backfill/reconcile)으로 partner-aging·원장 등 전 journal-거래처 화면에 **실 사업자번호** 표시. 본 슬라이스의 "미등록" 가드는 하드룰 즉시 준수용 stopgap.
- cutover(Phase 11) 후: MV `partner_aging_snapshot` 물리 제거(D3), 원장 대조/운영 대시보드 격리(슬4).
