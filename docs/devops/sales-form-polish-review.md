# Sales Form UX Polish 슬라이스 — DevOps 검토 리포트

> 슬라이스: sales-form-polish-slice | base: b5583b7 (PR #19 머지 직후) |
> 작성일: 2026-05-04 | 작성: DevOps Agent (worktree `agent-ad2f97791dc53915a`)

본 리포트는 BE 작은 변경 (inventory-service `POST /inventory/balances/batch`
endpoint 1건) + FE 큰 리팩토링 (`@dnd-kit` 3 NPM 의존성 + 신규 컴포넌트
3종 + SlipFormPage 표 layout 전환 + DispatchView 가로→세로) + Designer
신규 디자인 토큰 alias (5 카테고리 약 40종) 슬라이스에 대한 DevOps 영역
검토 결과이다. **인프라 자체 변경 0건**이며, 본 리포트는 **NPM 의존성
영향 + 인쇄 보안 후속 권고 갱신 + 후속 슬라이스 권고**에 집중한다.

---

## 1. 인프라 변경

### 1.1 추가/수정 자원
- **인프라 자체 변경 없음** — 신규 마이크로서비스 / DB 스키마 / docker
  compose / infra 설정 모두 0건
- BE: inventory-service `POST /inventory/balances/batch` endpoint **1건만
  추가** (productIds[] + warehouseIds[] → 창고 × 모델 매트릭스 응답).
  기존 `/api/inventory/**` 라우트 하위로 자동 노출 — gateway 라우트 변경
  0건
- FE: 신규 NPM 의존성 **3종** 추가 — `@dnd-kit/core`, `@dnd-kit/sortable`,
  `@dnd-kit/utilities`. Bundle size 영향 약 30~50kB (gzipped 12~18kB)
- Designer: `tokens.css` 끝에 신규 alias **약 40종** append (기존 토큰
  변경 0건 — Q5=B 결정대로 SlipFormPage / StockBalanceModal /
  DispatchView 만 점진 적용)

### 1.2 점검 결과 (실제 파일 확인)

| 항목 | 위치 | 상태 |
|---|---|---|
| `/api/inventory/**` 라우트 | `services/api-gateway/src/main/resources/application.yml:54-60` | 기존 — `lb://inventory-service` + StripPrefix=1 + JwtAuthentication. 신규 batch endpoint 자동 노출 |
| `/api/slips/**` 라우트 | `services/api-gateway/.../application.yml:38-44` | 기존 — 본 슬라이스 BE 변경 없음 |
| `/api/products/**` 라우트 | `services/api-gateway/.../application.yml:46-52` | 기존 — 본 슬라이스 BE 변경 없음 |
| Electron CORS 호환 | `services/api-gateway/.../config/CorsConfig.java` | 기존 — `app://com.samhanair.logis.desktop`, `file://*`, `http://localhost:*` 패턴 등록 완료 |
| Docker Compose | `infrastructure/docker-compose.yml` | 변경 불필요 |
| GitHub Actions CI | `.github/workflows/ci.yml` | 변경 불필요 (assemble + test 그대로) |
| Prometheus/Grafana | `infrastructure/{prometheus,grafana}` | 변경 불필요 — 기존 inventory-service 메트릭 자동 수집 |

신규 batch endpoint 가 기존 prefix (`/api/inventory`) 하위에 추가되므로
gateway 라우트 수정은 일절 필요 없다. CORS 도 데스크톱 origin 패턴이
이미 등록되어 있어 추가 조정 불요.

---

## 2. 빌드 영향

### 2.1 BE 빌드 영향

| 항목 | 영향 | 비고 |
|---|---|---|
| `./gradlew :services:inventory-service:assemble` | +3~5초 | batch DTO + Controller + Service + Repository 메서드 — 컴파일 부담 미미 |
| `./gradlew :services:inventory-service:test` | +20~30초 | unit + IT (Testcontainers) 1~2건 추가 예상 (batch endpoint 권한 매트릭스 7-tier) |
| 전 모듈 `./gradlew assemble` | +5초 미만 | inventory-service 단일 모듈 변경 |
| 전 모듈 `./gradlew test` | +30초 미만 | 기존 IT 영향 없음 |

### 2.2 FE 빌드 영향

| 항목 | 영향 | 비고 |
|---|---|---|
| `npm install` (clients/desktop) | +5초 | `@dnd-kit/core` + `@dnd-kit/sortable` + `@dnd-kit/utilities` 3 패키지 신규 다운로드 |
| `npm run typecheck` | +1~2초 | dnd-kit 타입 정의 추가 |
| `npm run build` (electron-vite) | +3~5초 | bundle size 약 30~50kB 증가 |
| renderer bundle | 675kB → **약 720kB** 추정 | dnd-kit 약 30~40kB raw + 신규 컴포넌트 3종 + tokens.css 약 5kB |
| `npm run build:win` (electron-builder) | 본 슬라이스 미수행 | 별도 release 슬라이스에서 처리 |
| Storybook 빌드 (`@samhan/design-system`) | 변경 없음 | 디자인 시스템 컴포넌트 미변경 (FE 가 desktop 내부에 신규 컴포넌트 추가) |

### 2.3 NPM 의존성 점검 (`@dnd-kit/*`)

| 패키지 | 버전 권장 | 라이선스 | 주의사항 |
|---|---|---|---|
| `@dnd-kit/core` | ^6.1.0 | MIT | maintenance 활성 (`react-beautiful-dnd` 는 abandoned 상태 — 의도적 회피) |
| `@dnd-kit/sortable` | ^8.0.0 | MIT | core 의 dependant. peer dep core ^6 |
| `@dnd-kit/utilities` | ^3.2.2 | MIT | 가벼움 (~2kB) |

라이선스 호환 OK — MIT 3건 모두 Samhan Public 사내 사용에 제약 없음.
패키지 supply chain 위험: 모두 npm registry verified publisher
(@clauderic). 별도 SCA (Snyk / Dependabot) 도입은 후속 슬라이스에서 일괄
처리 권고.

### 2.4 디자인 토큰 갱신 영향

- 적용 대상: `clients/web/design-system/src/tokens/tokens.css` 끝에
  **append only** (Designer `tokens.md` § 1 인용)
- 기존 토큰 변경 0건 — 16 컴포넌트 회귀 영향 없음
- Storybook visual regression 위험: **없음** (기존 토큰 미변경)
- bundle size 영향: tokens.css 약 +3~5kB (gzipped 약 +1~2kB)

---

## 3. 인쇄 양식 보안 (PR #19 후속 권고 갱신)

본 슬라이스는 PR #19 (slip-output-format) 의 인쇄 양식 두 종 중
**DispatchView 만** 가로 → 세로 정정한다. 인쇄 보안 위험 자체는 PR #19
와 동일하며, 본 슬라이스에서도 권한 분리 / 워터마크 / 감사 로그 / PDF
저장 차단은 적용되지 않는다.

### 3.1 잔여 위험 (PR #19 review.md §2 동일)

| # | 위험 | 본 슬라이스 변경 | 후속 슬라이스 도입 시점 |
|---|---|---|---|
| 1 | 거래처/단가 정보 종이/PDF 외부 유출 | 동일 (DispatchView 세로 변경만) | 인쇄 보안 강화 슬라이스 |
| 2 | 인쇄 권한 미분리 (모든 인증 사용자) | 동일 — 신규 권한 가드 미적용 | 인쇄 보안 강화 슬라이스 |
| 3 | Electron `window.print()` → OS PDF 다이얼로그 | 동일 — `printToPDF` API 차단 미적용 | 인쇄 보안 강화 슬라이스 |
| 4 | 인쇄 감사 추적 불가 (`SlipPrintEvent` 미존재) | 동일 — logging-service 발행 없음 | 인쇄 보안 강화 슬라이스 |
| 5 | 거래처 정보 마스킹 없음 | 동일 | Partner Service Q9 슬라이스 |
| 6 | 인쇄 횟수 제한 없음 | 동일 | 인쇄 보안 강화 슬라이스 |

### 3.2 본 슬라이스 신규 위험 (낮음)

- DispatchView 세로 layout 변경으로 1장에 더 많은 라인 노출 가능
  (가로 대비 표 가용 영역 약 +25%) → 단일 PDF 1장 캡처 시 외부 유출
  정보량 증가. 권한 분리 도입 전까지는 **물리적 종이 인쇄 워크플로 우선
  사용 권고** (운영 차원).

### 3.3 권고 (PR #19 와 동일, 우선순위 변경 없음)

후속 "인쇄 보안 강화" 슬라이스에서 PR #19 review.md §2.2 의 6개
권고 항목 (인쇄 권한 분리 / 워터마크 / 감사 로그 / PDF 차단 / 거래처
정보 마스킹 / 인쇄 횟수 제한) 일괄 도입.

---

## 4. CI 영향

| 항목 | 영향 | 비고 |
|---|---|---|
| `./gradlew assemble` | < 5초 추가 | inventory-service 단일 모듈 변경 (batch endpoint + DTO) |
| `./gradlew test` | < 30초 추가 | inventory-service IT 1~2건 (Testcontainers Postgres + 7-tier 권한 매트릭스) |
| `npm install` (clients/desktop) | +5초 | @dnd-kit 3 패키지 |
| `npm run build` (clients/desktop) | +3~5초 | bundle size 30~50kB 증가 |
| `.github/workflows/ci.yml` | 변경 불요 | FE build 는 별도 워크플로 미존재 — 후속 데스크톱 release 슬라이스에서 검토 |
| Workflow 파일 | 변경 0건 | gradlew assemble + test 그대로 |

**FE CI 부재 인지** — 현재 `ci.yml` 은 `./gradlew assemble + test` 만
수행한다. clients/desktop typecheck / lint / build 는 CI 가 검증하지
않으며, 본 슬라이스도 동일 정책 유지. 후속 데스크톱 release 슬라이스
또는 별도 FE CI 슬라이스에서 도입 권고.

---

## 5. 모니터링 / 운영

### 5.1 메트릭 (Prometheus)

신규 batch endpoint 자동 노출 메트릭:
- `http_server_requests_seconds_count{uri="/inventory/balances/batch", method="POST"}`
- `http_server_requests_seconds_sum{...}` — latency 추적

권고 추가 메트릭 (후속 슬라이스):
- `inventory_balance_batch_request_size{p50,p95,p99}` — productIds 배열
  크기 분포 (대량 호출 가드 임계 결정용)
- `inventory_balance_batch_warehouse_count{p50,p95}` — warehouseIds 배열
  크기

### 5.2 Grafana 대시보드

- 기존 inventory-service 패널이 batch endpoint 자동 포함
- 별도 패널 신설 불요 (본 슬라이스 한정)

### 5.3 알람

- 신규 알람 불요. 기존 inventory-service SLO 임계 그대로 적용
- batch endpoint 가 N+1 쿼리 위험 가지므로 BE IT 에서 N=20 시 응답
  100ms 이하 검증 권고 (QA 협조)

---

## 6. 후속 슬라이스 권고 (우선순위)

본 슬라이스 종료 후 도입 권고 우선순위. PR #19 권고 (Slip 2nd /
인쇄 보안 / Partner Service Q9 / Admin UUID) 를 갱신하여 **디자인 토큰
점진 적용** 신규 권고를 추가한다.

