# Phase 12 step-1 (PR-H1) — 실시간 협업 SSE infra + slip 코멘트 smoke

> 본 dev-report 는 PR (`feature/integrated-phase-12-step-1-websocket-infra`) 의 종합 작업 보고. PR #122 (운영 검증 인프라) 머지 후 **사용자 결정 옵션 A (Phase 12 실시간 협업 시리즈, 총 ~13주)** 진입의 첫 슬라이스. **Samhan Public 핵심 가치 = "두 사람이 같은 전표 보고 한 명 코멘트 → 다른 사람에게 실시간 반영"** 의 최소 검증 단계 (smoke).

## 1. 배경

### 1.1 Samhan Public 핵심 가치 정의

영업 직원이 거래처에서 견적 / 주문 입력 → 창고 직원이 같은 전표를 보고 출고 라인 확인 / 메모 / 수량 정정 시점에 두 사람이 동시에 한 슬립을 보면서 코멘트로 즉시 의사소통할 수 있어야 함. 외부 메신저 (카카오톡 / Slack) 우회 시 발생하는 두 가지 문제:

- **비동기 누락** — 영업이 카톡으로 "이 라인은 SET 묶음으로 출고해주세요" 보내도 창고 담당이 메시지 미확인 시점 출고 → 출고 후 정정 불가능 (전자서명 봉인)
- **컨텍스트 분실** — 슬립 화면 컨텍스트 외부에서 메시지 흐름 → 어느 슬립의 어느 라인 이야기인지 추적 비용 + 회계 감사 시점 거래 사실 보존 불가능

→ **slip 단위 코멘트 + 실시간 broadcast** 가 위 두 문제를 동시에 해소.

### 1.2 시리즈 전체 의도 (PR-H1 ~ PR-H4)

| 슬라이스 | 기간 | 목표 |
| --- | --- | --- |
| **PR-H1 (본 PR)** | 1주 | SSE infra + slip 코멘트 smoke (최소 검증) |
| PR-H2 | ~3주 | slip audit overlay + 실시간 sync (라이프사이클 10단계 변경 broadcast + 사용자별 색상) |
| PR-H3 | ~1.5주 | 권한 / 수락 / 거절 워크플로우 (영업→창고→기사 인계) |
| PR-H4 | ~7주 | 전 15 service 확장 + `shared/realtime` module + Redis Pub/Sub 분기 |

본 PR-H1 = 인프라 + 단일 slip 코멘트 smoke. PR-H2 ~ PR-H4 의 의존 시드를 본 PR 에서 제공:
- `userIdToColor` HSL hash util (PR-H2 audit overlay 의존)
- `SlipRealtimeBroker` 단일 노드 in-memory 패턴 (PR-H4 시점 Redis Pub/Sub 분기 진입)
- gateway `httpclient.response-timeout: 600s` + heartbeat 30s 패턴 (PR-H2 ~ PR-H4 모두 일관)

## 2. 핵심 결정 (D-P12-01 요약)

> 자세한 결정 사실 / 근거 / 영향 = `migration/decisions/DECISIONS.md` D-P12-01 참조.

| 결정 | 채택 |
| --- | --- |
| 실시간 통신 표준 | **SSE (Spring `SseEmitter`)** — WebSocket / STOMP / 외부 SaaS 모두 회피 |
| broker 구현 | **단일 노드 in-memory** `Map<UUID, CopyOnWriteArrayList<SseEmitter>>` (다중 노드 진입 시 PR-H4 Redis Pub/Sub 분기) |
| 인증 | **JWT 헤더** (`Authorization: Bearer <token>`) — fetch+ReadableStream polyfill (desktop) + `react-native-sse` (mobile-staff) |
| schema | **`slip_comments` 신규 + Flyway V17** (BaseEntity 7 audit + Soft Delete + 부분 인덱스) |
| heartbeat | **30s** (`samhan.realtime.heartbeat-seconds=30`) + gateway `httpclient.response-timeout: 600s` |
| 외부 SaaS 의존 | **0** (Pusher / Firebase Realtime / Ably 모두 회피) |

