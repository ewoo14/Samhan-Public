# M-PHASE-8-readiness — Phase 7 → Phase 8 전환 준비 plan

## 1. Phase 7 완료 항목 체크리스트

| 항목 | 상태 | 출처 |
|---|---|---|
| 카페24 SSH dry-run script | DONE | PR #81 |
| Render Blueprint (estimate-app 활성, order-app 미러) | DONE | PR #81 |
| QA Playwright 60+ cell 시나리오 | DONE | PR #81 |
| QA edge (api-5xx / stock-race / dc-snapshot) | DONE | PR #82 |
| QA visual regression 5 spec | DONE | PR #82 |
| QA Detox 6 시나리오 | DONE | PR #82 |
| FE schema/selector 정밀화 | DONE | PR #82, #83 |
| DevOps CSP / Slack 비동기 | DONE | PR #82 |
| BE product by-code endpoint | DONE | PR #83 |
| design-system dark-mode 정식 | DONE | PR #84 |
| visual baseline 5 spec 활성 | DONE | PR #84 |
| README + ROADMAP + DECISIONS Phase 7 | DONE | PR #85 |
| 통일 alias 토큰 (폰트 / spacing / radius / shadow) | DONE | PR #86 |
| Pretendard web font 통일 | DONE | PR #86 (jsdelivr) + 본 PR (self-host) |
| RN graceful 폰트 hook | DONE | PR #86 |
| Pretendard self-host (jsdelivr SPOF 회피) | DONE | 본 PR |
| Express helmet middleware 정식 | DONE | 본 PR |
| Electron renderer CSP 갱신 | DONE | 본 PR |
| visual baseline `document.fonts.ready` 가드 | DONE | 본 PR |
| Phase 7 회고 보고서 | DONE | 본 PR `docs/dev-reports/phase7-retrospective.md` |
| DECISIONS.md Phase 7 마무리 + Phase 8 진입 항목 | DONE | 본 PR |

## 2. Phase 8 진입 전제 조건

| 전제 조건 | 산출 | 위임 대상 |
|---|---|---|
| D9 답변 (14 backend MSA 호스팅 옵션 X1 ~ X4 중 1택) | 옵션 채택 commit (DECISIONS) | 호스팅 결정 회의 |
| (X1 옵션 시) D6/D7/D8 답변 (카페24 SSH 활성) | 배포 대상 / 디렉토리 / pm2 명명 | 인프라 답변 |
| Phase 8 backend dev/staging 환경 가동 | 14 MSA endpoint URL 활성 | DEVOPS |
| visual baseline 6 PNG staging 생성 | `playwright test --update-snapshots` 후 commit | QA |

> D9 답변 X 시 = 카페24 plan 업그레이드 X 가정 → Hetzner / AWS 결정.

## 3. Phase 8 작업 분해

### 3.1 호스팅 인프라 (선택 옵션에 따라)

| 옵션 | 설명 | 월 비용 (예시) | 비고 |
|---|---|---|---|
| X1 카페24 plan 업그레이드 | 기존 카페24 SSH 호스트 plan 업그레이드 (RAM / CPU) | $50 ~ $100 | D6/D7/D8 답변 필수 |
| X2 Hetzner Cloud | CCX22 (4 vCPU, 16GB RAM) × 2 노드 + 외부 PostgreSQL | $50 ~ $80 | EU 리전, 응답시간 트레이드오프 |
| X3 AWS EC2 + RDS | t3.medium × 2 + RDS PostgreSQL (db.t3.small) | $150 ~ $250 | seoul 리전, 운영 안정 |
| X4 하이브리드 | front 카페24 + backend Hetzner / AWS | 변동 | DNS 분기 + CORS 정책 추가 |

### 3.2 DB migration (Flyway)

| 단계 | 산출 | 의존 |
|---|---|---|
| V1 ~ V8 staging 적용 | 14 service Flyway baseline 검증 | staging stack 활성 |
| V1 ~ V8 production 적용 | dry-run + outOfOrder 비활성 | staging 통과 |
| 65 row 한국 일반기업회계기준 시드 | accounting-service init 시 자동 | DB 생성 후 |
| 16 명 유저 시드 | user-service init 시 자동 | DB 생성 후 |

