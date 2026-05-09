# Sales Form UX Polish — Designer 산출물

본 디렉토리는 Samhan Public Sales Form Polish 슬라이스의 **Designer (5-team 신규)** 산출물입니다.
FE 팀은 본 산출물의 spec 을 인용해 구현하며, BE/QA/DevOps 팀도 wireframe / interaction flow 를 참고합니다.

> 사용자 (개발책임자) 강조: **"디자인 좀 잘 부탁해"**
> → 본 슬라이스는 단순 기능 구현이 아닌, 화면 품질 자체가 1급 요구사항.

---

## 1. 디자인 철학

### 1.1 영감 (Inspiration)

| 출처            | 차용 요소                                                      |
| --------------- | -------------------------------------------------------------- |
| **Notion**      | 부드러운 회색 grayscale, 충분한 line-height, 미니멀 border    |
| **Linear**      | dense 정보 밀도 + crisp typography + 빠른 micro-interaction   |
| **Figma**       | 카드 elevation, 모달 overlay, 컴포넌트 variant 체계           |
| **이카운트ERP** | 1·2·3 라인 넘버링, 행 클릭 선택, dense table, 단축키 friendly |
| **데스크톱 ERP**| 8시간 작업 가능한 눈 피로 적은 색감, 키보드 우선 네비게이션  |

### 1.2 원칙 (Principles)

1. **모던 미니멀 + dense 정보 밀도**
   - 사무실 8시간 사용 가정 — 화면당 가능한 많은 정보를 깔끔하게.
   - 행 높이 40px (일반 ERP 24~30px 보다는 여유, Notion 스타일 보다는 dense).

2. **일관된 spacing scale (4-base)**
   - `4 / 8 / 12 / 16 / 24 / 32 / 48` 외 다른 값 사용 금지.
   - margin / padding / gap 모두 동일 scale.

3. **typography scale (Pretendard)**
   - 12 / 14 / 16 / 20 / 24 / 28pt — 한국어 글자 가독성 최우선.
   - 숫자 컬럼 `font-variant-numeric: tabular-nums` 의무 (자릿수 정렬).

4. **color palette (Notion/Linear 영감 — 모던 미니멀)**
   - Background: `#FAFBFC` (앱) / `#FFFFFF` (카드) / `#F4F6F8` (subtle/hover)
   - Border: `#E1E5EA` / `#C9D1D9` (hover) / `#3B82F6` (focus·selected)
   - Text: `#1A1F2E` (primary) / `#5C6773` (secondary) / `#8A95A4` (tertiary)
   - Brand: `#1E40AF` (primary action) / `#3B82F6` (hover) / `#DBEAFE` (subtle)
   - State: `#10B981` (success) / `#EF4444` (danger) / `#F59E0B` (warning)
   - Selection: `#EFF6FF` (selected row bg) — 매우 옅은 파랑

5. **Border radius**
   - 4px (input/button), 8px (card/modal), 12px (large card).
   - 12px 초과 사용 금지 (모던하지만 과한 둥글림 X).

6. **Shadow (subtle)**
   - card: `0 1px 3px rgba(0,0,0,0.04)` — 거의 안 보이는 elevation.
   - modal: `0 8px 24px rgba(0,0,0,0.12)` — 명확한 layer separation.

7. **micro-interaction**
   - 모든 hover / focus / selected 트랜지션 `120ms ease-out`.
   - 너무 느리면 sluggish, 너무 빠르면 cheap.

### 1.3 안티 패턴 (사용 금지)

- 둥근 12px 초과 button (cute 하지만 ERP 부적합)
- 진한 grey (`#666` 등) text — 한국어 가독성 저하
- 주황/녹색 primary action — brand blue 만 사용
- emoji 아이콘 (Heroicons / Lucide line icon 만 사용)
- gradient 배경 (모던 미니멀과 어긋남)
- shadow strong (sharp shadow는 Material 스타일, 본 프로젝트는 soft)

---

## 2. 산출물 (본 디렉토리)

| 파일 | 내용 |
| --- | --- |
| `README.md` | 본 문서 — 철학 + 영감 + 원칙 |
| `wireframes.md` | SlipFormPage / 재고조회 모달 / DispatchView 세로 A4 ASCII art |
| `tokens.md` | 디자인 토큰 갱신 (현재 vs 신규 비교 표) |
| `components.md` | 신규/변경 컴포넌트 spec — LineRow / StockBalanceModal / DragHandle / DispatchView |
| `ux-flow.md` | interaction flow + 키보드 단축키 + drag/drop / lookup / 재고조회 |
| `print-spec.md` | 인쇄 양식 spec — DispatchView 세로 A4 / InvoiceView 가로 A4 비교 |

---

## 3. FE 가 인용해야 할 핵심 spec (top 5)

1. **`components.md` — `<LineRow>` props/states 표** — 라인 행 핵심 컴포넌트
2. **`components.md` — `<StockBalanceModal>`** — 재고 조회 다건 batch
3. **`tokens.md` — 신규 색상/spacing/radius 표** — 적용 우선순위 (SlipFormPage 만 우선)
4. **`ux-flow.md` — drag-and-drop 시나리오** — `@dnd-kit/sortable` 통합 패턴
5. **`print-spec.md` — A4 portrait CSS** — `@page { size: A4 portrait; margin: 12mm; }`

---

## 4. 의존성 추천 (FE 가 설치)

| 패키지 | 버전 | 용도 |
| --- | --- | --- |
| `@dnd-kit/core` | `^6.1.0` | drag and drop 기반 |
| `@dnd-kit/sortable` | `^8.0.0` | 라인 순서 변경 |
| `@dnd-kit/utilities` | `^3.2.2` | CSS transform helper |

> 본 슬라이스 Designer 결정 사항 (Q3=A `@dnd-kit/sortable` 채택).
> 대안 `react-beautiful-dnd` 는 maintenance mode + React 18 호환성 미보장 → 채택 X.

---

## 5. 적용 범위 (Q5=B 본 화면만)

- **본 슬라이스 적용**: SlipFormPage / StockBalanceModal / DispatchView (세로 A4)
- **차후 점진 적용**: 16개 design-system 컴포넌트 + 55 stories
- **이유**: tokens 변경 시 전 화면 visual regression 위험. 본 슬라이스는 SlipFormPage 한정 검증 후 후속 슬라이스에서 점진 확산.

---

## 6. 검증 (QA 협조)

QA agent 가 본 슬라이스에서 검증해야 할 디자인 항목:

- [ ] 라인 컬럼 우측 정렬 (수량/단가/합계) — tabular-nums 적용 확인
- [ ] 행 hover / selected 색상 일치
- [ ] drag 중 opacity 0.6 적용
- [ ] 재고 조회 모달 max-width 720px / overlay 60% black
- [ ] DispatchView 인쇄 시 A4 portrait, 여백 12mm
- [ ] 작업지시서 서명 박스 60×40mm
- [ ] 모든 텍스트 Pretendard fallback (Noto Sans KR)