## 3. 산출물 (6 commits, Phase A 5 + Phase B 1)

### 3.1 Phase A — 5 commits (DevOps 1 + BE 1 + FE-1 desktop + FE-2 mobile-staff + Designer)

#### `18f5177` chore(devops): PR-H1 gateway SSE env + nginx production hint + heartbeat 환경변수

| 파일 | 변경 |
| --- | --- |
| `services/api-gateway/src/main/resources/application.yml` | `httpclient.response-timeout: 600s` (SSE keep-alive) |
| `infrastructure/env-templates/api-gateway.env` | response-timeout 600s 환경변수 |
| `infrastructure/env-templates/slip-service.env` | `SAMHAN_REALTIME_HEARTBEAT_SECONDS=30` |
| `services/slip-service/src/main/resources/application.yml` | `samhan.realtime.heartbeat-seconds` property |
| `docs/devops/realtime-sse-production.md` 신규 | nginx config + AWS ALB / cafe24 운영 hint (`proxy_buffering off` / `proxy_read_timeout 600s` / `gzip off`) |

#### `5073029` feat(slip-service): PR-H1 SSE realtime infra + slip_comments smoke (V17)

15 files +1164 -1.

| 파일 | 변경 |
| --- | --- |
| `services/slip-service/src/main/resources/db/migration/V17__add_slip_comments.sql` 신규 | slip_comments + 부분 인덱스 + BaseEntity 7 audit |
| `services/slip-service/src/main/java/.../slip/comment/domain/SlipComment.java` 신규 | entity (BaseEntity 7 audit + Soft Delete) |
| `services/slip-service/src/main/java/.../slip/comment/repository/SlipCommentRepository.java` 신규 | repository (`@SQLRestriction("is_deleted = false")` 자동) |
| `services/slip-service/src/main/java/.../slip/comment/service/SlipCommentService.java` 신규 | add / listRecent / softDelete |
| `services/slip-service/src/main/java/.../slip/comment/web/SlipCommentController.java` 신규 | POST + GET (ApiResponse wrapper + ROLE 가드) |
| `services/slip-service/src/main/java/.../slip/comment/web/dto/{AddSlipCommentRequest,SlipCommentResponse}.java` 신규 | DTO 2건 |
| `services/slip-service/src/main/java/.../slip/realtime/SlipRealtimeBroker.java` 신규 | in-memory `Map<UUID, CopyOnWriteArrayList<SseEmitter>>` + 30s heartbeat + IOException/IllegalStateException cleanup |
| `services/slip-service/src/main/java/.../slip/realtime/SlipRealtimeController.java` 신규 | `GET /slips/{id}/realtime` SseEmitter (infinite timeout) |
| `services/slip-service/src/main/java/.../slip/SlipServiceApplication.java` | `@EnableScheduling` 활성 (heartbeat) |
| `services/slip-service/src/test/java/.../slip/comment/service/SlipCommentServiceTest.java` 신규 | 단위 5 case |
| `services/slip-service/src/test/java/.../slip/realtime/SlipRealtimeBrokerTest.java` 신규 | 단위 4 case |
| `services/slip-service/src/test/java/.../slip/comment/it/SlipRealtimeControllerIT.java` 신규 | IT 5 case (SSE / POST / GET / broker / 403) |
| `services/slip-service/src/test/java/.../slip/it/ApplicationContextLoadIT.java` | broker bean 단일 등록 가드 보강 |

#### `d9ad940` feat(desktop): PR-H1 SlipRealtimeClient + SlipDetailPage 코멘트 영역

3 files +472 -1.

| 파일 | 변경 |
| --- | --- |
| `clients/desktop/src/renderer/realtime/SlipRealtimeClient.ts` 신규 | fetch+ReadableStream polyfill (JWT 헤더 주입 + 5s reconnect backoff) |
| `clients/desktop/src/renderer/api/slipComment.ts` 신규 | list + add 2 endpoint client |
| `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` | 코멘트 Card 추가 (useQuery + useEffect SSE + optimistic add, data-testid 4종) |

#### `e26aab4` feat(mobile-staff): PR-H1 react-native-sse + SlipDetailScreen + 코멘트

