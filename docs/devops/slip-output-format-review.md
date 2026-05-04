# Slip Output Format 슬라이스 — DevOps 검토 리포트

> 슬라이스: slip-output-format-slice | base: 7c1b298 | 작성일: 2026-05-04 | 작성: DevOps Agent (worktree `agent-a39f9182dfc58f45e`)

본 리포트는 BE 작은 변경 (product-service / slip-service `lookup-by-model` endpoint)
+ FE 큰 리팩토링 (clients/desktop IA 분리, 11 화면, UUID 전면 제거, 인쇄 양식 2종,
slip 라이프사이클 transition) 슬라이스에 대한 DevOps 영역 검토 결과이다.
신규 인프라 자원은 0건이며, 본 리포트는 **인쇄 보안 위험 분석 + UUID admin 화면
분리 권고 + 후속 슬라이스 권고** 에 집중한다.

---

## 1. 인프라 변경

### 1.1 추가/수정 자원
- **인프라 자체 변경 없음** — 기존 7 마이크로서비스 재활용. 신규 모듈 0건
- DB 스키마 변경 없음 (Q9=B 결정 — BE 도메인 확장은 후속 슬라이스로 분리)
- API Gateway 라우트 변경 없음 (`/api/products/**`, `/api/slips/**` 기존 라우트
  로 신규 endpoint 자동 노출)

### 1.2 점검 결과 (실제 파일 확인)

| 항목 | 위치 | 상태 |
|---|---|---|
| `/api/products/**` 라우트 | `services/api-gateway/src/main/resources/application.yml:46-52` | 기존 — `lb://product-service` + StripPrefix=1 + JwtAuthentication |
| `/api/slips/**` 라우트 | `services/api-gateway/src/main/resources/application.yml:38-44` | 기존 — `lb://slip-service` + StripPrefix=1 + JwtAuthentication |
| Electron CORS 호환 | `services/api-gateway/.../config/CorsConfig.java:44-51` | 기존 — `app://com.samhanair.logis.desktop`, `file://*`, `http://localhost:*` 패턴 등록 완료 |
| Docker Compose | `infrastructure/docker-compose.yml` | 변경 불필요 |
| GitHub Actions CI | `.github/workflows/ci.yml` | 변경 불필요 (assemble + test 그대로) |

신규 endpoint 가 기존 prefix (`/api/products`, `/api/slips`) 하위에 추가되므로
gateway 라우트 수정은 일절 필요 없다. CORS 도 데스크톱 origin 패턴이 이미
등록되어 있어 추가 조정 불요.

---

## 2. 인쇄 보안 검토 (이미지 1, 2 거래명세서/출고전표 인쇄)

본 슬라이스에서 도입되는 두 종 인쇄 양식 (거래명세서, 작업지시서/출고전표) 은
종이/PDF 형태로 시스템 외부로 정보가 흐르는 첫 경로이다. 보안 위험과
권고를 정리한다.

### 2.1 보안 위험

1. **거래처 / 단가 정보의 외부 유출**
   - 거래명세서 인쇄 시 거래처 정보 (사명/주소/사업자번호) + 단가 + 합계
     가 종이로 외부 노출됨. 거래처별 채권/매출 정보가 그대로 유통됨.
2. **권한 미분리**
   - 본 슬라이스는 모든 인증 사용자가 인쇄 가능 (Q5/Q6 권한 정책 미명시).
     WAREHOUSE 직원이 거래명세서를 인쇄하거나, SALES 직원이 작업지시서를
     인쇄하는 등 의도하지 않은 권한 확대 가능.
3. **PDF 저장 → 외부 공유**
   - Electron `window.print()` 는 OS 의 PDF/Printer 다이얼로그를 호출.
     "Save as PDF" 선택 시 사용자 로컬에 PDF 저장 후 메일/메신저로 외부
     공유 가능. 시스템 차원의 통제 불가.
