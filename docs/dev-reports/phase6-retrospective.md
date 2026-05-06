# Phase 6 회고 보고서

## 1. 개요

Phase 6 (legacy migration 본격 구현 단계) 의 모든 슬라이스가 main 머지를 완료하여,
본 보고서로 슬라이스별 산출, 학습 매트릭스, Phase 7 위임 항목을 정리한다.

- 시작 commit (Phase 5 종료 후 첫 머지): PR #38 (M1a product-service 시드)
- 종료 commit: PR #79 (client mock 일괄 제거) — `b4c4970` (origin/main HEAD)
- 총 머지 PR 수: 14건 (close 4건 + 머지 14건 — 일부 close 는 통합 PR 으로 합류)

## 2. 머지된 PR 목록 (Phase 6)

| PR | 제목/요지 | 상태 | 비고 |
|---|---|---|---|
| #38 | M1a product-service 시드 (시드 데이터 + 시드 runner) | 머지 | Phase 6 첫 슬라이스 |
| #50 | web order-app v4 (Vite SPA + PWA 1차) | 머지 | 단편 발행 |
| #53 | web order-app v4 후속 fix | 머지 | #50 정정 |
| #51 | Desktop v4 (Electron + Vite + React) 1차 | 머지 | 단편 발행 |
| #54 | Desktop v4 후속 fix | 머지 | #51 정정 |
| #52 | Mobile v4 (Expo RN + WebView wrapper) | 머지 | order-app v4 wrap |
| #58 | estimate-app v2 (Node.js + Express + EJS) | 머지 | B2 옵션 채택 |
| #61 | mobile DC notice 삭제 (UUID 노출 회피 가드 적용) | 머지 | 단편 hotfix |
| #66 | (단편 hotfix 발행) | **close** | 단편 PR 정리 계기 |
| #67 | legacy-v2 import (이카운트/노션 살린 버전) | 머지 | 후 별 프로젝트 분리로 결정 |
| #70 | #67 revert (legacy-v2 → 다른 프로젝트 이전) | 머지 | SamhanLogis 범위 정리 |
| #68 | BE 버전 1 — product-service google sheets cron | 머지 | 후 #75 정정 |
| #69 | RN client 통합 (Mobile v4 + mobile-staff v3) | 머지 | 통합 발행 첫 적용 |
| #71 | M3 단독 발행 | **close** | 통합 PR 합류 (#76) |
| #72 | M2 partner-auth-service | 머지 | GG fix `97ca8da` 는 #76 으로 합류 |
| #73 | FE 버전 1 — estimate-app google sheets 직접 | 머지 | |
| #74 | M4 단독 발행 (CI fail) | **close** | 통합 PR 합류 (#76, CI fix 포함) |
| #75 | #68 정정 (`getDisplayValues` / `getFormulas`) | 머지 | google sheets API 호출 정확화 |
| #76 | Phase 6 backend 통합 — M2 GG fix + M3 + M4 fix + M5 | 머지 | 4 슬라이스 squash 통합 |
| #77 | DEVOPS — Cloudflare Pages deploy workflow (order-app 활성) | 머지 | estimate-app/카페24 template |
| #78 | QA — Playwright (web) + Detox (RN) 셋업 + CI workflow | 머지 | 30 case e2e 인프라 |
| #79 | client mock 일괄 제거 (`USE_MOCK_FALLBACK` 폐기) | 머지 | 실 backend 호출 전환 |

## 3. 학습 사항 매트릭스

### 3.1 PR 발행 패턴

| 학습 | 트리거 | 적용 |
|---|---|---|
| 단편 PR → 통합 PR 전환 | PR #66 close (디자인 단편 hotfix) | 통합 발행 |
| 단독 발행 → 통합 발행 | PR #71 / #74 / #77 / #78 / #79 (각 단독 발행 후 통합 재구성 발생) | 통합 발행 |
| 통합 PR 의 historic commit GG 검출 | PR #76 1차 발행 (10 commit history → GG fail 9건) | `git merge --squash` x N (sub 별 단일 commit) |

### 3.2 GitGuardian 오탐 / 진탐 패턴

| 패턴 | 검출 카테고리 | 회피 방법 |
|---|---|---|
| `samhan/samhan_dev_pw` literal | Generic Password | placeholder `${VAR:CHANGE_ME_LOCAL_ONLY}` |
| Testcontainers `tc_pw_xxx` | Generic Password | `withUsername/withPassword` 호출 생략 → default test/test |
| fixture `password: "..."` 키 이름 | Generic Password (오탐) | 키 이름 변경 (e.g. `password` → `apiKey`) |
| `DUMMY_*_TOKEN` 환경 변수 default | (정상 — placeholder) | 명시 prefix 로 detector 회피 |