7 files +874 -3.

| 파일 | 변경 |
| --- | --- |
| `clients/mobile-staff/package.json` + `package-lock.json` | `react-native-sse@^1.2.1` 의존 추가 (RN EventSource polyfill) |
| `clients/mobile-staff/src/realtime/SlipRealtimeClient.ts` 신규 | `subscribeToSlip` + heartbeat watchdog 60s |
| `clients/mobile-staff/src/api/slipComment.ts` 신규 | list / create / delete + ApiResponse wrapper assert |
| `clients/mobile-staff/src/screens/SlipDetailScreen.tsx` 신규 | slip 정보 + 코멘트 list/입력/전송 + SSE invalidate |
| `clients/mobile-staff/src/screens/driver/DriverDashboardScreen.tsx` | slip card 에서 "전표 보기 / 코멘트" 진입 link |
| `clients/mobile-staff/src/screens/driver/DriverTabNavigator.tsx` | minimal stack 으로 SlipDetailScreen push 활성 |

#### `fda4d8f` feat(design-system + uiux): PR-H1 코멘트 UX mock + userIdToColor 색상 hash util 시드

5 files +265.

| 파일 | 변경 |
| --- | --- |
| `clients/web/design-system/src/utils/userColorHash.ts` 신규 | HSL deterministic hash util (PR-H2 audit overlay 의존 시드) |
| `clients/web/design-system/src/utils/userColorHash.stories.tsx` 신규 | Storybook 1 story (5 userId 색상 swatch + Determinism 검증) |
| `clients/web/design-system/src/{index.ts,utils/index.ts}` | barrel export 보강 |
| `docs/uiux/phase12/H1-comment-smoke.md` 신규 | wireframe + 한국어 라벨 |

### 3.2 Phase B — 1 commit (QA)

#### `04e2b44` test(qa): PR-H1 QA 시나리오 + multi-context 작동 캡처 (SSE smoke)

7 files +807.

| 파일 | 변경 |
| --- | --- |
| `docs/qa/phase-12-step-1-websocket-infra/scenarios.md` 신규 | 14 case (subscribe + broadcast 5 + 다중 client 5 + API contract 4) + 페르소나 5 |
| `docs/qa/phase-12-step-1-websocket-infra/working-comment-context-a-input.png` 신규 | 사용자 A MASTER 영업 코멘트 입력 직전 |
| `docs/qa/phase-12-step-1-websocket-infra/working-comment-context-a-after-send.png` 신규 | 사용자 A 전송 직후 optimistic 표시 |
| `docs/qa/phase-12-step-1-websocket-infra/working-comment-context-b-receives.png` 신규 | 사용자 B SALES 창고 SSE 시뮬레이션 수신 |
| `docs/qa/phase-12-step-1-websocket-infra/working-multi-context-split.png` 신규 | 좌-A 우-B 한 화면 합성 (1280+1280=2560) |
| `tools/manual-capture/capture-pr-h1.js` 신규 | Playwright multi-context 자동화 (browser.newContext 2회 분리 + sharp 좌-우 합성 + 한국어 라벨 60px + generatePlaceholders fallback) |
| `clients/desktop/src/renderer/api/mock.ts` | POST/GET `/comments` mock (`globalThis.__SAMHAN_MOCK_COMMENTS_SEED` 으로 capture 시점 seed 주입, dev-only) |

## 4. 검증

### 4.1 단위 + IT (BE)

- `SlipCommentServiceTest` — 5 case (add / listRecent / softDelete / 빈 body 거부 / 권한 거부) PASS
- `SlipRealtimeBrokerTest` — 4 case (register / broadcast / cleanup IOException / heartbeat) PASS
- `SlipRealtimeControllerIT` — 5 case (SSE subscribe / POST 201 / GET 200 / broker single bean 가드 / 403 권한 거부) PASS
- `ApplicationContextLoadIT` — broker bean 단일 등록 가드 PASS

### 4.2 typecheck (FE)

