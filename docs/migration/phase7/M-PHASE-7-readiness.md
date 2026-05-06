# M-PHASE-7-readiness — Phase 6 → Phase 7 전환 준비 plan

## 1. Phase 6 완료 항목 체크리스트

| 항목 | 상태 | 출처 |
|---|---|---|
| product-service 시드 | DONE | PR #38 |
| product-service google sheets sync (cron) | DONE | PR #68 + #75 |
| M2 partner-auth-service skeleton | DONE | PR #72 + GG fix `97ca8da` (PR #76 합류) |
| M3 dc-config-service skeleton | DONE | PR #76 (M3 sub) |
| M4 partner-order-service skeleton | DONE | PR #76 (M4 sub, CI fail fix 포함) |
| M5 slip-service `/from-*` endpoint | DONE | PR #76 (M5 sub) |
| order-app v4 (Vite + PWA) | DONE | PR #50 + #53 |
| Desktop v4 (Electron + Vite) | DONE | PR #51 + #54 |
| Mobile v4 (Expo RN + WebView) | DONE | PR #52 + PR #69 통합 |
| mobile-staff v3 (Expo RN + WebView) | DONE | PR #69 통합 |
| estimate-app v2 (Express + EJS) | DONE | PR #58 |
| Cloudflare Pages deploy workflow (order-app 활성) | DONE | PR #77 |
| QA Playwright + Detox 셋업 (30 case) | DONE | PR #78 |
| client mock fallback 일괄 제거 | DONE | PR #79 |
| Phase 6 회고 보고서 | DONE | 본 PR `docs/dev-reports/phase6-retrospective.md` |
| DECISIONS.md Phase 6 마무리 항목 | DONE | 본 PR `migration/decisions/DECISIONS.md` |

## 2. Phase 7 진입 전제 조건

| 전제 조건 | 산출 | 위임 대상 |
|---|---|---|
| estimate-app v2 호스팅 옵션 1건 확정 | `M-ESTIMATE-APP-hosting-decision.md` 의 A/B/C 중 1건 채택 | 호스팅 결정 회의 |
| 14 backend MSA 호스팅 옵션 1건 확정 | 본 plan § 4 의 X1~X4 중 1건 채택 | 호스팅 결정 회의 |
| 카페24 SSH D6/D7/D8 답변 | 배포 대상 / 디렉토리 / pm2 명명 | 인프라 답변 |
| Phase 7 backend dev/staging 환경 가동 | endpoint URL 5종 (M2/M3/M4/M5 + product) | DEVOPS |
| Phase 7 backend 가동 후 client 실 endpoint 검증 | mock 제거 후 e2e 시나리오 PASS | QA |

## 3. Phase 7 작업 분해 (위임 슬라이스)

### 3.1 QA 슬라이스

| 슬라이스 | 산출 | 의존 |
|---|---|---|
| Q-S1. 시나리오 30 → 90 cell 확장 | Playwright spec 60 case 추가 (3 device × 30 시나리오 abstraction) | Phase 7 backend staging |
| Q-S2. k6 부하 시험 (catalog / draft / slip) | k6 script + dashboard | Phase 7 backend staging |
| Q-S3. OWASP ZAP 보안 시험 | ZAP baseline scan 보고 | Phase 7 backend staging |
| Q-S4. mock 제거 후 backend 안정화 검증 | 30/90 case PASS 보고 | Phase 7 backend staging |

### 3.2 DEVOPS 슬라이스

| 슬라이스 | 산출 | 의존 |
|---|---|---|
| D-S1. Phase 7 backend staging 환경 구축 | 14 backend MSA 의 staging endpoint 5종 활성 | 호스팅 결정 |
| D-S2. estimate-app v2 production 활성화 | Render Web Service / Workers / 카페24 SSH 중 1건 + DNS | 호스팅 결정 |
| D-S3. 카페24 SSH workflow 활성화 | `deploy-cafe24-ssh.yml.template` 제거 + secrets | D6/D7/D8 답변 |
| D-S4. Phase 7 backend 의 dev/staging 환경 모니터링 | logs / metrics dashboard | D-S1 후 |

### 3.3 backend 슬라이스 신설

| 슬라이스 | 범위 | 의존 |
|---|---|---|
| BE-S1. partner-service 신설 | M5 의 `partnerCode` → `partnerId` lookup 단일 owner | M3 dc-config-service 와 owner 분리 검토 |
| BE-S2. accounting-service 실 구현 | Phase 6 skeleton (accounting slice A) 위에 한국 일반기업회계기준 표준 계정과목 코드 (100/200/300/400/500/800/900) seed + 매출/매입 분개 endpoint | product-service / slip-service |
| BE-S3. backend dev/staging 안정화 | Phase 6 머지 4 슬라이스 + product-service 의 endpoint 가동 + 모니터링 | D-S1 |