### 3.3 빌드 / 환경

| 학습 | 트리거 | 적용 |
|---|---|---|
| 한글 path JDK 17 트랩 (`gradle test` 실패) | 로컬 검증 | `assemble` + IT skip |
| Hibernate 6 + PostgreSQL `@Lob String` → OID 매핑 충돌 (Flyway TEXT 와 schema-validation fail) | PR #76 M4 5 IT cascade fail | `@Column(columnDefinition="TEXT")` 명시 |
| Slip entity nullable + DB DEFAULT 모순 회귀 | PR #76 후속 hotfix (`f830efc`) | entity Java 기본값 + `nullable=false` 일관 |
| idempotencyKey 거래처별 격리 누락 | PR #76 후속 hotfix (`dd21d57`) | PostgreSQL Testcontainer share 환경에서 `(partnerId, draftSeq)` UNIQUE composite |

### 3.4 데이터 / API 호환

| 학습 | 트리거 | 적용 |
|---|---|---|
| Google Sheets `getValues()` ↔ `getDisplayValues()` ↔ `getFormulas()` 차이 | PR #75 (#68 정정) | display vs raw vs formula 3종을 슬라이스별 명시 |
| UUID 노출 금지 | PR #18 + PR #61 (DC notice 삭제) | client 노출 식별자는 비즈니스 코드 (사번/슬립번호/창고 코드/모델명/거래처명) 만 |
| client mock silent fallback → endpoint 회귀 가림 | PR #79 | A 옵션 (완전 폐기) 채택 — 실패는 throw |

## 4. 미결 항목 (Phase 7 위임)

### 4.1 호스팅

- **estimate-app v2 호스팅 결정** — Express SSR (Cloudflare Pages 기술적 불가) → Workers / Render / 카페24 SSH 3안 비교 필요. 본 PR 의 `docs/migration/phase7/M-ESTIMATE-APP-hosting-decision.md` 참조.
- **카페24 SSH 배포 활성화** — 1G RAM 한계 + samhan 공식 홈페이지 pm2 공존 검증 필요. 활성화 보류 중 (테스트만 진행).
- **14 backend MSA Phase 7 호스팅** — 별도 인프라 결정 (Hetzner / 카페24 plan 업그레이드 / Render 유료 등). `docs/migration/phase7/M-PHASE-7-readiness.md` § 4 참조.

### 4.2 환경

- **dev/staging 환경 구축** — Phase 6 backend 4 슬라이스의 dev/staging endpoint 부재 (`SAMHAN_API_BASE_URL` 등은 client `.env.example` 만 정의, 실 endpoint 미가동).
- **mock 제거 후 backend 안정화 검증** — PR #79 머지로 client 가 실 backend 호출 전환 → backend 미가동 시 5xx/네트워크 오류 노출. dev 환경 가동 후 e2e 시나리오 재돌입 필요.

### 4.3 QA

- **시나리오 30 → 90 cell 확장** — PR #78 의 30 case (3 device × 10 시나리오 abstraction) 를 90 case 로 확장. 본 작업은 Phase 7 QA 슬라이스 후속.
- **k6 부하 + OWASP ZAP 보안** — Phase 7 QA 후속 슬라이스로 위임.

### 4.4 도메인 / backend 신설

- **partner-service 신설** — M5 의 `partnerCode` → `partnerId` lookup 미구현 (현재 raw `partnerCode` 직접 사용). partner master 단일 owner 분리 필요.
- **accounting-service 실 구현** — Phase 6 의 accounting slice A 는 skeleton 만 머지. 한국 일반기업회계기준 표준 계정과목 코드 (100/200/300/400/500/800/900) seed 가 적용된 실 슬라이스 후속.

## 5. 통계 요약

| 항목 | 값 |
|---|---|
| 머지 PR | 14 |
| close PR | 4 (#66 hotfix / #71 M3 / #74 M4 / #67 → #70 revert) |
| backend skeleton 신설 슬라이스 | M2 / M3 / M4 / M5 + product-service 시드/sync (총 5) |
| frontend 신설 client | order-app v4 / Desktop v4 / Mobile v4 / mobile-staff v3 / estimate-app v2 (총 5) |
| 신규 학습 후보 | 통합 PR squash GG / Hibernate `@Lob` TEXT / mock A 폐기 (3건) |

---

본 보고서는 Phase 6 종료 시점의 스냅샷이며, Phase 7 진입 전제 조건은
`docs/migration/phase7/M-PHASE-7-readiness.md` 에서 확장한다.
