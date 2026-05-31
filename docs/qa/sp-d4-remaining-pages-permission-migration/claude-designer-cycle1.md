# SP-D4 Designer 리뷰 — Cycle 1
> 리뷰어: Claude Designer Agent
> PR: #244  head: `6d141002`
> 브랜치: `feat/sp-d3-slip-dispatch-permission-migration` (SP-D4 커밋 포함)
> 작성일: 2026-05-18

---

## 1. 리뷰 범위

| 파일 | 변경 유형 |
|------|-----------|
| `clients/desktop/src/renderer/routes/PermissionMatrixPage.tsx` | 변경 — 13 카테고리 그룹 + thead 3행 구조 추가 |
| `clients/desktop/src/renderer/api/permissionsApi.ts` | 변경 — PageCode 19 → 41 타입 확장 |
| `clients/web/design-system/src/tokens/tokens.css` | 참조 검증 (수정 없음) |

---

## 2. design-system 토큰 실존 검증

### 2.1 그룹 헤더 (`--color-brand-*`) 토큰

`PermissionMatrixPage.tsx` 카테고리 그룹 헤더 `th` 스타일에서 참조하는 토큰 3종 실존 여부를 `tokens.css` 에서 직접 확인.

| 참조 토큰 | 용도 | tokens.css 실존 | 값 (light mode) |
|-----------|------|-----------------|-----------------|
| `--color-brand-50` | 그룹 헤더 background | 확인 (line 12) | `#EFF6FB` |
| `--color-brand-200` | 그룹 헤더 border | 확인 (line 14) | `#AECFE7` |
| `--color-brand-700` | 그룹 헤더 color (텍스트) | 확인 (line 19) | `#1B4A6B` |

3종 토큰 모두 정상 실존. dark mode 오버라이드도 `html[data-theme="dark"]` 블록(line 406~413)에 동일 변수명으로 등재되어 있어 다크 모드 호환 확인.

### 2.2 그룹 헤더 색상 대비 (WCAG AA)

light mode 기준:
- 배경 `#EFF6FB` vs 텍스트 `#1B4A6B` — 명도 대비 약 8.1:1 (WCAG AAA 충족)
- border `#AECFE7` 는 시각 구분용 선 토큰 — 접근성 기준 대상 외 (장식적 요소)

이상 없음.

### 2.3 dirty 경고 배너 토큰

| 참조 토큰 | tokens.css 실존 |
|-----------|-----------------|
| `--color-warning-50` | 확인 (line 42) |
| `--color-warning-200` | 확인 (line 43) |
| `--color-warning-800` | 확인 (line 47) |

이상 없음.

### 2.4 미등록 토큰 — 결함 발견

**F-D-01** (아래 §6 상세 기술): `--color-warning-400` 토큰이 `PermissionMatrixPage.tsx` line 724 (`borderLeft: isDirty ? '3px solid var(--color-warning-400)'`)에서 참조되나 `tokens.css` 에 등록되어 있지 않음.

**F-D-02** (아래 §6 상세 기술): `--color-danger-600`, `--color-success-600` 토큰이 toast 스타일(line 562~563)에서 참조되나 `tokens.css` 에 등록되어 있지 않음.

---

## 3. thead 3행 구조 디자인 검토

### 3.1 구조 개요

```
행 1 (그룹 헤더): "역할\페이지" rowSpan=3 th | [그룹명 colSpan=N] × 13개
행 2 (페이지 라벨): [PAGE_LABEL] × 41개
행 3 (액션 서브헤더): [조회/변경 | 조회] × 41개
```

### 3.2 그룹 헤더 색상 계층

- 행 1 그룹 헤더: `--color-brand-50` 배경 + `--color-brand-700` 텍스트 (brand 계열 강조)
- 행 2 페이지 라벨: `--color-neutral-50` 배경 + 기본 텍스트 (중간 중립)
- 행 3 액션 서브헤더: `--color-neutral-50` 배경 + `--color-neutral-400` 텍스트 (보조 정보 약하게)

