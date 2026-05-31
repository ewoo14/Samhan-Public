## Claude 5-agent 사이클 1 통합 리뷰 (head `66c441cc`)

> tech-manager agent 가 BE / FE / Designer / QA (후공정) / DevOps 5 agent 결과 종합.

### 결함 종합 표 (CRITICAL → P1 → P2 → Nit)

| # | 출처 | 우선순위 | 위치 | 내용 | 처리 권고 |
|---|---|---|---|---|---|
| 1 | FE | **CRITICAL** | `SalesPartnerOrderDetailPage.tsx:158-159` | `window.open('/api/v1/.../print', '_blank')` 이 axios interceptor 우회 → JWT Bearer 헤더 + X-Partner-Code 미전달 → 운영 401/403. **기능 차단** | Claude fix: axios `blob` fetch + `URL.createObjectURL` 패턴 또는 cookie 세션 계약 |
| 2 | BE/QA/Designer | P1 | `PartnerOrderPrintService.java:285` | "거래처명" 자리에 `partnerCode` 출력. `partnerName` 필드 부재 — 인쇄 양식 사업자 확인 불가 | Claude fix: `PartnerOrder.partnerName` 스냅샷 필드 추가 또는 `PartnerAuthClient` 조회 |
| 3 | BE | P1 | `PartnerOrderPrintIT.java:88-89` | `charset=UTF-8` IT 검증이 HTML `<meta>` 검색, Content-Type 헤더 미검증 | Claude fix: `content().contentType("text/html;charset=UTF-8")` |
| 4 | Designer/QA | P1 | `PartnerOrderPrintService.java:292` | `order.getStatus().name()` 영문 enum 노출 (`CONFIRMING` 등) | Claude fix: `statusLabel()` 한국어 매핑 헬퍼 |
| 5 | Designer | P1 | `print.module.css:16` + BE inline CSS | `'Pretendard'` 단일 weight, `Pretendard Variable` 우선 순서 누락 — A4 인쇄 weight quality | Claude fix: `'Pretendard Variable', Pretendard, ...` 순서 |
| 6 | FE | HIGH | `print.module.css` 미사용 | BE inline HTML 반환 — FE css 미참조, 번들에 dead code | Claude fix: 파일 제거 또는 후속 슬라이스 명시 |
| 7 | FE | MEDIUM | `routes/index.tsx:372-373` | `/sales/partner-orders` 라우트 RoleGuard 누락 — PARTNER role 데스크탑 진입 가능 | Claude fix: `RoleGuard allow={...}` 추가 |
| 8 | BE | P2 | `PartnerOrderPrintService.java:79` | `totalAmount / 11` 부가세 역산 가정 명시 부재 | Javadoc 보강 |
| 9 | BE | P2 | `PartnerOrderPrintService.java:306` | `categoryLabel` default 영문 raw, 한국어 fallback 부재 | "기타" fallback |
| 10 | FE | HIGH | `handlePrint` L157 | `if (!orderId) return` dead guard | 제거 |
| 11 | FE | LOW | `AuthSnapshot` | `partnerCode` 필드 부재 (D-1 fix 연동) | preload IPC 확장 |
| 12 | Designer | P2 | `print.module.css @media print` | `page-break-inside: avoid` 누락 (테이블 행 잘림) | `tr { page-break-inside: avoid }` |
| 13 | Designer | P2 | PNG 02 날인란 | "사용자 확인"/"거래처 확인" — legacy "공급자/수신자" 형식 검토 | 사이클 2 사용자 캡처 후 |
| 14 | Designer | P2 | PNG 01 버튼 위계 | 인쇄 secondary 정합 — 의도적 secondary 명시 | 정상 / 코멘트 |
| 15 | Designer | P3 | print preview shadow | 미리보기 box-shadow 부재 | 후속 |
| 16 | QA | MEDIUM | `PartnerOrderPrintIT` saveOrder orderNo | 슬래시 vs 하이픈 포맷 불일치 가독성 | 일관 정리 |
| 17 | QA | LOW | dev-report §6 Verification | `실행 예정` → 실제 PASS 갱신 필요 | 갱신 |
| 18 | BE/DevOps/QA | Nit | `X-Partner-Code` 상수 분산 | controller + IT 리터럴 중복 | `HttpHeaderConstants.PARTNER_CODE_HEADER` 통합 |
| 19 | BE | Nit | `confirmedAt` null fallback | DRAFT 상태 인쇄 시 날짜 공란 | `createdAt` fallback |

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | 사이클 2 필요 (P1 2건) |
| FE | **CRITICAL 1건 (D-1) — 사이클 1 Claude fix 필수** + HIGH/MEDIUM 4건 |
| Designer | 사이클 2 필요 (P1 3건) |
| QA | 사이클 2 필요 (HIGH 1건 = BE-1) |
| DevOps | APPROVE (X-Partner-Code 통합 Nit) |

### TM 결정 (5회차 워크플로우 첫 적용)

- **종합**: 사이클 1 Claude fix 필수 (FE D-1 CRITICAL 기능 차단). 양쪽 합의 P1 6건 일괄 처리.
- **Claude fix 후보 (1c 단계)**:
  1. FE D-1 (CRITICAL): `handlePrint` → axios blob fetch + `URL.createObjectURL` 새 탭. X-Partner-Code 헤더 axios 에서 자동 주입 (or auth.partnerCode 활용)
  2. BE-1: `PartnerOrder.partnerName` 추가 또는 PartnerAuthClient 조회. Flyway V7 partner_name 컬럼 add
  3. BE P1-2: IT Content-Type 검증 강화 (`contentType("text/html;charset=UTF-8")`)
  4. Designer D1 / QA BE-2: `statusLabel()` 한국어 매핑 (`PartnerOrderStatus.label()` enum method 또는 service helper)
  5. Designer D2: BE inline HTML + print.module.css `'Pretendard Variable'` 우선
  6. FE D-3: `print.module.css` 제거 (BE inline 사용) 또는 후속 명시 (route 전환 시 재사용)
  7. FE D-4: `/sales/partner-orders` 라우트 RoleGuard 추가
  8. (P2/Nit 묶음): X-Partner-Code 상수 통합 + categoryLabel "기타" + page-break-inside + dev-report §6 갱신
- **Codex 2a review 대기**: Claude fix 후 push → Codex 5-agent 가 위 fix 정합성 + 신규 검증
- **사이클 2c Codex fix**: Codex 가 valid 평가한 미처리 항목 + 자체 발견 보완

**tech-manager — 2026-05-17**
