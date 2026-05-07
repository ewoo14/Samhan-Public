# Phase 9 — 회고 보고서

본 문서는 Phase 9 (잔여 도메인 — partner / groupware / notification / dashboard) 5 슬라이스 (W1~W5) 의 산출, 결정, 가드, 학습을 종합한다. Phase 10 진입 plan (`docs/migration/phase10/M-PHASE-10-readiness.md`) 과 짝을 이루는 회고 문서.

---

## 1. Phase 9 요약 (5 슬라이스 W1~W5)

| W | 산출 | PR | 머지 commit | 핵심 |
|---|---|---|---|---|
| W1 | partner-service skeleton (port 8095) | #91 | (W1 머지) | 거래처 마스터 + ServiceDiscoveryClient 첫 소비자 + M5 의존성 해소 endpoint |
| W2 | groupware-service skeleton (port 8092) | #92 | `003800f` | 결재선/메신저/일정 + UserClient + LazyInitializationException 회고 |
| W3 | notification-service skeleton (port 8093) | #93 | `199d88e` | 3 channel adapter (FCM/SES placeholder/Aligo) + UserClient bulk verify (W2 BE backlog #4 채택) + DevOps backlog #11/#12 흡수 |
| W4 | dashboard-service skeleton (port 8094) | #94 | `b2f38ea` | KPI/실시간재고/매출 + 2 materialized view + 4 client + W3 backlog 5건 흡수 + 사용자 가드 (fix 후속 PR/Phase 위임 금지) 적용 = backlog 13건 본 PR 채택 + slip-service 시간 의존 회귀 정공법 fix |
| W5 | 회고 + Phase 10 진입 plan + 잔존 backlog 1건 흡수 | (본 PR) | TBD | partner-service findByCodes bulk endpoint + dashboard-service PartnerCodeResolver bulk 전환 + Phase 9 종합 |

총 4 신규 service (partner / groupware / notification / dashboard) + 1 shared module (user-client-abstraction) + 회고 + Phase 10 진입 plan.

---

## 2. 산출 통계