| # | 슬라이스 | 영역 | 비고 |
|---|---|---|---|
| 1 | **Slip 2nd HISTORY** (수정 사유 + 팀장 승인 + 시점별 복원) | BE + FE | Plan §3.1 / 882a766 와 본 슬라이스 lifecycle 연계 |
| 2 | **인쇄 보안 강화** (권한 분리 + 워터마크 + 감사 로그 + PDF 차단) | BE + FE + DevOps | 본 리포트 §3 + PR #19 review.md §2.2. DispatchView 세로 변경 후에도 잔여 위험 동일 |
| 3 | **Partner Service Q9** (전잔/후잔/할인율/감리주소 BE 도메인 확장) | BE | accounting-service 와 통합 |
| 4 | **Admin UUID 화면** (`/admin/system/objects` MASTER/DEVELOPER 한정) | FE + DevOps | gateway `/api/admin/**` 신설 + role 필터 강제 |
| 5 | **디자인 토큰 전 컴포넌트 적용** (현재 SlipFormPage + 신규 3종만 — 16 컴포넌트 점진 migration) | FE + Designer | Q5=B 결정 후속. 16 컴포넌트별 visual regression 테스트 의무 |
| 6 | **모바일 듀얼 앱** (Phase 6 — 창고원/거래처 분리) | 신규 영역 | 모바일은 디자인 토큰 mobile alias 별도 검토 필요 |
| 7 | **전자서명 링크 자동 발행** (Plan §3.1 — 처리완료 시 자동) | BE + Notification | Phase 5 |
| 8 | **WebSocket 실시간 동기화** (Phase 5 — Dashboard 실시간 카운터) | BE + FE | Phase 5 Notification 연계 |
| 9 | **FE CI 도입** (typecheck + lint + build + Storybook 빌드 검증) | DevOps | 본 슬라이스 §4 인지 — 후속 처리 |
| 10 | **SCA / Dependabot 도입** (NPM supply chain 자동 알림) | DevOps | 본 슬라이스 §2.3 인지 — 후속 처리 |