- `clients/desktop` — typecheck PASS
- `clients/mobile-staff` — typecheck PASS, expo doctor 16/17 (pre-existing expo-font / expo-location version 경고만, 본 PR 무관)

### 4.3 풀빌드 (root)

- `gradlew assemble` — GREEN
- 13 backend service 모두 build PASS

### 4.4 작동 캡처 (QA)

- multi-context Playwright `capture-pr-h1.js` 실행 — 4 PNG 생성 완료
- 한국어 100% / UUID 비공개 / ROLE 풀네임 (MASTER / SALES) 통과
- PR body inline raw URL + commit-pinned (HEAD `04e2b44`) + HEAD 200 검증 의무

## 5. 후속 (PR-H1 머지 후)

- **PR-H2 (~3주) — slip audit overlay + 실시간 sync** — slip 라이프사이클 10단계 변경 broadcast (DRAFT→SAVED→DISPATCHED→...→COMPLETED) + 사용자별 색상 audit overlay (userColorHash 활용) + 변경 이력 timeline UI. 본 PR-H1 머지 후 즉시 진입.
- **PR-H3 (~1.5주) — 권한 / 수락 / 거절 워크플로우** — 영업 → 창고 → 기사 인계 시점 명시적 수락 + SSE 양방향 push.
- **PR-H4 (~7주) — 전 15 service 확장** — `shared/realtime` module 추출 + Redis Pub/Sub 분기 (다중 노드 진입 시 활성). partner / inventory / accounting / arologis / dashboard 등 14 backend MSA 도메인 모두 SSE 채널 도입.

## 6. 제약 / 가드 일관

- **BaseEntity 7 audit fields 의무** — `slip_comments` 신규 entity 7 audit (id / created_at / created_by_user_id / updated_at / updated_by_user_id / is_deleted / version) 모두 채움
- **Soft Delete 일관** — `slip_comments.is_deleted` + `@SQLRestriction("is_deleted = false")` + 부분 인덱스 (`WHERE is_deleted = false ORDER BY created_at DESC`) 적용
- **한국어 Javadoc** — `SlipRealtimeBroker` / `SlipRealtimeController` / `SlipCommentService` / `SlipComment` 모두 한국어 Javadoc 의무
- **ROLE 풀네임** — 본 PR 모든 산출물 풀네임 (MASTER / MANAGER / SALES / WAREHOUSE / DRIVER 등). 약어 (M/M/S) 금지
- **UUID 비공개** — 코멘트 author 표시 = 사용자명 + ROLE (UUID 0 노출). data-testid `slip-comment-${commentId}` 는 내부 식별자만 사용
- **partner_code snapshot 의무 (해당 없음)** — slip_comments 는 partner 의존 0
- **Korean path JDK 트랩 회피** — Windows dev 시 `gradle test` 회피 가능. CI Linux runner 에서 정식 검증
- **외부 SaaS 의존 0** — Pusher / Firebase Realtime / Ably 회피. Spring `SseEmitter` 표준만 사용

## 7. 통합 PR 패턴 일관 (memory `feedback_integrated_pr_pattern`)

본 PR-H1 = 5-team 병렬 (BE / FE-1 desktop / FE-2 mobile-staff / Designer / DevOps) Phase A 5 + Phase B (QA) 1 = 단일 통합 PR (총 6 commits). 별도 docs PR 회피 (memory `feedback_continuous_docs_sync` 일관) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신.

## 8. 5-team 리뷰 + CI + PM + 사용자 머지 워크플로우 (memory `feedback_pr_review_workflow`)

본 PR 머지 절차:
1. PR 발행 즉시 `gh pr checks --watch` 자동 시작 (memory `feedback_pr_ci_monitoring`)
2. 5-team 리뷰 (BE / FE / Designer / QA / DevOps) PR comment 토론 (memory `feedback_tm_led_agent_discussion`)
3. CI green + reviewer agent 토론 종료 후 TM 종합 추가 commit (필요 시)
4. PM 최종 승인 댓글 + 머지 요청 (memory `feedback_user_merge_authority`)
5. 사용자 머지
6. 머지 후 PR-H2 (slip audit overlay + 실시간 sync ~3주) 진입
