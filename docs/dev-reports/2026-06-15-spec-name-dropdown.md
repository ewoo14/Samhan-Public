# 사양명 입력 드롭박스 — 개발 리포트 (PR #487)

> 2026-06-15 세션. 사양(스펙) 후속 큐 #1 (마지막). 다모델 워크플로우(Opus 계획/PR → Codex 개발 → Opus 리뷰 → Codex 교차 → PM 머지).
> 관련: 핸드오프 `docs/handoff/2026-06-15-spec-followup-queue.md`, 메모리 [[estimate-spec-data-sources]].

## 1. 배경·결정
품목 등록/수정 폼(`ProductFormPage`) 사양명(specKey) 입력이 자유 텍스트라 일관성 부족. 개발책임자 의도: "사양명 자유입력 → 드롭다운(기존 **시드** spec_key에서 선택)".

**설계 결정(PM 정찰 기반):**
- **드롭박스 소스 = `spec-key-templates`(V4 시드 53키, 큐레이션)** — 개발책임자 "시드 spec_key" 의도 부합 + 목적전용 테이블 + **BE 무변경**. (핸드오프 원안 "ProductSpec distinct 741"은 오라벨 키 전파 위험으로 비채택.)
- **자유입력 보존 = native `<input list>`+`<datalist>`** — design-system `AsyncAutocomplete`는 후보-선택형(자유입력 미보장)이라 미채택. DS `Input`이 `InputHTMLAttributes`+`...rest` 라 `list` forward.

## 2. 구현 (FE 전용 — BE 무변경)
- `productCatalogApi.ts`: `SpecKeyTemplateResponse` + `listSpecKeyTemplates(category?)`. **bare array 응답**(`res.data` — 형제 ApiResponse 엔드포인트와 엔벨로프 다름).
- `ProductFormPage.tsx`: `useQuery(['spec-key-templates'])` 후보 로드(실패 graceful `[]`). `specKeyOptions` = distinct + **min `displayOrder` 정렬**((displayOrder, ko-locale)). 사양명 `<Input list=…>` + 공유 `<datalist>`(여러 사양 행 공유). `onChange` 무변경 → **자유입력 보존**(specKey=string).
- `productFormModel.test.ts`: 커스텀(템플릿 미수록) 사양명 보존 단언.
- `mock.ts`: `MOCK_SPEC_KEY_TEMPLATES` V4 시드 대표 키 확대(GET 핸들러 기존).

## 3. 다모델 리뷰 (수렴 0 P1/P2)
- **라운드 A (Opus FE+UX)**: FE 0(엔벨로프·dedup·자유입력·graceful 정합). **UX P2** = datalist 33옵션 무정렬(`findAll()` no-ORDER BY + FE no-sort, datalist optgroup 미지원이라 정렬이 유일 탐색 레버) → Opus fix(FE displayOrder 정렬, BE 무변경).
- **라운드 B (Codex 교차)**: 0 P1/P2. 정렬 fix 결정성·엔벨로프·자유입력·mock·a11y 전건 OK. (P3: `displayOrder: number` 타입 표현 — 런타임 `??` 안전.)

## 4. QA (Docker 실서버, 실데이터, 가짜 없음)
실서버 라이브(:5173 electron-vite renderer + :8080 게이트웨이, dev_master). `playwright/spec-name-dropdown-real-qa`.
- datalist **33 distinct 실 옵션**(라이브 spec-key-templates, displayOrder 정렬: 규격·등급·배관경·냉매가스…) + list 연결 단언.
- **자유입력 보존**: 커스텀 "커스텀특수사양-현장협의" 그대로 + 드롭다운 affordance(▼). (`docs/qa/spec-name-dropdown/`)
- typecheck 0(tsconfig.node+web) · vitest 7(자유입력 보존 포함) · CI green.

## 5. 비목표 / 후속(P3)
- 카테고리 필터(폼에 estimateCategory 필드 부재 → 전체 노출, 별도 셀렉터 선행 필요).
- design-system combobox로 Select↔datalist 외형 통일(장기 에픽).
- `displayOrder` 타입 `number|null` 정합(코스메틱).