## 4. 14 backend MSA 호스팅 결정 (X1~X4)

본 결정은 호스팅 결정 회의에서 답변 후 별도 plan 으로 구체화한다. 본 plan 에서는 옵션만 후보 정리한다.

### X1. Hetzner Cloud (CX22 / CX32 VPS)

| 항목 | 상세 |
|---|---|
| 비용 (월) | CX22 €5 (4 vCPU, 8GB RAM) ~ CX32 €10 (8 vCPU, 16GB RAM) |
| Latency (한국) | Helsinki / Falkenstein region — ~250ms (Phase 7 staging 으로는 허용) |
| 운영 부담 | 중 — Docker compose 자체 구축, monitoring 별도 |
| 호환성 | 14 backend Spring Boot 3 + PostgreSQL service-per-DB 14개 → CX32 권장 |

### X2. 카페24 plan 업그레이드 (가상서버 / 클라우드)

| 항목 | 상세 |
|---|---|
| 비용 (월) | 가상서버 8GB ~월 ₩60k (대략) / 클라우드 별도 |
| Latency (한국) | < 30ms |
| 운영 부담 | 상 — SSH + 자체 구축, 기존 1G RAM plan 과 별도 |
| 호환성 | Spring Boot + PostgreSQL Docker compose 가능 — 카페24 가상서버 sudo 가용 시 |

### X3. Render.com (PostgreSQL + 14 Web Services)

| 항목 | 상세 |
|---|---|
| 비용 (월) | Web Service Starter $7 × 14 = $98 + PostgreSQL 14개 비용 → 합 $200+ (대략) |
| Latency (한국) | Singapore region ~150ms |
| 운영 부담 | 하 — 각 service 의 Dockerfile push 만, monitoring 내장 |
| 호환성 | 14 Spring Boot service 전체 native 가능 |

### X4. AWS / GCP (참고)

| 항목 | 상세 |
|---|---|
| 비용 | EC2 t3.medium × 14 + RDS PostgreSQL 14개 → 월 $300+ (대략) |
| Latency (한국) | Seoul region ~10ms |
| 운영 부담 | 중 — EKS / ECS 권장, IAM 정책 / VPC 별도 |
| 호환성 | 모든 stack 지원 |

### 비교 매트릭스

| 항목 | X1 Hetzner | X2 카페24 업그레이드 | X3 Render | X4 AWS |
|---|---|---|---|---|
| 월 비용 | €5~10 | ~₩60k+ | $200+ | $300+ |
| Latency (한국) | ~250ms | < 30ms | ~150ms | ~10ms |
| 운영 부담 | 중 | 상 | 하 | 중 |
| 14 service 동시 가동 | CX32 가능 | 가상서버 spec 검토 | 가능 (비용 ↑) | 가능 (비용 ↑) |
| Phase 7 staging 적합 | O | O (spec 검토 후) | O | O (cost ↑) |

답변 대기 항목 (호스팅 결정 회의):

- D9-1. Phase 7 staging 비용 상한
- D9-2. 한국 latency 요구치 (staging 단계 250ms 허용 여부)
- D9-3. 운영 부담 우선순위 (자체 구축 vs PaaS)

## 5. 위험 / 블로커

| 위험 | 영향 | 완화 |
|---|---|---|
| Phase 7 backend 미가동 → mock 제거 후 client 동작 불가 | client 5개 모두 5xx | 호스팅 결정 회의 즉시 진행, dev 환경 우선 가동 |
| estimate-app v2 호스팅 미결 → production URL 미가동 | estimate.samhan-air.com 503 | M-ESTIMATE-APP-hosting-decision § 0 권장 (B Render) 즉시 채택 가능 |
| 카페24 SSH 활성화 보류 → 일부 deploy workflow template 잔류 | template 파일 잔류 (배포 risk X) | D6/D7/D8 답변 후 활성화 |
| 14 backend 호스팅 결정 지연 → Phase 7 진입 지연 | 전체 일정 +1~2주 | 본 plan § 4 의 4안 즉시 비교 회의 |

## 6. 다음 단계

1. 호스팅 결정 회의 (estimate-app v2 + 14 backend MSA 동시 결정 권장)
2. DEVOPS 의 D-S1 (backend staging) + D-S2 (estimate-app production) 슬라이스 발행
3. QA 의 Q-S1 (90 case 확장) + Q-S4 (mock 제거 후 검증) 슬라이스 발행
4. backend 의 BE-S1 (partner-service) + BE-S2 (accounting-service) 신설 슬라이스 발행

본 plan 의 모든 위임 슬라이스는 통합 발행 으로 진행한다.
