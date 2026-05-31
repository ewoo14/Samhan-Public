# 🟢 Codex TM 5-Section Cross-Check Review — SP-D4 Cycle 1

**HEAD**: `6d141002`
**PR**: #244
**리뷰어**: Codex 1회 통합 (5-section: BE / FE / Designer / QA / DevOps)
**Claude 리뷰**: [#issuecomment-4477730888](https://github.com/ewoo14/SamhanLogis/pull/244#issuecomment-4477730888) cross-check 완료

## 종합 판정: **FIX 요청** — P0 7건 valid + Codex 추가 발견 3건

### A. Claude 발견 평가 (32건 → valid 29 / partial 1 / 확인 불가 2)

| # | 영역 | 결함 | 평가 | 사유 |
|---|---|---|---|---|
| P0-1 | BE | ArologisAdminController guard 누락 | ✅ valid | `:100/119` 두 곳만 `checkEdit`, `:143~418` 다수 매핑 누락 |
| P0-2 | BE | ProductController 6 write guard 누락 | ✅ valid | `:121~156` PATCH/PUT/DELETE 매핑에 X-User-Role/checkEdit 없음 |
| P0-3 | BE | WarehouseController 4 write guard 누락 | ✅ valid | `:137/185/201/233` write 매핑 누락 |
| P0-4 | Designer | `--color-warning-400` 미등록 | ✅ valid | `PermissionMatrixPage.tsx:723` 참조, `tokens.css:42~48` 400 없음 |
| P0-5 | Designer | success/danger 600 미등록 | ✅ valid | `:562` 참조, `tokens.css:38~57` 600 없음 |
| P0-6 | QA | ArologisAdminPermissionIT MockBean 누락 | ✅ valid | `:42` DynamicPermissionClient 만 |
| P0-7 | DevOps | flywayInfo Gradle task 실행 불가 | ✅ valid | dry-run `:44/:80` task 요구, build.gradle 에 flyway plugin 없음 |
| P1-1 | FE/QA | mock MANAGER admin.users view 불일치 | ✅ valid | mock `:5659` 포함, V10 `:179` FALSE,FALSE |
| P1-2 | FE | mock SALES products.list edit 불일치 | ✅ valid | mock `:5762`, V10 `:241` edit FALSE |
| P1-3 | FE | mock WAREHOUSE sales.vendor-order edit 불일치 | ✅ valid | mock `:5778`, V10 `:98` edit FALSE |
| P1-4 | FE | mock INVENTORY products.list edit 불일치 | ✅ valid | mock `:5786`, V10 `:244` edit FALSE |
| P1-5 | FE | 창고 운영 빈 그룹 헤더 | ✅ valid | AppLayout `:252~253` `_showInventoryStock` 포함, `:805` 그룹 노출 |
| P1-6 | BE | PartnerOrderConfirm SALES RoleGuard 누락 | ✅ valid | `:63` hasAnyRole 에 SALES 없음 |
| P1-7 | BE | PartnerBlock/EditRequest guard 미연결 | ✅ valid | `PartnerPermissionGuard:44/46` code 정의, controllers grep 없음 |
| P1-8 | BE | arologis DynamicPermissionClient qualifier | 🟡 partial | `:46` 기본 Builder, 타 5 서비스 `@Qualifier` 사용 |
| P1-9 | QA | T05/T14 URL 문서 drift | ✅ valid | spec `:348/:354` vs scenario `:113/:306` |
| P1-10 | Designer | `아로지스` 오기 | ✅ valid | `PermissionMatrixPage:197/:255` 오기 (BE PageCode `:176/:179` 는 `아로로지스` 정확) |
| P1-11 | DevOps | dry-run created_by 불일치 | ✅ valid | docs `:103`, V10 rows `'system'` |
| P1-12 | DevOps | permission_guard_denied_total 미구현 | ✅ valid | docs metric 참조, 서비스 코드 0건 |
| P2-1~11 | 각 영역 | (생략) | ✅ valid | 모두 코드 anchoring 확인 |
| P2-12~14 | 기타 | Claude "그 외 잔여" | ⚠️ 확인 불가 | TM 파일 상세 미기재 |

### B. Codex 자체 추가 발견 (Claude 놓침)

**C-P1 (HIGH)** — `ArologisAdminPermissionIT.java:23/:63/:92`
> 테스트명은 `ArologisAdmin` 이지만 실제 검증은 `/admin/arologis/regions` + `arologis.region` 만 수행. P0-1 의 `arologis.admin` 21 endpoint 누락을 잡는 IT 가 없음. **P0-1 fix 후에도 회귀 가드 부재**.

**C-P2 (MEDIUM)** — `sp-d4-deploy-rolling-order.md:62`
> dry-run 문서 외 rolling-order 문서도 `created_by = 'sp-d4-v10'` 사용. V10 seed 가 `'system'` 이므로 배포 검증 쿼리 0건 오판 (DO-2 확장).

**C-P2 (MINOR)** — `PermissionMatrixPage.tsx:468`
> Claude F-D-02 는 toast `:562` 만 지적, 에러 화면도 `--color-danger-600` 사용. 같은 미등록 토큰 (F-D-02 확장).

### C. Cycle 2 fix 권장 우선순위 (Codex 종합)

| 우선순위 | 항목 | 작업량 |
|---|---|---|
| P0 | Arologis/Product/Warehouse guard coverage 보강 | L |
| P0 | ArologisAdminPermissionIT 외부 client MockBean 추가 + arologis.admin 21 endpoint IT 보강 (C-P1 포함) | M |
| P0 | tokens.css `--color-warning-400` / `--color-success-600` / `--color-danger-600` 추가 또는 기존 500/700 으로 대체 | S |
| P0 | Flyway dry-run 가이드 명령 교체 (Spring Boot 기동 로그 + `flyway_schema_history` 조회 방식) | S |
| P1 | mock.ts ↔ V10 4셀 정합 (MANAGER admin.users view, SALES/WAREHOUSE/INVENTORY edit 매트릭스) | M |
| P1 | AppLayout 빈 그룹 제거 (`_showInventoryStock` 제외) | S |
| P1 | PartnerOrderConfirm RoleGuard SALES 정책 결정 (Plan §2 매트릭스 SALES:V/E 일관) | S |
| P1 | partner block/edit-request BE guard 연결 | M |
| P1 | metric 문서 정정 (로그 기반 또는 Counter 구현 결정) | S~M |
| P1 | rolling-order created_by 정정 (C-P2) | S |
| P2 | route RoleGuard 보강, PAGES_WITH_EDIT, QA 문서 URL drift, domain SQL 보완, 로그 태그 [SP-D3]→[SP-D4], 명칭 `아로지스` → `아로로지스` | S |

### D. 종합 판정

- **FIX 요청**
- 머지 차단 사유: P0 7건 valid + Codex 추가 P1 1건 (C-P1)
- cycle 3 안 머지 가능성: **높음** (대부분 범위 누락/문서 정합 → 1회 통합 fix 후 수렴 가능)

### E. 한국어 boundary 결과

- ✅ UUID 비공개: 화면 노출 UUID 신규 위반 0건
- ✅ 한국어 commit: head `6d141002` 본문 한국어 일관
- ❌ 도메인 명칭: `PermissionMatrixPage.tsx:197/:255` `아로지스` → `아로로지스` 정정 필요 (BE PageCode 는 정확)

---

**TM 결정: FIX 요청 → cycle 2 통합 fix → head B 재리뷰**

Codex TM — 2026-05-18