3단계 계층이 명확히 구분됨 — brand → neutral → neutral/muted 순서. 일관성 양호.

### 3.3 "역할 \ 페이지" 교차 셀

rowSpan=3, `position: sticky; left: 0; zIndex: 40` 적용. MASTER 행도 좌측 sticky. 스크롤 시 역할 열 고정 — 광폭 매트릭스(41컬럼) 에서 필수적인 UX 처리로 적절.

### 3.4 폰트 크기

| 영역 | 폰트 크기 | 토큰/직접값 |
|------|-----------|-------------|
| 그룹 헤더 | `12px` | 직접값 (`var(--font-size-xs)` 미사용) |
| 페이지 라벨 | `12px` (table 상속) | 직접값 |
| 액션 서브헤더 | `11px` | 직접값 |
| 체크박스 라벨 | `10px` | 직접값 |

그룹 헤더는 `var(--font-size-xs)` (12px) 와 값은 동일하나 CSS 변수를 직접 인용하지 않고 리터럴 `12px` 로 기술됨. 기능 이상은 없으나 토큰 체계 일관성 측면에서 권장 개선 사항 (MINOR).

---

## 4. UX 일관성 — 카테고리 순서 검토

### 4.1 그룹 배치 순서

| 순번 | 그룹 | 유형 | 업무 흐름 적절성 |
|------|------|------|-----------------|
| 1 | 회계 | SP-D1~D3 기존 | 재무 핵심 — 상위 배치 적절 |
| 2 | 매입 | SP-D1~D3 기존 | 구매 흐름 — 적절 |
| 3 | 매출 | SP-D1~D3 기존 | 판매 흐름 — 적절 |
| 4 | 배차 | SP-D1~D3 기존 | 물류 실행 — 적절 |
| 5 | 알림 | SP-D1~D3 기존 | 알림 보조 — 적절 |
| 6 | 관리 | SP-D1~D3 기존 | 시스템 관리 — 후순위 적절 |
| 7 | 견적 | SP-D4 신규 | 영업 선행 — 적절 |
| 8 | 거래처주문 | SP-D4 신규 | 견적 후속 → 주문 — 적절 |
| 9 | 재고 | SP-D4 신규 | 창고/재고 관리 — 적절 |
| 10 | 직원·계정 | SP-D4 신규 | 사람 관리 — 적절 |
| 11 | 거래처 | SP-D4 신규 | 파트너 마스터 — 적절 |
| 12 | 상품 | SP-D4 신규 | 상품 마스터 — 적절 |
| 13 | 아로지스 | SP-D4 신규 | 독립 운영 단위 — 후순위 적절 |

SP-D1~D3 기존 그룹 → SP-D4 신규 그룹 순서로 배치하여 이전 슬라이스와의 연속성이 유지됨. SP-D4 신규 그룹 내 순서 또한 "영업(견적→주문) → 재고 → 마스터 데이터(직원·거래처·상품) → 외부 서비스(아로지스)" 업무 흐름 기준으로 논리적으로 타당함.

알파벳 정렬보다 업무 흐름 순서가 권한 매트릭스 성격(운영자 시각)에 적합 — 이상 없음.

---

## 5. DesignSystem 컴포넌트 신규 작성 및 Storybook 영향 검증

### 5.1 PermissionMatrixPage 내 design-system import

```tsx
import { Button, Badge, Spinner } from '@samhan/design-system'
```

3종 모두 기존 컴포넌트. `clients/web/design-system/src/components/` 신규 파일 0건 확인.

### 5.2 Storybook stories 파일 변경

`clients/web/design-system/src/` 하위 `.stories.tsx` 파일 변경 없음 — PR #244 기여 파일 범위 외. Storybook 영향 0건 확인.

### 5.3 design-system `index.ts` export 변경

신규 컴포넌트 추가 없으므로 `index.ts` export 변경 없음 — 이상 없음.

---

## 6. PageGroup 그룹명 / 라벨 한국어 일관성 검토

### 6.1 `직원·계정` 중간점 표기

```tsx
{ label: '직원·계정', pages: ['admin.employees', 'admin.users'] }
```