4. **PrintScreen / 화면 캡처**
   - 인쇄 권한 분리를 추가해도 화면 표시 자체에는 정보 노출. PrintScreen
     으로 우회 가능. 워터마크 외에는 기술적 차단이 어려움.
5. **감사 추적 불가**
   - 누가 / 언제 / 어떤 슬립을 인쇄했는지 기록 없음. 정보 유출 사고 발생
     시 추적 불가능.

### 2.2 권고 (후속 슬라이스)

| # | 항목 | 설명 | 우선순위 |
|---|---|---|---|
| 1 | 인쇄 권한 분리 | 거래명세서: SALES/MANAGER/MASTER. 작업지시서: WAREHOUSE/MANAGER/MASTER. SlipController 또는 FE Route Guard 양쪽에서 강제 | High |
| 2 | 인쇄 워터마크 | 인쇄 view 에 사용자명/시각/슬립번호 워터마크 (회수/추적용 — 화면 우상단 + 본문 대각선 옅게) | High |
| 3 | 인쇄 감사 로그 | `SlipPrintEvent` 도메인 (slipId/printerUserId/printType/printedAt/clientIp). logging-service 발행 | High |
| 4 | PDF 저장 차단 | Electron 메인 프로세스에서 `webContents.on('before-input-event')` + `window.print({silent: true, deviceName})` 또는 `printToPDF` API 차단 | Medium |
| 5 | 거래처 정보 마스킹 | 저권한 사용자에게는 사업자번호 일부 `***`, 주소는 시/구만 표시 | Medium |
| 6 | 인쇄 횟수 제한 | 동일 슬립 일정 횟수 초과 인쇄 시 MANAGER 승인 요구 | Low |

---

## 3. UUID admin 화면 분리 권고 (Q6=A 후속)

### 3.1 본 슬라이스 (FE 가 처리)
- 모든 일반 사용자 화면에서 UUID 노출 제거 (memory: `feedback_uuid_no_user_visibility.md`)
- 비즈니스 식별자 (슬립번호 / 창고 코드 / 모델명 / 거래처명) 만 노출
- API 응답의 UUID 는 React 컴포넌트 `key` props 로만 사용, render 미수행

### 3.2 후속 슬라이스 (DevOps + FE 영역)
- **`/admin/system/objects` 신규 화면**
  - MASTER / DEVELOPER role 만 접근 가능 (Route Guard + Gateway role 필터)
  - UUID + 시스템 메타데이터 (created_at / created_by / updated_at / updated_by /
    version / deleted_at) 표시
  - 도메인별 raw 조회 (Slip / Product / Inventory / User / Partner)
  - UUID 직접 입력 → 단건 조회 + 관련 entity graph 시각화
  - 사용처: 디버깅, 데이터 무결성 점검, 고객 지원 시 ID 기반 직접 조회
- AppLayout 사이드바에서 admin role 한정으로만 메뉴 표시
- gateway 라우트는 기존 `/api/users`, `/api/slips` 등 재활용 — 별도
  `/api/admin/**` 라우트 신설하여 role 필터 (MASTER/DEVELOPER) 강제 권고
- 본 슬라이스에는 미포함 — Q9=B 의 partner-service 도메인 확장과 함께 도입

---

## 4. CI 영향

| 항목 | 영향 | 비고 |
|---|---|---|
| `./gradlew assemble` | < 5초 추가 | BE: lookup-by-model endpoint + DTO 추가 — 컴파일 부담 미미 |
| `./gradlew test` | < 30초 추가 | product-service / slip-service unit + IT 1~2건 추가 예상 |
| `npm run build` (clients/desktop) | bundle 약간 증가 | 612kB → 약 700kB 추정. 11 화면 + 인쇄 양식 2종 + lifecycle UI |
| electron-builder Windows .exe | 본 슬라이스 미수행 | 별도 release 슬라이스에서 처리 |
| Workflow 파일 | 변경 불요 | `.github/workflows/ci.yml` 수정 없음 |

---

## 5. 모니터링 / 운영

