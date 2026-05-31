## designer 사이클 1 리뷰 (head `97afca70`)

### 결함 표

| # | 심각도 | 위치 | 내용 | 수정 방향 |
|---|---|---|---|---|
| D-01 | **P0** | PNG 01/02/03/04 전체 | QA 스크린샷 한글 인코딩 깨짐 — 모든 PNG 한글이 `二쉼ц????젤 ?뺐씰`, `窠ь젲?맷팍` 등 완전히 깨짐. Playwright 캡처 환경 (Windows headless Chromium) Pretendard 폰트 미로드 또는 locale 미설정 추정. 실제 UI 렌더 검증 불가 | `use.locale: 'ko-KR'`, font preload, `--font-render-hinting=none` 추가 후 재캡처 의무 |
| D-02 | **P0** | PNG 02 | QA 스크린샷이 실제 UI 가 아닌 Playwright 디버그 패널 (DELETE /api/v1/partner-orders/2026-05-17-1 + 204 No Content). 사용자가 보는 화면(목록 페이지 이동 결과) 아님 | 삭제 성공 후 `/sales/partner-orders` 목록 페이지로 navigate 된 화면 캡처로 교체 |
| D-03 | **P0** | PNG 03/04 | PNG 03/04 가 견적→주문 전환 화면이 아닌 Playwright 디버그 패널 — `source_estimate_id`, `409 Conflict`, `PARTNER_ORDER_FROM_ESTIMATE_ALREADY_CONVERTED` API 응답 raw. 실제 desktop UI 0건. 또한 QA 발견 PNG 03 success 시나리오가 아닌 409 표시 — 내용 자체도 오류 | 견적서 상세에서 "주문으로 전환" 버튼 → 성공/오류 피드백 화면 캡처로 교체 |
| D-04 | P1 | `SalesPartnerOrderDetailPage.tsx` L208-217 | 삭제 버튼 `variant="secondary"` — destructive action 시각 cue 미적용. design-system `variant="danger"` 적용 (FE-1 동일) | `variant="danger"` |
| D-05 | P1 | `SalesPartnerOrderDetailPage.tsx` L524-535 | 삭제 확인 dialog 확인 버튼도 `variant="primary"` (파란색). 파괴 확인은 red/danger 필수 (이카운트 UX 표준) (FE-2 동일) | `variant="danger"` |
| D-06 | P1 | `SalesPartnerOrderDetailPage.tsx` L540-542 | 삭제 dialog 본문 — `주문서 {orderNumber}를` 조사 오류. orderNumber 가 숫자/영문 끝 시 `을` 자연스러움. 주문번호 시각 강조 없음 | `<strong>{orderNumber}</strong>을(를)` 강조 + 받침 조건부 |
| D-07 | P2 | `SalesPartnerOrderDetailPage.tsx` L192-218 | PARTNER 역할 삭제 버튼 비노출 시나리오 PNG 미캡처 | PNG 05 추가: PARTNER 세션 — 수정/삭제 버튼 없음 캡처 |
| D-08 | P2 | `sales.module.css` L989-998 | `successBanner` `--color-success-*` scale 사용, 동 파일 `.statusSent` 는 `--state-success-bg` 사용 — 토큰 키 혼용 | `--state-success-bg` / `--state-success` 통일 또는 DS `--color-success-*` 정식 등록 |
| D-Nit-1 | Nit | `SalesPartnerOrderDetailPage.tsx` L64-65 | 삭제 버튼과 "← 목록" ghost link 시각 분리 없음 (삭제 ↔ 목록 이동 혼동 여지) | gap / margin-left auto 시각 분리 |

### 긍정 사항

- inline style 0건 (사이클 4.5/5.5 회고 완전 준수)
- `.tdLeft` / `.expandedComponentText` 패턴 일관 (L303, L315)
- `deleteErrorMessage` → `errorBanner` `role="alert"` 접근성
- UUID 사용자 비노출 가드 — orderNumber (`2026/05/17-1`) 노출, UUID 미노출
- 인쇄 양식 영향 없음
- design-system 클래스 일관

### 종합

**사이클 2 필요** — P0 3건 (PNG 인코딩 + raw API) 디자인 검증 자체 불완전. P1 3건 destructive variant 필수. PNG 4장 재생성 + 삭제 버튼 danger + 조사 fix 후 재제출.

**designer agent — 2026-05-17**
