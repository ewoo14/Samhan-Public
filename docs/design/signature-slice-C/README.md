# Signature Slice C — 모바일 전자서명 디자인 산출

> **작성**: 2026-05-05 Designer agent.
> **대상**: PR #23 (C1+C2 MVP — 서명 저장 + 모바일 페이지 + 인수자 share + DispatchView 인쇄 통합).
> **base**: Slice B (`docs/design/notification-slice-B/`) 의 mobile mini bundle 자체완결 정책 그대로 계승.

---

## 1. 산출 요약

본 디렉토리는 Slice C (모바일 전자서명) 의 **5-team 디스패치용 디자인 spec + mock + 캡처** 입니다.

| 산출 | 파일 |
| --- | --- |
| 디자인 요약 | `README.md` (본 파일) |
| 4 화면 wireframe | `wireframes.md` |
| 신규 토큰 (canvas / meta) + mobile mini bundle 동기화 | `tokens.md` |
| 신규 컴포넌트 spec (`SignaturePad`, `SignatureViewer`) | `components.md` |
| 4 시나리오 UX flow | `ux-flow.md` |
| Slice B mobile-spec 확장 (`signature.js` mini bundle ≤6KB) | `mobile-spec.md` |
| 모바일 + 데스크톱 4 mock | `mocks/01_mobile_signature_page.html` ~ `04_dispatch_print_with_signature.html` |
| QA 첨부용 캡처 4장 | `screenshots/01_*.png` ~ `04_*.png` |

---

## 2. 핵심 디자인 결정

### 2.1 mobile mini bundle 자체완결 정책 유지
Slice B 에서 정의한 `mobile.css`/`mobile.js` (≤12KB total) 자체완결 원칙을 본 슬라이스에서도 유지합니다. **디자인 시스템 (`@samhan/design-system`) 의존 X**. Canvas 서명 캡처를 위해 신규 `signature.js` mini bundle 만 추가 (≤6KB gzip, 의존성 zero, signature_pad lib 미사용).

| 파일 | 사이즈 (gzip) | 비고 |
| --- | --- | --- |
| `mobile.css` (Slice B 계승) | ≤4KB | canvas 클래스 추가 후 ≤4.5KB |
| `mobile.js` (Slice B 계승) | ≤4KB | 변경 없음 |
| `signature.js` (Slice C 신규) | ≤6KB | dynamic import — 서명 페이지 진입 시만 로드 |
| **total (서명 페이지)** | **≤14.5KB** | budget 내 |

### 2.2 Canvas 서명 영역 크기
- 좁은 화면 (320~374px): **320×200px** (좌우 padding 12px 후 280×200 사용 가능, 단 spec 상 320 고정)
- 넓은 화면 (375~480px): **400×200px** 까지 확장
- 펜 두께 2.5px, 색상 검은색 #000 (대비 ↑)
- touch + mouse 통합 이벤트, `passive: false` (preventDefault 로 스크롤 차단)

### 2.3 PNG 표시 영역 (인수자 view + DispatchView 인쇄)
- 모바일 view: max-width 100% (responsive), 비율 유지
- DispatchView 인쇄: 인수자 결재 셀 (60mm × 30mm) 안에 max-width 55mm 로 fit
- 메타 정보 (서명자명 / 시각 / hash 앞 8자리) 작은 글씨로 함께 표시

### 2.4 무효화 권한 분리 (UI 가시성)
- MASTER 만 SlipDetailPage 에 [무효화] 버튼 노출 (MANAGER 는 read-only 카드만)
- 무효화 confirm dialog 에 reason textarea 의무 (≥10자)

### 2.5 UUID 미노출 — 토큰만 노출
- batch token (base64url) 과 share token (base64url) 만 URL path 노출
- slipId / batchId / signatureId UUID 는 DOM data-* 안에도 절대 미노출
- 서명자명 + 슬립번호 + 거래처명 등 비즈니스 식별자만 화면 표시

---

## 3. Slice B → Slice C 변경 분량 요약

| 영역 | 변경 |
| --- | --- |
| `mobile.css` | canvas 클래스 + 서명 완료 상태 클래스 추가 (~50 LOC, ≤0.5KB gzip) |
| `mobile.js` | 변경 없음 (`signature.js` 별도 mini bundle) |
| `signature.js` (신규) | Canvas + touch + base64 + SHA-256 (Web Crypto) (~200 LOC, ≤6KB gzip) |
| 디자인 시스템 (`@samhan/design-system`) | **변경 없음** (mobile mini bundle 자체완결) |
| `SlipDetailPage` (desktop) | 서명 정보 카드 1개 + [무효화] 버튼 1개 (기존 토큰 재사용) |
| `DispatchView` 인쇄 (desktop) | 인수자 결재 셀 안에 PNG `<img>` 조건부 렌더 + 메타 (CSS-only 변경) |

---

## 4. 회고 가드 적용

- **`feedback_pr_qa_screenshots.md`** — Designer mock 4종 모두 Edge headless 캡처 첨부 (`screenshots/`).
- **`feedback_uuid_no_user_visibility.md`** — 4 mock 모두 UUID 미노출 검증 완료. 토큰은 URL path 만, slipNo / 서명자명 / hash 앞 8자리만 노출.
- **`feedback_korean_commits.md`** — 본 디렉토리 모든 마크다운 + mock HTML label 한국어.
- **`feedback_print_design_iteration.md`** — DispatchView 인쇄 변경은 인수자 결재 셀 안 PNG `<img>` 조건부 추가 + 메타 표시만 (CSS-only 변경 우선, 기존 `--print-approval-*` / `--print-signature-*` 토큰 재사용).
- **`feedback_function_documentation.md`** — README/spec 마크다운 본문 한국어, 컴포넌트 Javadoc 은 후속 BE/FE agent 가 구현 시 한국어로 작성.

---

## 5. 후속 5-team 디스패치 시 참고

| 팀 | 본 디렉토리 인용 권장 파일 |
| --- | --- |
| BE | `mobile-spec.md` §2 (API contract — body/response shape), `wireframes.md` §1 (서명 페이지 input/output) |
| FE (desktop) | `wireframes.md` §3-4 (SlipDetailPage 카드, DispatchView 인쇄), `components.md` §2 (`SignatureViewer`) |
| FE (mobile mini bundle) | `mobile-spec.md` 전체, `components.md` §1 (`SignaturePad`), `mocks/01-03` |
| Designer | (본 산출 — 추가 디스패치 없음) |
| QA | `mocks/` + `screenshots/` 비교 회귀 + UUID 미노출 DOM inspector 검증 |
| DevOps | `mobile-spec.md` §5 (CSP `script-src 'self'` 안 Web Crypto 정상 동작 확인) |

---

## 6. 미해결 사항

없음. plan §7 Open Question 8건 모두 사용자 확정 (2026-05-05).
