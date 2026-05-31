# Designer Cycle 1 — audit Slice C PR #260

**작성**: 2026-05-19 Designer agent  
**대상**: PR #260 (audit Slice C — arologis-desktop hidden 정책 + signature-slice-C mobile-spec 정합)  
**범위**: design-system token / 인쇄 양식 / 사이드바 영향 + mobile-spec.md 와 검증 spec 정합 확인

---

## 1. design-system token 영향 평가

`clients/web/design-system/src/tokens/tokens.css` 확인 결과:

- `--print-signature-w / h / gap` (3개) 은 Slice A 범주(용달기사/인수자 서명 기본 치수)로 기존 등록 완료.
- `docs/design/signature-slice-C/tokens.md` §1.2 에서 정의한 **데스크톱용 `--slip-signature-*` 토큰 11개** 및 **`@media print` `--print-signature-img-max-w/h/fit`, `--print-signature-meta-*` 4개** 는 현재 `tokens.css` 에 **미등록**이다.
- 모바일 mini bundle 전용 `--signature-canvas-*`, `--signature-meta-*`, `--signature-complete-*` 토큰은 `mobile.css` 하드코딩 정책이므로 `tokens.css` 미등록이 **정상**이다.
- **결론**: `tokens.css` 에 `--slip-signature-*` 11개 + `@media print --print-signature-img-*` / `--print-signature-meta-*` 4개가 FE 구현 시 추가되어야 한다. PR #260 범위 내 FE 미구현이므로 현재는 결함 아님 — FE 구현 PR 발행 전 반드시 병행 등록 필요.

## 2. 인쇄 양식 영향 평가

`wireframes.md` §4, `ux-flow.md` §4.4, `tokens.md` §1.3 교차 검토:

- 결재선 5칸 그리드(60×30mm) **변경 없음** — `feedback_print_design_iteration.md` 가드 준수 확인.
- 인수자 셀 CSS-only 변경(`max-height: 18mm img + 메타 8pt / 7pt`)은 기존 `--print-approval-*` 치수 범위 내이며 픽셀 회귀 없음.
- 서명 없는 슬립 인쇄 시 빈 셀 유지 조건이 wireframe §4.2에 명시되어 Slice A 양식 100% 보존.
- **이상 없음**.

## 3. 사이드바 영향 평가

`docs/design/sp-d1-dynamic-rbac/arologis-desktop-policy.md` 검토:

- arologis-desktop AROLOGIS_MASTER / AROLOGIS_MANAGER 양쪽 모두 전체 메뉴 접근 — role 분기 불필요.
- SP-D1 hidden 정책 3대 전제(멀티 role, 동적 RBAC, 카테고리 헤더 DOM 제거) 중 해당 사항 없음.
- `routes/index.tsx` SP-D1 hidden 미적용이 **의도적 결정**으로 문서화되어 있어 UI 변경 0건이 정당하다.
- **이상 없음**.

## 4. mobile-spec.md 와 검증 spec 정합 확인

`qa/playwright/tests/signature-c/signature-c-smoke.spec.ts` 와 `docs/design/signature-slice-C/mobile-spec.md` 교차 검토:

| mobile-spec 항목 | spec 내용 | spec 대응 여부 |
|---|---|---|
| §2.1 POST API 응답 UUID 미포함 | `id / slipId / batchId` absent | SC-1, SC-5 `toBeUndefined()` 정합 |
| §2.1 Response 400 hash mismatch | clientHash 불일치 | SC-2 정합 |
| §2.1 Response 410 만료 | batch token 만료 | SC-4 정합 |
| §3.6 PNG ≤50KB 가드 | BE bytea ≤51200 bytes | SC-3(60KB→400), SC-8(49999→200) 경계값 정합 |
| §3.7 SHA-256 Web Crypto | 64자 hex, 결정적, avalanche | 별도 `describe` 블록 완전 검증 정합 |
| §3.4 canvas 사이즈 분기 | 320 / 400 viewport 분기 | SC-10 `test.fixme` — FE 미구현 표기 정합 |
| §3.5 passive: false | touchstart/move/end | SC-9 `test.fixme` — FE 미구현 표기 정합 |
| §5 UUID DOM 0건 | DOM outerHTML regex null | SC-7 `test.fixme` — FE 미구현 표기 정합 |

- **FE 번들 미구현(SC-6,7,9,10) 전부 `test.fixme` 처리** — false green 불허 원칙(`audit-slice-a` 패턴) 준수 확인.
- `page.setContent()` 패턴 0건, `|| true` 우회 0건 확인.
- **정합 이상 없음**.

## 5. 발견 사항 요약

| 구분 | 내용 | 조치 |
|---|---|---|
| 경고 | `tokens.css` `--slip-signature-*` 11개 + `@media print` 4개 미등록 | FE 구현 PR 에서 병행 등록 (현재는 FE 미구현 단계이므로 결함 아님) |
| 정상 | 인쇄 양식 픽셀 회귀 없음 | — |
| 정상 | arologis-desktop 사이드바 hidden 미적용 — 의도적 결정 | — |
| 정상 | mobile-spec / 검증 spec 10개 항목 전부 정합 | — |

**Cycle 1 결론**: design-system token 미등록 1건(FE 구현 선행 조건)을 제외하면 디자인 범위 내 결함 없음. 코드 수정 불필요.
