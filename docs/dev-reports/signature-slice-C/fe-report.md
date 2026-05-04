# FE Report — signature-slice-C

> **작성**: 2026-05-05 FE agent (PM 디스패치 산출).
> **PR 후보**: PR #23.
> **base commit**: `e7073cd` (Plan + Designer 산출물).

본 보고서는 Slice C (모바일 전자서명) FE 산출물을 정리합니다. BE / QA / DevOps / Designer 와의 contract 는 Plan §2 / Designer `mobile-spec.md` §2 / `components.md` §2.3 충실 반영.

---

## 1. 산출 컴포넌트 (디자인 시스템 신규 2개)

기존 23개 → **25개**.

| # | 컴포넌트 | path | 사용처 |
| --- | --- | --- | --- |
| 24 | `<SignaturePad>` | `clients/web/design-system/src/components/SignaturePad/` | 모바일 서명 캡처 (mock 라우트) |
| 25 | `<SignatureViewer>` | `clients/web/design-system/src/components/SignatureViewer/` | SlipDetailPage + 인수자 view |

각 컴포넌트는 `.tsx` + `.module.css` + `.stories.tsx` + `index.ts` 4 파일 표준 구성.

### 1.1 SignaturePad
- vanilla canvas + `forwardRef` + `useImperativeHandle` (clear/isEmpty/toDataURL/toBlob).
- pointer event 우선 + touch fallback (iOS 13 미만).
- `passive: false` 의무 (Designer mobile-spec.md §3.5).
- devicePixelRatio 스케일 — retina 흐림 방지.
- placeholder 는 CSS pseudo `::before` (Canvas pixel 검사 정확도 유지).
- props: width / height / onChange / disabled / penColor / penWidth / placeholder.

### 1.2 SignatureViewer
- read-only PNG `<img>` + `<dl>` 메타 (서명자/시각/검증코드).
- `size="desktop"` (150×80 PNG + 메타 우측) / `size="fluid"` (100% width + 메타 하단) 2 모드.
- 검증코드 = signatureHash 의 앞 8자 mono (UUID 비공개 가드).

---

## 2. 라우트 변경 (desktop 앱 mock 2개)

`clients/desktop/src/renderer/routes/index.tsx` 에 **AuthGuard 외부** 라우트 2건 추가:

| route | element | 용도 |
| --- | --- | --- |
| `/mobile/d/:token/s/:slipNo` | `MobileSignaturePage` | Designer wireframes.md §1 모바일 서명 페이지 mock |
| `/mobile/share/:shareToken` | `MobileRecipientPage` | Designer §2.2 인수자 view (`?from=signed` 분기) |

Phase 5 nginx 분리 시 sign.samhan-air.com 으로 이관 — 본 슬라이스는 desktop 앱 안 mock 시뮬레이션 유지 (Plan §7 Q8).

---

## 3. SlipDetailPage 확장

신규 "전자서명 정보" 카드 + 무효화 modal:
- `slip.signedAt && slip.signaturePng` 일 때 `<SignatureViewer size="desktop">` + 채널/공유링크/만료 메타 표시.
- 공유링크는 12자 short form + `<CopyButton>` 으로 전체 URL 복사 (`#/mobile/share/{token}`).
- **MASTER only** [서명 무효화] 버튼 (`role === 'MASTER'` 조건). Designer §3.4 권한 매트릭스.
- `<Modal>` 안 reason textarea (≥10자 검증, 0/500 카운터). DELETE 호출 후 `['slip', id]` 쿼리 invalidate → 카드 자동 갱신.
- 미서명 시 안내 문구 (Designer §3.2).

---

## 4. DispatchView 인쇄 통합

기존 `dispatch-signatures` 영역의 "인수자 서명" 셀에 PNG `<img>` + 서명자명 + 날짜 조건부 추가:
- `slip.signaturePng && slip.signerName` 일 때만 노출.
- 미서명 시 기존 라벨만 유지 (회귀 0).
- CSS 토큰: `--print-signature-img-max-w/h/fit` (max-h 18mm / object-fit contain).
- 셀 grid / 폭 변경 **없음** — `feedback_print_design_iteration.md` CSS-only 가드 준수.

> **Designer wireframes.md §4.1 vs 실제 base 코드 차이 메모**: Designer spec 은
> 결재선 5칸 (작성자/검토자/승인자/운전자/인수자) 의 인수자 셀에 PNG 추가 가정.
> 실제 PR #21 base 는 결재선 5칸 (담당부서/담당자/출고인/검수인/결재) + 별도
> `dispatch-signatures` 영역 (용달기사/인수자) 분리. 본 FE 는 **회귀 위험 0** 을
> 우선해 별도 영역의 인수자 셀에 PNG 추가. PNG fit 정책 (max-h 18mm) 은 동일.

---

## 5. API 클라이언트 + mock 확장

### 5.1 신규 `api/signature.ts` (4 함수)
- `recordSignature(token, slipNo, body)` — POST `/public/batches/.../signature`
- `getSignatureShare(shareToken)` — GET `/public/signatures/{token}`
- `getSignatureAdmin(slipId)` — GET `/api/slips/{id}/signature`
- `invalidateSignature(slipId, reason)` — DELETE `/api/slips/{id}/signature`