### 5.1 메트릭 (Prometheus)
- `product_service_lookup_by_model_total{result="hit|miss"}` — 사용자 모델명
  입력 패턴 추적. miss 비율 높으면 모델명 정규화 또는 자동완성 도입 검토
- `slip_service_lookup_product_total{result="hit|miss"}` — 동일 목적
- `slip_print_total{type="invoice|workorder", role="..."}` — 후속 슬라이스
  에서 추가될 인쇄 감사 메트릭

### 5.2 로깅
- 인쇄 이벤트는 logging-service 로 발행 권고 (후속) — Elasticsearch 색인 →
  Kibana / Grafana 에서 거래처별, 사용자별 인쇄 추적 대시보드 구성

### 5.3 알람 (후속)
- 동일 사용자가 단시간 내 N 건 이상 거래명세서 인쇄 → MANAGER 알림
- 비업무 시간대 (야간/주말) 인쇄 → MASTER 알림

---

## 6. 후속 슬라이스 권고 (우선순위)

1. **Slip 2nd slice — HISTORY snapshot + 출고일 변경**
   - Plan §3.1 의 HISTORY 복원 기능. 수정 사유 + 팀장 승인 + 시점별 복원
   - 882a766 (slip HISTORY restore) 와 본 슬라이스의 transition 도메인 연계
2. **인쇄 보안 강화**
   - 위 §2.2 의 6개 권고 일괄 도입. SlipPrintEvent 도메인 + 권한 분리 +
     워터마크 + PDF 차단
3. **거래처 잔액 / 채권 view (Q9 후속)**
   - partner-service (Phase 4) 도메인 확장 — 전잔/후잔/할인율/감리주소/
     입금예정일. accounting-service 와 통합
4. **Admin UUID 화면**
   - `/admin/system/objects` MASTER/DEVELOPER 한정. §3.2 와 함께
5. **전자서명 링크 자동 발행**
   - Plan §3.1 의 "처리완료 시 서명 링크 자동 발행". Phase 5 Notification 연계
6. **WebSocket 실시간 동기화**
   - Phase 5 Notification + Dashboard 실시간 카운터
7. **모바일 듀얼 앱**
   - Phase 6 — 창고원 / 거래처 분리 모바일 앱

---

## 7. Plan 대비 의도적 변경 (Q1~Q9)

| Q | 결정 | 본 슬라이스 반영 |
|---|---|---|
| Q1 | A — 새 슬라이스 (PR #18 머지 후) | 적용 (base 7c1b298) |
| Q2 | A — POST internal `lookup-by-model` + GET by-model wrapper | BE 영역 |
| Q3 | B — onBlur lookup | FE 영역 |
| Q4 | A — `window.print()` + `@media print` CSS | FE 영역 |
| Q5 | A — 1 큰 슬라이스 2주 | PM 영역 |
| Q6 | A — 모든 화면 UUID 제거 (admin 분리는 후속) | FE + 본 리포트 §3 |
| Q7 | A — StockTransfer FE 완전 (목록 + 작성 + lifecycle) | FE 영역 |
| Q8 | A — SlipDetailPage lifecycle transition 본 슬라이스 포함 | FE 영역 |
| Q9 | B — BE 도메인 확장 후속 분리 (Partner Service Phase 4 와 함께) | 본 리포트 §6.3 |

---

## 8. 결론

- 인프라 변경 0건. BE 작은 endpoint 추가 + FE 큰 리팩토링으로 DevOps
  영역의 직접 작업은 없음
- **인쇄 보안은 본 슬라이스의 가장 큰 잔여 위험.** 후속 슬라이스에서
  권한 분리 + 워터마크 + 감사 로그 + PDF 차단 도입 필수
- Admin UUID 화면 분리는 Q9 후속 (Partner Service) 와 묶어서 도입 권고
- 후속 우선순위: Slip 2nd HISTORY → 인쇄 보안 → Partner Service Q9 →
  Admin UUID → 전자서명 링크 → WebSocket → 모바일 듀얼 앱
