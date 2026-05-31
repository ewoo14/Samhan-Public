# Designer cycle 1 리뷰 — audit Slice 4 (PR #255 FE Pretendard lint)

**판정**: APPROVE
**작성일**: 2026-05-19
**검토자**: UI/UX Designer agent

---

## 1. design-system tokens.css 영향 검토

`clients/web/design-system/src/tokens/tokens.css` 변경 내용 확인.

- `--font-family-sans` / `--font-family-base` 토큰 값 변경 없음
- `--color-insung-*` 6종 (SP-10-2 기존 추가) 포함 컬러 토큰 변경 없음
- `--font-size-*` / `--font-weight-*` / `--line-height-*` 변경 없음

**결론**: font-family token 변경 없음. design-system token 영향 0.

---

## 2. Pretendard 9 weight self-host 정합 검증

### P1-2 dist/style.css @font-face

`clients/web/design-system/dist/style.css` 에 `@font-face` 선언 존재 확인 (grep 1건 히트).  
`--font-family-sans` 및 `--font-family-base` 양 토큰 모두 `"Pretendard Variable", Pretendard` 선두 배치 확인.

### P1-3 arologis-mobile OTF

`clients/arologis-mobile/assets/fonts/` 에 아래 4종 OTF 실존 확인:
- Pretendard-Regular.otf
- Pretendard-Medium.otf
- Pretendard-SemiBold.otf
- Pretendard-Bold.otf

**관찰**: 아로로지스 모바일은 Regular/Medium/SemiBold/Bold 4종 self-host. 9 weight 전체가 아닌 운영 필요 weight 선택적 번들 (모바일 APK 용량 최적화 목적으로 간주) — 기존 패턴과 동일. 변경 없음.

`clients/mobile-staff/assets/fonts/` 동일 4종 OTF 실존 확인 — 변경 없음.

---

## 3. 인쇄 양식 / 사이드바 / 모바일 서명 UX 영향

- 인쇄 양식 토큰 (`--print-*`) 변경 없음
- 사이드바 관련 토큰 변경 없음
- 모바일 서명 (`clients/mobile-staff/src/screens/driver/SignatureScreen`) 관련 토큰 변경 없음

**결론**: 인쇄 양식 / 사이드바 / 모바일 서명 UX 영향 0.

---

## 4. 종합 판정

| 검증 항목 | 결과 |
|---|---|
| design-system tokens.css font-family token 변경 | 없음 |
| Pretendard 9 weight self-host dist/style.css @font-face | 정합 |
| arologis-mobile OTF self-host | 실존 (4종) |
| 인쇄 양식 토큰 영향 | 0 |
| 사이드바 UX 영향 | 0 |
| 모바일 서명 UX 영향 | 0 |

PR #255 audit Slice 4 FE Pretendard lint — Designer 관점 이상 없음. **APPROVE**.
