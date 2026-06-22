# 슬5 (capstone) — 메뉴 ↔ 권한설정 정합 + 권한설정 동작 검증

> 동적 결재라인 에픽(`2026-06-22-dynamic-approval-line-config-rendering-design.md` §7 슬5)의 **마지막 슬라이스 = 에픽 종료**. 명칭 변경(판매전표)·신규 메뉴(결재라인 설정)·전 전표 결재란이 누적된 후 **메인 데스크톱 권한 체계 정합을 점검**한다. 정적 정합은 이미 완벽 → **라이브 동작 검증 중심**(개발책임자 결정 = 검증 중심 + 발견 갭만 fix).

## 1. 3원 정합 대조표 (메인 데스크톱)

| 소스 | page-code 수 | 소스 위치 |
|---|---|---|
| 좌측 사이드바 메뉴(canAccess 가드) | 60 | `clients/desktop/src/renderer/components/AppLayout.tsx` |
| 권한설정 매트릭스(PAGE_GROUPS) | 179 | `clients/desktop/src/renderer/routes/PermissionMatrixPage.tsx` |
| BE PageCode enum(.code) | 151 | `services/auth-service/.../domain/PageCode.java` |

### 대조 결과
| 검사 | 결과 | 판정 |
|---|---|---|
| (A) 사이드바에 있는데 매트릭스/BE에 **없는** page-code | **0** | ✅ 가드 없는 노출 0 |
| (B) 매트릭스에 있는데 사이드바 링크 **없는** page-code | 119 | ✅ **의도적** — API 액션 권한(slip.transfer.process·tax-invoice.emit-nts 등)·직접 URL 라우트(PermissionGuard)·이중가드. 메뉴 링크 없이 권한만 부여하는 정상 패턴 |
| (C) 매트릭스 page-code 중 BE enum에 **없는** 것 | **1 적발 → fix → 0** | 🔧 **capstone 실 갭 적발+해소**: `sales.partner-order.convert`(A2-4 주문 출고전환)가 V41 Flyway 시드 + FE canAccess(convert CREATE 가드)·매트릭스 등재인데 **BE `PageCode.java` enum 만 누락** → 슬5 에서 `SALES_PARTNER_ORDER_CONVERT` enum 추가 → **C=0**. 런타임은 raw string @RequirePermission + V41 시드로 동작했으나 enum 카탈로그 불완전(정합 위반) 해소 |
| (D) BE enum 중 메인 매트릭스에 **없는** 것 | 6 (arologis Phase B) | ⚠️ **아로로지스-desktop 백오피스 소관**(별도 클라이언트 [[project_arologis_independent]]) — 메인 데스크톱 갭 아님. **Phase B 후속 플래그** |

> 🔎 **메타 발견(capstone)**: 위 C=1 갭이 그동안 슬립한 원인 = **BE PageCode enum ↔ FE 매트릭스 PAGE_GROUPS ↔ Flyway seed 를 자동 대조하는 정합 테스트 부재**(`PageCodeTest` 는 부분 seed-sync만). → **후속 권장**: 세 소스 page-code 집합 자동 대조 가드 테스트 추가(드리프트 재발 방지). 본 슬5 범위 외(별도 슬라이스).

### admin.approval-line-config (신규 결재라인 설정 메뉴) — 4중 정합 ✅
| 체크포인트 | 상태 |
|---|---|
| 사이드바 `AppLayout.tsx` | `dynamicCanAccess('admin.approval-line-config','view')` ✓ |
| 라우트 `routes/index.tsx` | `<PermissionGuard pageCode="admin.approval-line-config">` ✓ |
| 매트릭스 `PermissionMatrixPage.tsx` | PAGE_GROUPS 직원·계정 그룹 ✓ |
| BE `PageCode.java` | `ADMIN_APPROVAL_LINE_CONFIG("admin.approval-line-config","결재라인 설정")` ✓ |

## 2. 라이브 동작 검증 (실 게이트웨이 :8080·실 시드·mock off)

테스트 계정(실 시드): dev_master(MASTER, a0…001) / dev_manager(MANAGER, a0…003) / dev_accountant(ACCOUNTANT, a0…005) / dev_staff(STAFF, b0…00b).

### 2-1. 라우트 가드 (비-MASTER → MASTER 전용) — API + UI
- **API**: dev_manager/dev_staff/dev_accountant 가 `GET /auth/admin/permissions`·`/auth/admin/permission-groups` → 전부 **403** ✓
- **UI**(S5-C): dev_manager 가 `/admin/permission-matrix` 진입 → **대시보드로 redirect**(PermissionGuard 차단) ✓. 캡처 `docs/qa/menu-permission-capstone-s5/03-permission-matrix-manager-blocked.png`.

### 2-2. 권한설정 매트릭스 렌더 + 동작 (MASTER) — S5-A
- `/admin/permission-matrix` → 매트릭스 풀 렌더: **179 page-code × 7 액션**(조회/생성/수정/삭제/복원/엑셀/인쇄) + 위험(삭제·복원) 컬럼 강조 + 계정 선택·템플릿 적용·전체ON/OFF·다른 계정 복사·저장. 캡처 `01-permission-matrix-master.png`.
- 권한 CRUD 엔드포인트 라이브 200: `/auth/admin/permissions`(매트릭스)·`/auth/admin/permissions/my`(effective)·`/auth/admin/permission-groups`.

### 2-3. 메뉴 가시성이 권한을 따름 (MASTER vs MANAGER 사이드바 비교)
- **MASTER**(S5-A): 인사 그룹 = 인사 관리·**권한설정**·권한 일괄 적용·그룹 권한·권한그룹 관리·권한 위임·**결재라인 설정** 전부 노출.
- **MANAGER**(S5-C): 인사 그룹 = 인사 관리·**결재라인 설정**만 노출(권한 관리류 숨김 — system.permission-admin 미보유). **결재라인 설정은 MANAGER도 노출**(admin.approval-line-config 위임 접근 동작 — A2-1 PGC 정책 일치).

### 2-4. 신규 결재라인 설정 메뉴 페이지 렌더 (MASTER) — S5-B
- `/admin/approval-line-config` → 문서 종류 셀렉터·역할·결재자 렌더. 캡처 `02-approval-line-config-master.png`.

## 3. MANAGEMENT_PAGE_CODES 위임 보호
`PageCode.java`: `{system.permission-admin, hr.role-management, admin.permission-groups}` = MASTER 명시 행위로만 grant/revoke(위임받은 비-MASTER 재부여 불가). 2-1 의 403 이 이 보호의 실 동작.

## 4. 결론
- **메인 데스크톱 3원 정합 = clean**(사이드바↔매트릭스↔BE 불일치 0, admin.approval-line-config 4중 정합).
- **권한설정 동작 = 라이브 검증 완료**(매트릭스 렌더·CRUD 엔드포인트·가드 403·메뉴 가시성·신규 메뉴 접근).
- **발견 갭 = 1 적발+해소**(메인 스코프): `sales.partner-order.convert` BE enum 누락 → `SALES_PARTNER_ORDER_CONVERT` 추가(C=0 회복). capstone 검증의 실 가치. 그 외 산출 = 본 dev-report + Playwright real-qa E2E 회귀 스펙 + QA 캡처.
- ⚠️ **후속(에픽 외)**: arologis Phase B 6 page-code(arologis.hr.*·arologis.accounting.*)의 arologis-desktop 매트릭스/메뉴/시드 동기화 — 아로로지스 독립 클라이언트 Phase B 작업으로 분리.

→ **동적 결재라인 에픽 종료**(슬1~5 + 그룹웨어 슬4a~c 완결).