`·` (U+00B7 MIDDLE DOT) 사용. 한국어 병렬 나열 표기 관용 부호로 적절. `data-testid="permission-matrix-group-직원·계정"` 과의 정합도 확인됨.

### 6.2 `아로지스` 표기

```tsx
{ label: '아로지스', pages: ['arologis.admin', 'arologis.region'] }
// PAGE_LABEL
'arologis.admin': '아로지스 배차',
'arologis.region': '지역·구역',
```

`feedback_arologis_name.md` 정식 표기는 **"아로로지스"** 이나 코드에는 **"아로지스"** 로 기술됨. `o`가 1개 누락된 오기.

**F-D-03** (아래 §7 상세 기술): 그룹명 `아로지스` → `아로로지스` 정정 필요.

### 6.3 PAGE_LABEL 한국어 검토

| PageCode | 현재 라벨 | 검토 결과 |
|----------|-----------|-----------|
| `estimates.list` | `견적 목록` | 이상 없음 |
| `sales.partner-order.list` | `주문 목록` | 이상 없음 |
| `sales.partner-order.draft` | `주문 작성` | 이상 없음 |
| `sales.partner-order.confirm` | `주문 확정` | 이상 없음 |
| `sales.partner-order.history` | `주문 이력` | 이상 없음 |
| `sales.partner-order.print` | `주문서 인쇄` | 이상 없음 |
| `sales.vendor-order` | `벤더 주문` | `벤더` 외래어 — 사용자 용어 확인 권장 (INFO) |
| `inventory.warehouse` | `창고 관리` | 이상 없음 |
| `inventory.stock` | `재고 현황` | 이상 없음 |
| `inventory.stock-transfer` | `재고 이동` | 이상 없음 |
| `inventory.dps` | `DPS 비교` | 이상 없음 |
| `inventory.audit` | `재고 감사` | 이상 없음 |
| `admin.employees` | `직원 관리` | 이상 없음 |
| `admin.users` | `계정 관리` | 이상 없음 |
| `partners.list` | `거래처 목록` | 이상 없음 |
| `partners.detail` | `거래처 상세` | 이상 없음 |
| `partners.block` | `거래처 차단` | 이상 없음 |
| `partners.edit-request` | `편집 결재` | 이상 없음 |
| `products.list` | `상품 목록` | 이상 없음 |
| `products.admin` | `상품 관리` | 이상 없음 |
| `arologis.admin` | `아로지스 배차` | `아로지스` → `아로로지스` 정정 필요 (F-D-03 연동) |
| `arologis.region` | `지역·구역` | 이상 없음 |

---

## 7. 발견된 결함

### F-D-01 [CRITICAL] `--color-warning-400` 토큰 미등록

**위치**: `PermissionMatrixPage.tsx` line 724

```tsx
borderLeft: isDirty
  ? '3px solid var(--color-warning-400)'
  : '1px solid var(--color-neutral-200)',
```

`tokens.css` 에 등록된 warning 계열 토큰:
- `--color-warning-50`, `--color-warning-200`, `--color-warning-300`, `--color-warning-500`, `--color-warning-700`, `--color-warning-800`

`--color-warning-400` 은 등록되어 있지 않음. 브라우저는 `var()` 폴백 없이 미등록 변수를 참조하면 해당 속성의 초기값(initial value)으로 fallback — `borderLeft` 색상이 사라지거나 `currentColor` 로 fallback 되어 dirty 셀 마커의 시각 피드백이 소실됨.