---

## 7. Plan 대비 의도적 변경 (Q1~Q6)

| Q | 결정 | 본 리포트 영역 |
|---|---|---|
| Q1 | 라인 1·2·3 넘버링 + 행 클릭 선택 (이카운트 패턴) | FE 영역 |
| Q2 | 라인 삭제 버튼 (행 끝 ⊗ 아이콘) | FE 영역 |
| Q3 | A — `@dnd-kit/sortable` (react-beautiful-dnd 회피) | 본 리포트 §2.3 |
| Q4 | A — `POST /inventory/balances/batch` (선택 N건 1회 호출) | 본 리포트 §1.1 + §5 |
| Q5 | B — 본 슬라이스 화면만 토큰 적용 (SlipFormPage / StockBalanceModal / DispatchView) | 본 리포트 §2.4 + §6.5 |
| Q6 | A — 모달 (page navigation 아님) | FE 영역 |
| 추가 | DispatchView 가로 → **세로** 정정 | 본 리포트 §3.2 |

---

## 8. 결론

- **인프라 변경 0건**. BE 작은 batch endpoint 추가 + FE 큰 리팩토링
  + Designer 토큰 alias append 로 DevOps 영역의 직접 작업은 없음
- **NPM 의존성 3종 (@dnd-kit) 신규 도입**. 라이선스 MIT 호환 OK,
  bundle size 영향 30~50kB 수용 가능. supply chain 위험은 후속 SCA
  슬라이스에서 일괄 처리
- **디자인 토큰 alias 추가** 방식으로 기존 16 컴포넌트 회귀 위험 0건.
  점진 migration 은 후속 슬라이스 권고
- **인쇄 보안 위험은 PR #19 와 동일** — DispatchView 세로 변경만으로는
  권한/워터마크/감사 로그 미적용. 인쇄 보안 강화 슬라이스 우선순위 #2
- 후속 우선순위: Slip 2nd HISTORY → 인쇄 보안 강화 → Partner Service Q9
  → Admin UUID → 디자인 토큰 전 컴포넌트 적용 → 모바일 듀얼 앱 → 전자
  서명 링크 → WebSocket → FE CI → SCA/Dependabot