| 영역 | W1 | W2 | W3 | W4 | W5 | 누적 |
|---|---|---|---|---|---|---|
| 신규 service | 1 | 1 | 1 | 1 | 0 | 4 |
| 신규 shared module | 0 | 0 | 0 | 1 (user-client-abstraction) | 0 | 1 |
| Flyway V1 (init) | 1 | 1 | 1 | 1 | 0 | 4 |
| Flyway V2 (post-init) | 0 | 0 | 0 | 1 (shedlock) | 0 | 1 |
| Materialized view | 0 | 0 | 0 | 2 | 0 | 2 |
| ServiceDiscoveryClient 소비자 (누적) | 1 | 2 | 3 | 4 | 4 (보존) | 4 |
| 외부 client (신규) | 0 | 1 (UserClient) | 1 (UserClient bulk) | 4 (Inventory/Accounting/PartnerOrder/Partner) | +1 method (PartnerClient.findByCodes) | - |
| dev-report | 1 | 1 | 1 | 1 | 1 | 5 |
| QA 캡처 PNG | 3 | 3 | 3 | 3 | 3 | 15 |
| DECISIONS D-P9-NN | 02→05 | 06→08 | 09→11 | 12→15 | 16→20 | 19 (D-P9-02~20) |
| backlog 채택 (본 PR) | 0 | 1 (W2 BE #4 → W3) | 5 (W2 → W3) + 5 (W3 → W4) | 13 (W3 → W4 + 사용자 가드 적용) | 1 (W4 → W5 BE 의견 3) | 25 |

> **누적 결정 D-P9-02 ~ D-P9-20**: 19건. D-P9-01 (Phase 9 4 service 포트 확정) 은 Phase 8 마무리 PR (#90) 시점 결정.

---

## 3. 핵심 결정 (D-P9 시리즈 19건)

| ID | 결정 | W | 출처 |
|---|---|---|---|
| D-P9-02 | partner-service skeleton (port 8095, M5 의존성 해소) | W1 | PR #91 |
| D-P9-03 | partner-service W1 산출물 매트릭스 | W1 | PR #91 |
| D-P9-04 | slip-service M5 client 구현 = W5 또는 Phase 10 cutover 위임 | W1 | PR #91 |
| D-P9-05 | ServiceDiscoveryClient `samhan.discovery.provider=eureka` default + Phase 10 aws-cloud-map 토글 | W1 | PR #91 |
| D-P9-06 | groupware-service 도메인 (결재선/메신저/일정) | W2 | PR #92 |
| D-P9-07 | 결재선 chain 모델 = ApprovalLine + ApprovalStep + 5상태 ApprovalStatus | W2 | PR #92 |
| D-P9-08 | ServiceDiscoveryClient 두 번째 소비자 = groupware-service | W2 | PR #92 |
| D-P9-09 | notification-service 도메인 + 3 channel adapter | W3 | PR #93 |
| D-P9-10 | Aligo SMS 흡수 + FCM/SES placeholder | W3 | PR #93 |
| D-P9-11 | UserClient bulk verify + Caffeine TTL 60s (BE backlog #4 채택) | W3 | PR #93 |
| D-P9-12 | Caffeine 일관 + Redis 토글 약속 (`samhan.cache.provider`) | W4 | PR #94 |
| D-P9-13 | materialized view 5분 CONCURRENTLY REFRESH | W4 | PR #94 |
| D-P9-14 | 4 외부 client + ServiceDiscoveryClient 네 번째 소비자 | W4 | PR #94 |
| D-P9-15 | shared:user-client-abstraction 통합 (W3 BE backlog #1 채택) | W4 | PR #94 |
| D-P9-16 | partner-service findByCodes bulk endpoint + dashboard PartnerCodeResolver bulk 전환 (W4 BE 의견 3 채택) | W5 | 본 PR |
| D-P9-17 | slip-service 시간 의존 design fix (LocalDate.now()) — main 도 영향 받았을 회귀 사전 예방 | W4+W5 | PR #94 후속 fix `cde6db9` |
| D-P9-18 | 사용자 가드 적용 (`feedback_integrated_pr_pattern.md` § fix 후속 PR/Phase 위임 금지) | W4+W5 | PR #94 회고 |
| D-P9-19 | Phase 10 진입 준비 완료 — AWS migration cutover plan 채택 | W5 | 본 PR |
| D-P9-20 | Phase 9 회고 종합 + Phase 10 시점 결정 | W5 | 본 PR |

---

## 4. 누적 backlog 채택 결과

| 시점 | backlog 식별 | 채택 | 잔존 | 위임 |
|---|---|---|---|---|
| W2 → W3 | 13건 | 1건 (BE #4 UserClient bulk verify) | 12건 후속 위임 | 12 |
| W3 → W4 | 15건 | 5건 (BE #1 abstraction / Designer #1 #2 / DevOps #4 / FE rename) | 10건 후속 위임 | 10 |
| W4 → W4 (사용자 가드 적용 후) | 12건 reviewer 식별 | 11건 본 PR 채택 + #12 (BE 의견 3) W5 위임 + slip-service 시간 의존 1건 추가 fix | 1건 (BE 의견 3) | 1 |
| W5 (본 PR) | 1건 잔존 | 1건 채택 (BE 의견 3 → D-P9-16) | 0 | 0 |

**핵심 학습**: W4 부터 사용자 가드 (`feedback_integrated_pr_pattern.md` § fix 후속 PR/Phase 위임 금지) 가 정착하여 reviewer 식별 fix 가 본 PR 일괄 채택. backlog 누적 → Phase 10 부담 패턴이 W5 시점 1건 잔존으로 축소.

---

## 5. 핵심 회고 (성공 + 학습)

### 5-1. 성공

1. **사용자 가드 적용 후 backlog 누적 0 패턴 정착** (W4 부터): reviewer 식별 fix 모두 본 PR 채택, Phase 10/W5 위임 회피.
2. **5 reviewer + 종합 TM 토론 패턴 일관 적용** (W1~W4): 평균 4.6 commit / 평균 30+ file 변경 per PR. 단편 PR / 단독 PR 발행 0건.
3. **shared:user-client-abstraction 모듈** (W4 신규): notification + groupware + dashboard 3 service delegate 패턴 통합. 회귀 0 (12 + 16 단위 + 21 IT case 모두 PASS 유지).
4. **materialized view + ShedLock multi-instance race 가드** (W4): production 진입 안전성 사전 확보. Flyway V2 (shedlock 테이블) + `@SchedulerLock` 패턴.
5. **slip-service 시간 의존 회귀 사전 발견 + 정공법 fix** (W4 후속 `cde6db9`): main 도 동일하게 영향 받았을 패턴을 본 PR 가 사전 예방. 6 file × `LocalDate.of(2026, 5, 5)` → `LocalDate.now()` 동적 값.
6. **D-P9-01 cascade**: partner-service 8095 → migration-service 8096 (Phase 10 신규) 충돌 회피 cascade — 포트 결정 사전 정렬.
7. **UUID 비공개 가드 (Q-W4-2)**: dashboard-service `salesAggregate` 의 입력 시그니처 `UUID partnerId` → `String partnerCode` 전환 + service-side resolve. `feedback_uuid_no_user_visibility.md` 일관.

### 5-2. 학습 / 개선

1. **W2 LazyInitializationException** (`Schedule.participants` lazy + DTO 변환): JOIN FETCH + distinct 정공법 fix → 다른 service lazy 일관 검토 패턴 정착 (W4 까지 일관 적용).
2. **W3 raw URL pin 시점** (`380eb66` → CDN 404 → 새 HEAD SHA re-pin): `feedback_pr_qa_screenshots.md` § "강화 — raw URL pin 시점" 가드 신설 + W4 일관 적용.
3. **W4 fix 후속 PR/Phase 위임 누적** (12건 backlog → W5 일괄 처리 부담): `feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지" 가드 신설 (사용자 명시) → W4+W5 일관 적용.
4. **임시 브랜치 push 패턴 회피**: W2 종합 TM `tm-pr92-work → push origin HEAD:feature/...` force-push 효과 dangling commit 발생. 정공법 PR 브랜치 직접 작업 가드 정착.
5. **DECISIONS D-P9 시리즈 19건**: 1 슬라이스 = 3~5 결정 평균. Phase 10 진입 시점에 결정 종합 매트릭스 (본 § 3) 가 plan 의 1차 참조.
6. **reviewer 권고 매트릭스 누적 + 사용자 가드 적용 → 본 PR 채택**: W4 시점에 12건 매트릭스 식별 → 11건 본 PR 채택 = 패턴 정착.

---

## 6. Phase 10 진입 준비 상태

| 항목 | 준비도 | 비고 |
|---|---|---|
| 14 service skeleton | OK | Phase 0~9 완료, 14 + migration (8096) 예정 |
| ServiceDiscoveryClient 추상화 | OK | 4 service 적용 (partner/groupware/notification/dashboard), Phase 10 cutover 시 `aws-cloud-map` toggle 가능 |
| Caffeine vs Redis | OK | D-P9-12 결정, `samhan.cache.provider=caffeine\|redis` toggle 보유 |
| Materialized view + ShedLock | OK | multi-instance race 가드 (D-P9-13 + W4 후속 fix `445a1a0`) |
| Secrets Manager spec | 대기 | Phase 8 spec 보유, Phase 10 cutover 시점 lambda 배포 |
| AWS RDS 호환 | OK | Postgres standard SQL + JSONB + partial unique index 일관 (Phase 8 22 file 검증) |
| 12-factor + chained-default 환경변수 | OK | Phase 8 D-P8-07 일관, 14 service env-template 보유 |
| QA 캡처 + raw URL HEAD 가드 | OK | Phase 9 회고로 강화 (W3 raw URL 학습) |
| 사용자 가드 (fix 본 PR 채택) | OK | W4 부터 일관 적용 |
| partner findByCodes bulk endpoint | OK | W5 본 PR 신규 (D-P9-16) — fan-out 직렬 RPC 회피 backing |

---

## 7. Phase 10 진입 plan 요약

(상세는 `docs/migration/phase10/M-PHASE-10-readiness.md` 참조)

1. **AWS Secrets Manager 도입** — `samhan.secrets.provider` toggle (Phase 8 rotation lambda spec → 실 배포)
2. **Discovery provider toggle = aws-cloud-map** — `shared:discovery-abstraction` 의 placeholder impl 활성
3. **Cache provider toggle = redis** — Caffeine → Redis 전환 (multi-instance scaling 시점)
4. **AWS RDS migration** — 현재 PostgreSQL → Aurora PostgreSQL (Flyway baseline 자동 적용)
5. **ShedLock cluster** — single-instance 가정 → multi-instance (W4 도입 ShedLock 5.13.0 기반)
6. **Resilience4j (timeout / circuit breaker / retry)** — 4 client + Aligo / FCM / SES adapter
7. **Cutover dry-run 3단계** — staging RDS + ALB + Route 53 (M-AWS-MIGRATION-DRY-RUN.md 14 section)
8. **Production 진입** — DNS 8 subdomain 점진 cutover + 14 service blue-green

---

## 8. 잔존 backlog (본 PR 흡수 1건 + Phase 10 위임 N건)

### 8-1. 본 PR 채택 (D-P9-16)

- **partner-service findByCodes bulk endpoint** — `POST /internal/partners/find-by-codes` 신규
- **dashboard-service PartnerCodeResolver bulk 전환** — `resolveAll(List<String>)` 신규 (cache hit/miss 분리 + miss 만 1회 RPC)
- IT 4건 (정상 / 빈 / 일부 미존재 / 토큰 누락) + 단위 4건

### 8-2. Phase 10 위임

- ServiceDiscoveryClient `aws-cloud-map` 토글 활성
- Caffeine → Redis 전환 (multi-instance scaling)
- Inventory / Accounting / PartnerOrder Internal API 응답 파싱 + DTO 매핑 (현재 skeleton-mode true)
- KPI 산출 batch job (Spring Batch / Quartz)
- Dashboard 화면 design-system Chart / Sparkline 컴포넌트
- Resilience4j 4 client + adapter (Aligo / FCM / SES)
- Materialized view 성능 모니터링 (Micrometer + REFRESH 시간 metric)
- W3 BE backlog #2 (UserClient.verifyBulk fail-fast 토글) — 현재 properties 만 보유
- W3 BE backlog #3 (NotificationGatewayResult 자동 재시도 큐)
- W3 DevOps #6 / #7 / #10 (Resilience4j / FCM secrets / Micrometer)
- W3 QA #11/#12/#13 (재시도 한도 / payload size / fail-mode IT)

---

## 9. 관련 PR + 문서

### Phase 9 PR

- W1 PR #91 — partner-service skeleton
- W2 PR #92 — groupware-service skeleton
- W3 PR #93 — notification-service skeleton
- W4 PR #94 — dashboard-service skeleton (+ slip-service 시간 의존 fix)
- W5 본 PR — 회고 + Phase 10 plan + 잔존 backlog 1건 흡수

### dev-report

- `docs/dev-reports/phase9-step-1-partner-service.md`
- `docs/dev-reports/phase9-step-2-groupware-service.md`
- `docs/dev-reports/phase9-step-3-notification-service.md`
- `docs/dev-reports/phase9-step-4-dashboard-service.md`
- `docs/dev-reports/phase9-step-5-retrospective.md` (본 PR)

### plan / readiness

- `docs/migration/phase9/M-PHASE-9-readiness.md`
- `docs/migration/phase10/M-PHASE-10-readiness.md` (본 PR 신규)
- `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md`

### DECISIONS

- `migration/decisions/DECISIONS.md` D-P9-02 ~ D-P9-20 (19건)

### service README

- `services/partner-service/README.md` (W5 findByCodes endpoint 섹션 추가)
- `services/groupware-service/README.md`
- `services/notification-service/README.md`
- `services/dashboard-service/README.md` (W5 PartnerCodeResolver bulk 섹션 추가)

---

## 10. 마무리 메시지

Phase 9 는 **잔여 도메인 5 슬라이스를 모두 통합 PR 패턴으로 발행**하면서, **사용자 가드 (fix 본 PR 채택) 가 W4 부터 정착**한 회고 가능한 phase. 5 reviewer (BE/FE/Designer/QA/DevOps) 토론 종합 + TM/PM 승인 체인 + 한국어 docs + QA 캡처 가드 + raw URL HEAD pin 가드가 일관 적용되었다.

Phase 10 cutover 의 4 큰 변화 (AWS Secrets Manager / Discovery / Cache / RDS) 는 모두 Phase 8/9 시점에 추상화로 사전 흡수되어 있어 코드 변경이 1줄 ~ 1 모듈 수준이다. AWS account + Aurora + ALB + Route 53 인프라 준비 시점에 D-P9-19 약속 일관 진행한다.