### 3.3 Eureka cluster

| 작업 | 산출 |
|---|---|
| 다중 노드 (2 ~ 3) 구성 | `services/eureka-server` profile prod |
| AZ 분산 (X3 옵션 시) | 다른 AZ 의 노드 인식 |
| Self-preservation mode 운영 임계치 | renewal threshold 0.85 |

### 3.4 Resilience4j prod 설정

| 컴포넌트 | 임계치 |
|---|---|
| Circuit Breaker | failureRate 50% / 윈도 30s / minimumNumberOfCalls 10 |
| Retry | maxAttempts 3 / backoff 200ms exponential |
| Bulkhead | maxConcurrentCalls 25 / maxWaitDuration 1s |
| Time Limiter | timeoutDuration 5s |

### 3.5 API Gateway

| 작업 | 산출 |
|---|---|
| production routing | 14 MSA route 활성 (eureka 기반) |
| rate limit | per-IP 100 req/min, per-user 1000 req/min |
| WAF (Cloudflare) | OWASP top 10 차단 + bot 보호 |
| HeaderAuthenticationFilter prod | JWT HS256 + audience claim 검증 |

### 3.6 모니터링 alert

| 통보 대상 | 임계치 |
|---|---|
| 에러율 > 1% (5분 윈도) | Slack #ops + SMS (담당자) |
| 응답시간 p95 > 500ms (5분 윈도) | Slack #ops |
| Disk free < 10% | Slack #ops + SMS |
| Memory free < 15% | Slack #ops |
| Eureka self-preservation 발동 | Slack #ops + SMS |

### 3.7 DNS cutover

| 서브도메인 | 대상 |
|---|---|
| `app.samhan-air.com`     | Cloudflare Pages (order-app v4) |
| `quote.samhan-air.com`   | Render (estimate-app v2) |
| `api.samhan-air.com`     | API Gateway |
| `sign.samhan-air.com`    | signature endpoint |
| `chat.samhan-air.com`    | (Phase 9 — groupware) |
| `files.samhan-air.com`   | MinIO 게이트웨이 |
| `monitor.samhan-air.com` | Grafana |
| `order.samhan-air.com`   | (별 도메인 또는 app 의 path 와 통합) |

### 3.8 production smoke

| 항목 | 검증 도구 |
|---|---|
| 14 MSA `/actuator/health` 200 | curl + jq |
| auth-service 로그인 happy path | curl |
| product-service /by-code 200 | curl |
| order-app v4 / estimate-app v2 / mobile / mobile-staff / desktop 6 PNG visual baseline 일치 | Playwright |
| Detox 6 시나리오 | Detox |

## 4. 주차별 plan

| 주 | 작업 | 산출 |
|---|---|---|
| W1 | 호스팅 결정 + DB 준비 | 14 MSA staging endpoint URL 활성 + V1~V8 staging 적용 |
| W2 | Eureka cluster + Resilience4j prod | 다중 노드 + 임계치 정의 |
| W3 | API Gateway + 모니터링 | rate limit + WAF + alert 정착 |
| W4 | DNS cutover + smoke | 8 서브도메인 적용 + smoke 통과 |
| W5 | 운영 안정화 + 회고 | 24h 무장애 + Phase 8 회고 보고서 |

## 5. 위임된 미결 결정

| ID | 주제 | 답변 시점 |
|---|---|---|
| D6 | 카페24 SSH 배포 대상 앱 (X1 옵션 시) | Phase 8 1주차 |
| D7 | 카페24 호스트 내 배포 디렉토리 | D6 답변 후 |
| D8 | 카페24 pm2 process 명명 규약 | D6 / D7 답변 후 |
| D9 | 14 backend MSA 운영 호스팅 옵션 (X1 ~ X4) | Phase 8 진입 전 |

## 6. 참조

- 누적 결정: `migration/decisions/DECISIONS.md`
- Phase 7 회고: `docs/dev-reports/phase7-retrospective.md`
- Phase 7 진입 plan: `docs/migration/phase7/M-PHASE-7-readiness.md`
- estimate-app 호스팅 결정: `docs/migration/phase7/M-ESTIMATE-APP-hosting-decision.md`
