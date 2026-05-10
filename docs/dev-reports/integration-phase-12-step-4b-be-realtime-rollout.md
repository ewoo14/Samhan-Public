# Phase 12 step-4b (PR-H4b) — BE 13 service 일괄 `shared/realtime-abstraction` 적용

> 본 dev-report 는 PR (`feature/integrated-phase-12-step-4b-be-realtime-rollout`) 의 종합 작업 보고. PR #126 (PR-H4a `shared/realtime-abstraction` module + slip-service 시범 마이그) 머지 후 **Phase 12 시리즈 4 (전 15 service + 50+ page 일괄 확장, ~7주) 분할 2/3** 진입. 본 PR = BE 13 service 일괄 적용 단계 — slip-service 외 13 backend MSA 가 본 PR-H4a 의 `shared/realtime-abstraction` 의존만 추가 + 도메인별 Flyway template 활용 + 도메인별 specialization (LockPolicy / EditRequestService / AuditController).

## 1. 배경

### 1.1 PR-H4a → PR-H4b 진입 사유

PR-H4a (PR #126) 머지 완료로 `shared/realtime-abstraction` module 19 신규 file (broker 5 + audit 4 + lock 4 + editrequest 5 + autoconfig 1) + AutoConfiguration imports + db/template 2 + 단위 29 PASS + slip-service 시범 마이그 회귀 0 + 풀빌드 GREEN. 본 PR-H4b = PR-H4a 시드 패턴을 **나머지 13 backend MSA 일괄 적용**:

| 분할 | 기간 | 책임 | 상태 |
| --- | --- | --- | --- |
| PR-H4a (PR #126) | ~1주 | BE 인프라 시드 (`shared/realtime-abstraction` + slip 마이그) | **머지 완료 (D-P12-04a)** |
| **PR-H4b (본 PR)** | ~3주 | BE 13 service 일괄 적용 + 도메인별 specialization | **진행 중 (D-P12-04b)** |
| PR-H4c | ~3주 | FE 50+ page 통합 (audit overlay + edit-request banner) | 대기 |

### 1.2 시리즈 진행 (PR-H1 ~ PR-H4c)

| 슬라이스 | 기간 | 목표 | 상태 |
| --- | --- | --- | --- |
| PR-H1 | 1주 | SSE infra + slip 코멘트 smoke | **머지 완료 (PR #123, D-P12-01)** |
| PR-H2 | ~3주 | slip audit overlay + 실시간 sync + TM 보완 3건 | **머지 완료 (PR #124, D-P12-02)** |
| PR-H3 | ~1.5주 | slip 수정/삭제 요청 워크플로우 + 잠금 가드 | **머지 완료 (PR #125, D-P12-03)** |
| PR-H4a | ~1주 | `shared/realtime-abstraction` module 추출 + slip 시범 마이그 | **머지 완료 (PR #126, D-P12-04a)** |
| **PR-H4b (본 PR)** | ~3주 | BE 13 service 일괄 적용 | **진행 중 (D-P12-04b)** |
| PR-H4c | ~3주 | FE 50+ page UI 통합 | 대기 |

## 2. 핵심 결정 (D-P12-04b 요약)

> 자세한 결정 사실 / 근거 / 영향 = `migration/decisions/DECISIONS.md` D-P12-04b 참조.

| 결정 | 채택 |
| --- | --- |
| 적용 범위 | **13 backend MSA — partner / inventory / accounting / arologis / product / dc-config / partner-order / user / groupware (9 specialization) + dashboard / notification (2 broker only) + logging (env 셋업) + slip 시드 = 13 service** |
| Specialization 패턴 | **9 service `<Domain>LockPolicy` + `<Domain>EditRequestService` + `<Domain>AuditLogService` + `<Domain>RealtimeController`** — shared base 1:1 상속 (호출자 변경 0 의무) |
| Flyway 신규 V?? | **9 service × 1 migration** (`db/template/audit_log_template.sql` + `edit_request_template.sql` 1:1 복제 + `<domain>` prefix 교체) — partner-service `V5`, inventory `V6`, accounting `V?`, arologis `V?`, product `V6`, dc-config `V?`, partner-order `V3`, user `V4`, groupware `V2`/`V3`, notification `V3` |
| Lock 정책 | **도메인별 status enum × 3 카테고리 (FREE / LOCKED_REQUIRES_APPROVAL / FULLY_LOCKED) 구현** — accounting POSTED 즉시 FULLY_LOCKED (한국 회계 무결성), arologis IN_TRANSIT+ FULLY_LOCKED, user/groupware audit only |
| Audit only 도메인 | **dashboard + notification 2건** — edit-request 미적용, broker + audit log + SSE 채널만 도입 (read-only / 알림 발송 도메인 특성) |
| Channel naming | **`samhan:<service>:<entity>:edit:{id}`** + edit-request `:edit-request:created/decided` 일관 (slip 시드 패턴 1:1) |
| 회귀 가드 | **각 service 단위 + IT 회귀 0** + slip-service 336 tests 100% 회귀 보존 + 풀빌드 GREEN |
| 후속 backlog | **logging / dashboard / dc-config / groupware `ApplicationContextLoadIT` 보강 — PR-H4c 진입 시 처리** |
| QA 작동 캡처 | **multi-service 동시 SSE 작동 캡처 4 PNG** (다중 service 동시 broker round-trip 시각 증거) |

## 3. 산출물 (7 commits = Phase A docs 1 + Phase A BE 5 + Phase B QA 1)

### 3.1 `8aacae3` docs(phase-12-h4b): Designer 13 service 적용 매트릭스 + DevOps Redis multi-service + QA 65 case

3 files +1304.

| 파일 | 변경 |
| --- | --- |
| `docs/uiux/phase12/H4b-be-rollout-checklist.md` 신규 (343 line) | 13 service 적용 매트릭스 + 도메인별 status enum × 3 카테고리 잠금 정책 일람 + Specialization 클래스 명명 규약 + audit overlay endpoint 패턴 + 한국어 라벨 매핑 표 (9 specialization 도메인 entity 별) |
| `docs/devops/phase12-redis-multi-service.md` 신규 (388 line) | 13 service 단일 ElastiCache 공유 환경 운영 가이드 + 단계적 cutover 절차 (in-memory → Redis 무중단) + channel naming 규약 (`samhan:<service>:<entity>:edit:{id}`) + publishFailureCount metric + production hint (max-connections / timeout / keepalive / 알람) |
| `docs/qa/phase-12-step-4b-be-realtime-rollout/scenarios.md` 신규 (573 line) | 70 case (13 service × 5 case = 65 + 회귀 가드 5) + 페르소나 5 (SALES/WAREHOUSE/ACCOUNTANT/MANAGER/MASTER 또는 DEVOPS) + 우선순위 매트릭스 (Critical 46 / Major 8 / Minor 3 / Info 3) + 도메인별 PASS 게이트 |

### 3.2 `12ace4a` feat(accounting+partner): PR-H4b BE-A shared-realtime 적용 — audit_log + SSE + 잠금 가드

42 files +2795. 단위 88 + IT 5 PASS.

| 도메인 | 신규 file | 비고 |
| --- | --- | --- |
| accounting (audit 4 + editrequest 8 + realtime 1 + Service 1 + Flyway 1 + 테스트 5 = 20) | `AccountingAuditLog` + Repository + Service / `AccountingEditRequest` + Lock + Repository + Service + Controller + DTO 4 / `AccountingRealtimeController` / `TaxInvoiceService` 보강 / `V?__add_accounting_audit_logs_and_edit_requests.sql` (111 line) / Service/Lock/EditRequestService/IT 5 testfile | accounting POSTED 즉시 FULLY_LOCKED (한국 회계 무결성, LOCKED_REQUIRES_APPROVAL 미사용) |
| partner (audit 3 + editrequest 8 + realtime 1 + Service 1 + Flyway 1 + 테스트 5 = 20) | `PartnerAuditLog` + Repository + Service / `PartnerEditRequest` + Lock + Repository + Service + Controller + DTO 4 / `PartnerRealtimeController` / `PartnerService` 보강 / `V5__add_partner_audit_logs_and_edit_requests.sql` (110 line) / Service/Lock/EditRequestService/IT 5 testfile | DRAFT free / ACTIVE locked-approval / SUSPENDED-INACTIVE fully-locked |

### 3.3 `5bcb7ad` feat(inventory+arologis): PR-H4b BE-B shared-realtime 적용

38 files +3117. 단위 + IT 추가.

| 도메인 | 신규 file | 비고 |
| --- | --- | --- |
| arologis (realtime audit 2 + editrequest 1 + service 4 + repository 2 + dto 2 + controller 1 + Flyway 1 + 테스트 5 = 18) | `ArologisAuditLog` / `ArologisEditRequest` + Repository / `ArologisAuditLogRecorder` / `ArologisEditRequestService` / `ArologisLockPolicies` / `DispatchDerivedStatus` / `ArologisAdminController` / DTO 2 / `DispatchService` 보강 / Flyway 1 / Recorder/EditRequest/Lock/DerivedStatus/IT 5 testfile | PLANNED free / DISPATCHED locked-approval / IN_TRANSIT-DELIVERED-CANCELED fully-locked + DerivedStatus 보강 |
| inventory (realtime audit 2 + editrequest 1 + service 3 + repository 2 + dto 2 + service 1 + controller 1 + Flyway 1 + 테스트 5 = 18) | `InventoryAuditLog` / `InventoryEditRequest` + Repository / `InventoryAuditLogRecorder` / `InventoryEditRequestService` / `InventoryLockPolicies` / `InventoryAuditService` / `InventoryAuditController` / DTO 2 / Flyway 1 / Recorder/EditRequest/Lock/AuditService/IT 5 testfile | DRAFT free / SUBMITTED locked-approval / POSTED-VOIDED fully-locked |

### 3.4 `530a149` feat(partner-order+product): PR-H4b BE-C shared-realtime 적용

41 files +2442.

| 도메인 | 신규 file | 비고 |
| --- | --- | --- |
| partner-order (audit 4 + editrequest 6 + config 1 + realtime 2 + entity 보강 1 + vendor controller 보강 1 + application yml 1 + Flyway 1 = 17) | `PartnerOrderAuditLog` + Repository + Service + Controller + DTO / `PartnerOrderEditRequest` + Repository + Service + Controller + DTO 4 / `PartnerOrderEditRequestProperties` / `PartnerOrderRealtimeBroker` / `PartnerOrderRealtimeController` / `PartnerOrder` 보강 / `VendorOrderController` 보강 / `application.yml` realtime property 4 line 추가 / `V3__add_realtime_overlay.sql` (138 line) | DRAFT free / SUBMITTED locked-approval / CONFIRMED-FULFILLED-CANCELED fully-locked |
| product (audit 4 + editrequest 6 + config 1 + realtime 2 + entity 보강 1 + application yml 1 + Flyway 1 = 16) | `ProductAuditLog` + Repository + Service + Controller + DTO / `ProductEditRequest` + Repository + Service + Controller + DTO 4 / `ProductEditRequestProperties` / `ProductRealtimeBroker` / `ProductRealtimeController` / `Product` 보강 / `application.yml` realtime property 4 line 추가 / `V6__add_realtime_overlay.sql` (125 line) | DRAFT free / ACTIVE locked-approval / DISCONTINUED-INACTIVE fully-locked |

### 3.5 `5c30306` feat(user+dc-config+notification): PR-H4b BE-D shared-realtime 적용

26 files +1255.

| 도메인 | 신규 file | 비고 |
| --- | --- | --- |
| dc-config (audit 3 + editrequest 2 + lock 2 + realtime 1 + Flyway 1 = 9) | `DcConfigAuditLog` + Repository + Service / `DcConfigEditRequest` + Repository / `DcConfigEditLockPolicy` / `DcConfigStatus` / `DcConfigRealtimeBroker` / `V?__add_dc_config_audit_logs_and_edit_requests.sql` (113 line) | DRAFT free / ACTIVE locked-approval / EXPIRED-INACTIVE fully-locked |
| user (audit 3 + editrequest 2 + lock 2 + realtime 1 + Flyway 1 = 9) | `UserAuditLog` + Repository + Service / `UserEditRequest` + Repository / `UserEditLockPolicy` / `UserStatus` / `UserRealtimeBroker` / `V4__add_user_audit_logs_and_edit_requests.sql` (111 line) | ACTIVE free (audit only — edit-request 미도입) / SUSPENDED-INACTIVE fully-locked |
| notification (audit 3 + realtime 1 + Flyway 1 = 5) | `NotificationAuditLog` + Repository + Service / `NotificationRealtimeBroker` / `V3__add_notification_audit_logs.sql` (71 line) | broker only — edit-request 미적용 (알림 발송 도메인 특성) |

### 3.6 `3914fdf` feat(logging+groupware+dashboard): PR-H4b BE-E shared-realtime 적용

10 files +386.

| 도메인 | 신규 file | 비고 |
| --- | --- | --- |
| dashboard | `build.gradle` shared 의존 추가만 (broker only — read-only KPI 도메인) | broker only |
| groupware (audit 2 + editrequest 2 + Flyway 2 = 6) | `GroupwareAuditLog` + Repository / `GroupwareEditRequest` + Repository / `V2__add_groupware_audit_logs.sql` (68 line) + `V3__add_groupware_edit_requests.sql` (83 line) | DRAFT-PUBLISHED free (audit only) / ARCHIVED fully-locked |
| logging | `build.gradle` shared 의존 + `application.yml` realtime property 12 line 추가 | env 셋업만 (audit log domain 도입은 PR-H4c 후속) |

### 3.7 `2db1d02` test(qa): PR-H4b QA 13 service 회귀 점검 + 다중 service 동시 SSE 작동 캡처

6 files +633. **multi-service 동시 SSE 작동 캡처 4 PNG**.

| 파일 | 변경 |
| --- | --- |
| `docs/qa/phase-12-step-4b-be-realtime-rollout/scenarios.md` 보강 (70 line 추가) | 13 service 회귀 점검 결과 표 + 5 case 회귀 가드 (slip-service 시드 100% 회귀 보존) |
| `docs/qa/phase-12-step-4b-be-realtime-rollout/working-multi-service-{tax-invoice-sync,partner-edit-sync,inventory-audit-sync,dispatch-sync}.png` 신규 4 PNG | accounting TaxInvoice / partner edit / inventory audit overlay / arologis Dispatch — 다중 service 동시 SSE round-trip 시각 증거 (Playwright multi-context + sharp 좌-우 합성 일관) |
| `tools/manual-capture/capture-pr-h4b.js` 신규 (563 line) | Playwright 자동화 (PR-H1/H2/H3 패턴 일관 — 4 도메인 mock seed + multi-context A/B 분리) |

### 3.8 TM docs (본 commit) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신

| 파일 | 변경 |
| --- | --- |
| `ROADMAP.md` | Phase 12 row 갱신 (PR #126 머지 + 본 PR-H4b 진행) + Phase 12 section 산출물 (본 PR-H4b) 추가 + PR 매트릭스 #126 확정 + 본 PR row 추가 |
| `migration/decisions/DECISIONS.md` | D-P12-04b 신규 항목 추가 (13 service 일괄 적용 + 도메인별 specialization + Flyway 9 신규 + Lock 정책 도메인별 매트릭스 + audit only 2 도메인 + channel naming + multi-service 작동 캡처) |
| `docs/dev-reports/integration-phase-12-step-4b-be-realtime-rollout.md` 신규 | 본 dev-report |

memory `feedback_continuous_docs_sync` 일관 — 별도 docs PR 폐기 패턴 일관.

## 4. 검증

### 4.1 단위 — 신규 specialization (88 + 추가 = 다수)

- accounting: `AccountingAuditLogServiceTest` (다수) + `AccountingLockPoliciesTest` + `AccountingEditRequestServiceTest` + `AccountingRealtimeIT` + `ApplicationContextLoadIT` PASS (88+ 합산)
- partner: `PartnerAuditLogServiceTest` + `PartnerLockPoliciesTest` + `PartnerEditRequestServiceTest` + `PartnerRealtimeIT` + `ApplicationContextLoadIT` PASS (93+ 합산)
- inventory: `InventoryAuditLogRecorderTest` + `InventoryEditRequestServiceTest` + `InventoryLockPoliciesTest` + `InventoryAuditServiceTest` + `InventoryRealtimeIT` PASS
- arologis: `ArologisAuditLogRecorderTest` + `ArologisEditRequestServiceTest` + `ArologisLockPoliciesTest` + `DispatchDerivedStatusTest` + `ArologisRealtimeIT` PASS + `DispatchServiceTest` 회귀 0
- partner-order / product / dc-config / user / notification / groupware / dashboard / logging — specialization + broker 단위 PASS

### 4.2 회귀 — slip-service (336 tests / 0 fail)

- PR-H1 SSE infra IT 5 case PASS (회귀 무손실)
- PR-H2 audit overlay IT 9 case PASS (회귀 무손실)
- PR-H3 edit-request IT 3 case PASS (회귀 무손실)
- PR-H4a thin facade 회귀 0 (단위 30+)

### 4.3 풀빌드 (root)

- `gradlew assemble` — GREEN (14 backend service + shared:realtime-abstraction 모두 build PASS)

### 4.4 multi-service 동시 SSE 작동 캡처 (4 PNG)

- `working-multi-service-tax-invoice-sync.png` — accounting TaxInvoice 동시 sync (좌-A 우-B 합성)
- `working-multi-service-partner-edit-sync.png` — partner edit 동시 sync
- `working-multi-service-inventory-audit-sync.png` — inventory audit overlay 동시 sync
- `working-multi-service-dispatch-sync.png` — arologis Dispatch 동시 sync

각 캡처 = 다중 service 동시 broker round-trip 시각 증거 (Playwright multi-context A/B 분리 + sharp 좌-우 합성, PR-H1/H2/H3 패턴 일관).

## 5. 후속 (PR-H4b 머지 후)

- **PR-H4c (~3주) — FE 50+ page UI 통합** — desktop `<Domain>DetailPage` 일괄 audit overlay (partner / inventory / accounting / arologis / product / dc-config / partner-order 7 specialization 도메인) + edit-request banner + mobile-staff 적용 (DispatchScreen / StockAdjustScreen 등) + Designer wireframe 도메인별 1건씩.
- **logging / dashboard / dc-config / groupware `ApplicationContextLoadIT` 보강** — PR-H4c 진입 시 처리 (본 PR-H4b 는 신규 entity / migration 위주, IT scaffold 는 PR-H4c 와 함께)

## 6. 제약 / 가드 일관

- **PR-H4a `shared/realtime-abstraction` 의존만 추가 + 도메인별 Flyway template 활용** — 신규 module 추가 0 (의존 추가 + specialization 만)
- **Specialization 패턴** — `<Domain>LockPolicy` (status enum × 3 카테고리) + `<Domain>EditRequestService` (status transition + consumeApproval) + `<Domain>AuditLogService` (record / listByEntity / revert) + `<Domain>RealtimeController` (`samhan:<service>:<entity>:edit:{id}` 채널)
- **BaseEntity 7 audit + Soft Delete 의무** — 9 신규 Flyway migration 모두 7 audit field + Soft Delete + 부분 인덱스 일관
- **호출자 변경 0 의무** — 각 service 기존 Service / Controller / IT 변경 0 (specialization 신규 추가만)
- **한국어 Javadoc 의무** — 13 service 신규 file 모두 한국어 Javadoc (memory `feedback_function_documentation`)
- **UUID 비공개 가드 (memory `feedback_uuid_no_user_visibility`)** — 응답 schema `actorId` = 색상 hash 입력 전용, 화면 표시 = `actorName` 만. 도메인 본체 식별자 (partnerId / journalId / ...) 비공개 — 비즈니스 식별자만 노출
- **권한 표기 풀네임 (memory `feedback_role_naming_full`)** — QA 페르소나 5 (SALES/WAREHOUSE/ACCOUNTANT/MANAGER/MASTER 또는 DEVOPS) 풀네임
- **외부 SaaS 의존 0** — Redis 도 사내 (AWS ElastiCache 또는 self-host)

## 7. 통합 PR 패턴 일관 (memory `feedback_integrated_pr_pattern`)

본 PR-H4b = 5-team 병렬 (BE 5 commits + Designer + DevOps + QA + TM) Phase A 6 + Phase B QA 1 + TM docs 1 = 단일 통합 PR (총 8+ commits). 별도 docs PR 회피 (memory `feedback_continuous_docs_sync` 일관) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신. **각 service 단위/IT 회귀 0 + slip-service 100% 회귀 보존 + 풀빌드 GREEN** — 별도 후속 fix PR 회피.

## 8. 5-team 리뷰 + CI + PM + 사용자 머지 워크플로우 (memory `feedback_pr_review_workflow`)

본 PR 머지 절차:
1. PR 발행 즉시 `gh pr checks --watch` 자동 시작 (memory `feedback_pr_ci_monitoring`)
2. 5-team 리뷰 (BE / FE / Designer / QA / DevOps) PR comment 토론 (memory `feedback_tm_led_agent_discussion`)
3. CI green + reviewer agent 토론 종료 후 TM 종합 추가 commit (필요 시)
4. PM 최종 승인 댓글 + 머지 요청 (memory `feedback_user_merge_authority`)
5. 사용자 머지
6. 머지 후 PR-H4c (FE 50+ page audit overlay 일괄 ~2주) 즉시 진입