**권고**: `--color-warning-400` 토큰을 `tokens.css` 에 추가(`#F1C268` 와 `#E9A53D` 사이 중간값 `#F5CE6A` 또는 `#F0C755` 제안)하거나, 기존 등록된 `--color-warning-300`(#F1C268) 또는 `--color-warning-500`(#E9A53D) 로 대체.

### F-D-02 [CRITICAL] `--color-danger-600`, `--color-success-600` 토큰 미등록

**위치**: `PermissionMatrixPage.tsx` line 562~563

```tsx
background: toast.type === 'success'
  ? 'var(--color-success-600)'
  : 'var(--color-danger-600)',
```

`tokens.css` 에 등록된 토큰:
- success: `--color-success` (alias), `--color-success-50`, `--color-success-200`, `--color-success-500`, `--color-success-700`
- danger: `--color-danger` (alias), `--color-danger-50`, `--color-danger-200`, `--color-danger-300`, `--color-danger-500`, `--color-danger-700`, `--color-danger-800`

`--color-success-600`, `--color-danger-600` 모두 미등록. toast 배경색 소실 또는 transparent fallback 발생.

**권고**:
- 성공 toast: `--color-success-500` (#10b981) 또는 `--color-success-700` (#047857) 으로 대체
- 오류 toast: `--color-danger-500` (#D6504A) 또는 `--color-danger-700` (#991B1B) 으로 대체

### F-D-03 [MAJOR] `아로지스` 그룹명 오기 — `아로로지스` 로 정정 필요

**위치**: `PermissionMatrixPage.tsx` line 197, 255~256

```tsx
// line 197
{ label: '아로지스', pages: ['arologis.admin', 'arologis.region'] }

// line 255~256
'arologis.admin': '아로지스 배차',
```

`feedback_arologis_name.md` 정식 표기: **"아로로지스"** (`o` 2개). `아로지스`는 1개 누락 오기.

data-testid 는 `permission-matrix-group-아로지스` 로 설정되어 있어 Playwright spec 이 이 값을 기준으로 작성된 경우 함께 수정 필요.

**권고**: `'아로지스'` → `'아로로지스'` 전체 교체 (그룹 label 및 PAGE_LABEL 2건).

### F-D-04 [MINOR] 그룹 헤더 `fontSize: 12` — 토큰 변수 미사용

**위치**: `PermissionMatrixPage.tsx` line 626

```tsx
fontSize: 12,
```

design-system 의 `--font-size-xs: 12px` 토큰과 값은 동일하나 직접 리터럴을 사용. `fontSize: 'var(--font-size-xs)'` 또는 `12` 대신 tokens 인덱스의 상수로 대체하면 토큰 체계 일관성 향상.

**권고**: `fontSize: 12` → `fontSize: 'var(--font-size-xs)'` (단위는 CSS custom property 내에 포함되어 있으므로 문자열 인용). 현재 기능에는 이상 없으므로 MINOR 수준.

---

## 8. 종합 평가

| 검토 항목 | 결과 |
|-----------|------|
| `--color-brand-50/200/700` 토큰 실존 | 통과 |
| 그룹 헤더 색상 계층 명확성 | 통과 |
| WCAG AA 대비 | 통과 (8.1:1 AAA) |
| DesignSystem 신규 컴포넌트 0건 | 확인 |
| Storybook 영향 0건 | 확인 |
| 카테고리 순서 (업무 흐름) | 적절 |
| `직원·계정` 중간점 표기 | 적절 |
| `아로로지스` 정식 명칭 | **오기 발견 (F-D-03)** |
| `--color-warning-400` 토큰 | **미등록 (F-D-01)** |
| `--color-danger-600/success-600` 토큰 | **미등록 (F-D-02)** |
| 폰트 토큰 변수 인용 | **MINOR 개선 여지 (F-D-04)** |

**사이클 1 결론**: F-D-01, F-D-02(CRITICAL — 토큰 미등록으로 런타임 시각 피드백 소실), F-D-03(MAJOR — 정식 명칭 오기) 해결 없이 APPROVE 불가.

---

## 9. TM 결정 권고

**cycle 2 수정 필수**:
1. F-D-01 — `--color-warning-400` 토큰 `tokens.css` 추가 또는 기존 토큰으로 대체
2. F-D-02 — toast 배경색 `--color-success-600`/`--color-danger-600` → 기존 등록 토큰 대체
3. F-D-03 — 그룹명/PAGE_LABEL `아로지스` → `아로로지스` 전체 교체

F-D-04 (MINOR — fontSize 리터럴) 는 cycle 2 에 함께 처리 권장하나 필수 아님.