### 5.2 `api/slip.ts` SlipDetail 확장 (signature 7 필드)
모두 nullable optional 로 추가 — 기존 코드 호환 유지:
- `signedAt` / `signerName` / `signaturePng` / `signatureHash` / `signatureChannel` / `signatureShareToken` / `signatureShareExpiresAt`

### 5.3 `api/mock.ts` 확장
- `MOCK_SIGNATURE_SEED` 상수 + 1×1 PNG fixture.
- `slip-002` (CONFIRMED) 에 시드 spread — SlipDetailPage / DispatchView 양쪽 시연.
- 신규 4 endpoint mock 핸들러:
  - POST `/public/batches/{token}/slips/{slipNo}/signature` → shareToken 발급
  - GET `/public/signatures/{shareToken}` → 인수자 view JSON
  - GET `/slips/{id}/signature` → admin 조회
  - DELETE `/slips/{id}/signature` → 200 (audit 로그는 BE 책임)

---

## 6. CSS 변경 (`styles/global.css`)

신규 영역 3개:
- `@media print` 안 `.dispatch-role-cell .dispatch-role-signature-img/meta` (Designer tokens.md §1.3 인쇄 토큰 인용).
- `.m-mock-frame *` — 모바일 mock 프레임 (375×812 viewport 시뮬레이션 + 모바일 디자인 토큰 fallback).
- `.slip-signature-card-*` — desktop SlipDetailPage 카드 스타일.
- `.dispatch-recipient-sign-cell .dispatch-role-signature-*` — 인쇄 미리보기 (화면) 표시 base.

기존 클래스 / 토큰 변경 없음 — Slice A/B 시각 회귀 0.

---

## 7. UUID 비공개 검증 (`feedback_uuid_no_user_visibility.md`)

| 화면 | UUID 노출? | 검증 |
| --- | --- | --- |
| MobileSignaturePage | URL `{token}/{slipNo}` 만 (UUID X) | path param 만 사용 |
| MobileRecipientPage | URL `{shareToken}` 만 (UUID X) | path param 만 |
| SlipDetailPage 신규 카드 | hash 앞 8자 + shareToken 앞 12자만 표시 | UUID 패턴 0건 |
| DispatchView 인쇄 셀 | 서명자명 + 서명일자 만 | UUID 패턴 0건 |

---

## 8. 검증 결과

| 검증 | 결과 |
| --- | --- |
| `clients/web/design-system && npm run build` | PASS — 75 modules transformed, gzip 17.76 kB |
| `clients/desktop && npm run typecheck` | PASS |
| `clients/desktop && npm run lint` | PASS — 0 errors (기존 warning 1 — Slice A 와 무관) |

---

## 9. 회귀 위험 평가

| 영역 | 위험 | 평가 |
| --- | --- | --- |
| 기존 디자인 시스템 컴포넌트 | 없음 | export 추가만 |
| Slice A/B mobile mini bundle | 없음 | 본 슬라이스는 desktop mock 라우트로 시뮬 |
| DispatchView 인쇄 | 낮음 | 미서명 시 기존 라벨 유지 (조건부 추가) |
| SlipDetailPage 기존 흐름 | 없음 | 카드 + modal 만 추가 (transition 흐름 무관) |
| mock 모드 (VITE_MOCK_MODE=1) | 낮음 | slip-002 만 시드 추가, 기존 7 슬립 흐름 유지 |

---

## 10. 다음 단계 (BE/QA 인계 사항)

1. **BE**: `SlipDetail` 응답에 signature 7 필드 추가 (Plan §3 V5 + JPA). `/public/...` endpoint 4건 구현.
2. **BE**: `signaturePng` 응답 시 `data:image/png;base64,...` dataURL prefix 포함 (FE 가 그대로 `<img src>` 에 사용).
3. **QA**: Designer `mobile-spec.md` §7 체크리스트 (디바이스 5종, 기능 10건, 성능 4건, 보안 4건) 검증.
4. **DevOps**: Phase 5 nginx 분리 시 `/mobile/d/...` → `sign.samhan-air.com/d/...` 라우팅. 본 슬라이스 deferred.
5. **Designer**: DispatchView 인쇄 셀 분리 (Slice A 의 결재선 5칸 vs Designer wireframes.md §4 가정 차이) 차후 spec 동기화.

---

## 11. 변경 파일 요약

신규 (10):
- `clients/web/design-system/src/components/SignaturePad/{SignaturePad.tsx,.module.css,.stories.tsx,index.ts}`
- `clients/web/design-system/src/components/SignatureViewer/{SignatureViewer.tsx,.module.css,.stories.tsx,index.ts}`
- `clients/desktop/src/renderer/routes/MobileSignaturePage.tsx`
- `clients/desktop/src/renderer/routes/MobileRecipientPage.tsx`
- `clients/desktop/src/renderer/api/signature.ts`
- `docs/dev-reports/signature-slice-C/fe-report.md` (본 문서)

수정 (5):
- `clients/web/design-system/src/index.ts` — export 추가
- `clients/desktop/src/renderer/api/slip.ts` — SlipDetail 7 필드 추가
- `clients/desktop/src/renderer/api/mock.ts` — 시드 + 4 핸들러
- `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` — 서명 카드 + 무효화 modal
- `clients/desktop/src/renderer/routes/index.tsx` — 모바일 라우트 등록
- `clients/desktop/src/renderer/print/DispatchView.tsx` — 인수자 셀 PNG
- `clients/desktop/src/renderer/styles/global.css` — 모바일 mock + signature card + print PNG
