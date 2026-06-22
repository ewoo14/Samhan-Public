# 후속 #1 — 권한 page-code 카탈로그 드리프트 해소 + 자동 가드 테스트

> 슬5 capstone 후속(에픽 외). 슬5 가 발견한 "시드된 page-code 가 BE `PageCode` enum 에 누락되는 드리프트"의 **근본원인 해소**: 활성 드리프트 1종 enum 편입 + **시드↔enum 자동 정합 가드 IT** 신설(재발 CI 차단).

## 배경
`GET /auth/admin/permissions/my` MASTER 경로(`allPageActions()` = `PageCode.values()`)가 enum 카탈로그에 의존 → enum 누락 시 카탈로그 불완전. 슬5 sweep 으로 드리프트 4종 분류, 슬5 에서 convert 1종 fix. 본 후속에서 나머지 해소 + 가드.

## 드리프트 해소 (성격별)
| page-code | 성격 | 조치 |
|---|---|---|
| `sales.partner-order.convert` | 활성(convert-to-slip) | 슬5 #567 enum 편입 완료 |
| **`sales.partner-order.revisions`** | **활성**: `partner-order-service` `PartnerOrderRevisionController:152` `@RequirePermission(...RESTORE)` | **`SALES_PARTNER_ORDER_REVISIONS` enum 편입**(본 후속) |
| `ecount.mig14.cash-list` | 의도적 폐기(`PageCodeTest:99-100` isValid=false 단언, 사용처 0) | enum 제외 유지 + **LEGACY_EXCLUDED allowlist** 등재 |
| `ecount.mig14.aging-snapshot` | 동상 | 동상 |

## 자동 가드 — `PageCodeSeedConsistencyIT` (근본원인 해소)
- **Testcontainers Postgres + Flyway 전체 적용 실 DB** 에서 권한 시드 5 테이블(`role_page_permissions`·`role_page_permission_templates`·`group_page_permissions`·`account_page_permissions`·`account_permission_overrides`)의 활성(is_deleted=false) DISTINCT `page_code` 전수 조회.
- 불변식: 각 page_code 가 `PageCode.isValid()` true, 단 `LEGACY_EXCLUDED`(cash-list/aging-snapshot) 제외. 조회 0건이면 실패(테이블 오지정 가드). 실패 메시지에 누락 코드 목록.
- SQL 텍스트 regex 가 아닌 **실 DB 쿼리**라 robust. 신규 시드가 enum 누락 page-code 추가 시 CI fail → 드리프트 재발 차단.

## 검증 (라이브/실측)
- ✅ 가드 IT 통과(revisions enum 편입 + legacy 제외 후 missing=0).
- ✅ **false-green 아님 증명**: revisions enum 임시 제거 → IT **FAILED**(드리프트 적발) → 복원 후 통과. 가드가 실제로 드리프트를 잡음을 실증([[per-round-live-qa]] 해석-검증 교훈).
- ✅ `PageCodeTest`(기존 per-migration seed-sync) 무회귀.

## 잔여 후속
- enum↔**FE 매트릭스(PAGE_GROUPS)** 교차 가드(현 가드는 시드↔enum 축; convert 는 시드(V41)에도 있어 본 가드가 커버하나 FE-only 드리프트는 별도 — 크로스언어 도구 필요).
- arologis Phase B page-code(arologis-desktop 소관).
