# 문서 양식 디자이너 (Document Form Designer) — 에픽 설계서

- 기준일: 2026-07-18 · 진실원: 개발책임자 브레인스토밍(2026-07-18) · 착수 시점: **#825 슬4(칩) 완료 후**
- 관련: [project_print_preview_standardization] · [project_slip_shipout_print_form] · [feedback_role_naming_full] · [feedback_pgc_c2_widening_option_a]

## 0. 배경·목표
개발책임자 지시(2026-07-18): 결재양식 편집을 지금의 "우측 필드 빌더(키/라벨/타입 나열 폼)"에서 **이카운트식 문서형 WYSIWYG**로 전환. 나아가 **문서 미리보기를 지원하는 모든 양식**(전표·거래명세서·세금계산서·견적서·결재문서·회계보고서 등)을 같은 GUI 편집기로 변경 가능하게 한다.

현재 인쇄 문서는 전부 **하드코딩 React PrintLayout 컴포넌트**(`clients/desktop/src/renderer/print/*`, `.../routes/accounting/print/*`)로 정의된다. 본 에픽은 **코드 정의 → 데이터(템플릿) 정의**로 전환하는 폼 엔진을 구축한다.

## 1. 확정 결정 (개발책임자·2026-07-18)
| # | 결정 |
|---|---|
| DFD-01 | 편집 깊이 = **자유 문서 디자인**(필드 배치 + 레이아웃 + 서식 전부 자유) |
| DFD-02 | 첫 **파일럿 = 결재 문서·양식**(슬4 연장·반쯤 데이터 기반·저위험) |
| DFD-03 | 설계 대상 = **인쇄·미리보기 문서**. 캔버스에 필드를 배치하면 그게 곧 템플릿 필드로 정의되고, 작성 입력화면은 그 필드 집합으로 자동 생성 |
| DFD-04 | **다중 명명 템플릿** 저장·관리(라이브러리) |
| DFD-05 | 캔버스 모델 = **하이브리드 밴드**(밴드로 뼈대 + 밴드 안/위 자유 배치·스냅 그리드·정렬 가이드). 반복 라인아이템 = detail 밴드 |
| DFD-06 | 편집기 = **3-pane**(좌: 요소·데이터 팔레트 / 중: 밴드 캔버스 / 우: 선택 요소 속성) |
| DFD-07 | 권한 = **기본 부여 MASTER·MANAGER**, 권한그룹 설정에서 수동 조정(부여/회수). **seed 진실원**·문서유형별 page-code(파일럿=기존 `groupware.approval-templates`) |
| DFD-08 | **법정 고정 양식**(세금계산서·재무제표 등)은 자유설계 **제외**(고정/테마만·법적 형식 고정) |

## 2. 아키텍처 (3계층)
1. **템플릿 스키마(데이터)** — 각 서비스 DB에 JSON 저장. `밴드 → 요소 → {타입·데이터바인딩·라벨·형식·위치/크기·스타일}`.
2. **렌더러** — `(템플릿 + 문서 데이터) → 밴드 문서` 렌더. **미리보기·인쇄·PDF 공용 렌더러**. 현 `PrintLayout`(desktop)/`PrintPreview`(design-system)·포맷 헬퍼(krw/krDate/toKoreanAmount) 재사용.
3. **편집기** — 3-pane WYSIWYG가 스키마를 편집·저장. 팔레트 데이터 항목 드래그 → 필드 정의 + 캔버스 배치.

## 3. 데이터 모델 (개략)
```
Template {
  id, docType(GROUPWARE_EXPENSE_REPORT…), name, version, status(DRAFT|ACTIVE), paper(A4…), bands[]
}
Band {
  kind(HEADER|BODY|DETAIL(반복)|FOOTER|APPROVAL), elements[]
}
Element {
  id, type(TEXT|FIELD|APPROVAL_GRID|TABLE|IMAGE|LINE),
  binding?(데이터 키), label?, format?(currency|date|number…),
  geometry{ x, y, w, h }, style{ font, size, align, weight, border }
}
```
- FIELD 요소의 `binding` = 템플릿 필드 키. **배치 = 필드 정의**. 작성 입력화면은 이 필드 집합으로 자동 생성.
- APPROVAL_GRID = 동적 결재란(2~5칸·전자서명). DETAIL 밴드 = 가변 라인아이템(거래명세서 등 확장 시).

## 4. 하위호환 (무회귀 안전판)
- 현 결재 문서 인쇄 레이아웃을 **기본 템플릿(seed)** 으로 1:1 변환 → 기존 문서는 그대로 렌더(**픽셀 회귀 스냅샷 가드**).
- 편집기 없이 기본 템플릿만으로도 동작 → 단계적 도입.
- ⚠️ 결재양식 필드 저장은 **soft-delete replace-set**(기존 필드 soft-delete + 신규 추가)임을 유의(라이브 QA 실측 확인 2026-07-18) — 렌더러/편집기 전환 시 이 모델 준수.

## 5. 슬라이스 분해 (각각 정식 워크플로우: 기획→CODEX SOL 검수→CODEX LUNA 구현→OPUS/CODEX 적대검증×2→라이브QA→머지)
| 슬라이스 | 내용 |
|---|---|
| **DS-1 Foundation** ◀ 첫 착수 | 템플릿 스키마 확정 + 렌더러 + 기본 템플릿 seed(현 레이아웃 1:1) + 결재 문서 미리보기/인쇄를 렌더러 경유로 전환(**출력 무변경**·픽셀 회귀 가드). *편집기 없음* |
| **DS-2 템플릿 관리** | 문서유형별 다중 명명 템플릿 CRUD·버전(DRAFT/ACTIVE)·활성 템플릿 렌더. 권한 seed(MASTER·MANAGER)+조정 |
| **DS-3 편집기 MVP** | 3-pane 팔레트/밴드 캔버스(드래그·스냅)/속성·데이터 바인딩·저장·라이브 미리보기. 현 필드 빌더 대체 |
| **DS-4 고도화** | 반복 detail 밴드·이미지/로고·서식 정밀·인쇄 fidelity iteration([feedback_print_design_iteration] 3~5회) |
| **이후 확장** | 거래명세서·판매송장·매입전표·견적서 등으로 엔진 확장(문서유형별 데이터 바인딩 어댑터만 추가). 법정 양식(DFD-08) 제외 |

## 6. 리스크
- **인쇄 fidelity** — 렌더러 출력이 현 하드코딩 레이아웃과 픽셀 동일해야(스냅샷 회귀 가드 필수).
- **엔진 blast radius** — 렌더러/스키마가 다수 문서에 공유 → 회귀 표면 큼(단계적·파일럿 우선).
- **가변 라인아이템** — DETAIL 밴드 반복 바인딩(거래명세서 확장 시 난이도↑).
- **권한·무결성** — 법정 양식 고정(DFD-08)·편집 권한 seed 진실원·회계 원장류 불변.

## 7. 검증 전략
- 각 슬라이스 캐논 워크플로우 엄수(적대검증 2-model + 라이브QA 스샷).
- **기존 인쇄 문서 스냅샷 회귀**(DS-1: 렌더러 전환 전후 픽셀 동일).
- design-system 렌더러/편집기 = Playwright mock 스위트([feedback_design_system_playwright_mock_suite]).

---
연관: #825(전역 입력 UX 에픽·칩 표준화가 선행 primitive 제공) · 신규 에픽 이슈 등록 예정
