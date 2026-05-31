## frontend-engineer 사이클 3 리뷰 (head `232c5637`)

### 사이클 2 FE 결함 해소 표

| 결함 | 내용 | 해소 여부 |
|---|---|---|
| FE-D1 (중요) | 409 reload 후 useEffect 조건 미충족 → 폼 재동기화 실패 | **해소** — `syncFormFromData` useCallback(L95) + `handleConflictReload`(L107)에서 `query.refetch()` → `syncFormFromData(result.data)` 직접 호출. editOpen 상태와 무관하게 폼 재동기화 보장됨 |

### 사이클 2.5 신규 fix 정합 검증

**1. `syncFormFromData` useCallback 선언 (L95~L100)**
deps 배열 `[]` 로 선언. `setPartnerCode`/`setDueDate`/`setMemo`/`setLines` 는 React 안정 참조이므로 deps 누락 없음. 정합.

**2. `handleConflictReload` 흐름 (L107~L121)**
`query.refetch()` → `result.data` 존재 시 `syncFormFromData` → `setConflictMessage(null)` → `setReloadSuccessMessage` 설정 순서 정확. 이중 타이머 방지 `clearTimeout(reloadSuccessTimerRef.current)` 선행 처리 확인. 정합.

**3. 3초 dismiss cleanup — memory leak 방지 (L128~L134)**
언마운트 전용 `useEffect(() => { return () => clearTimeout(...) }, [])` 분리 선언. `reloadSuccessTimerRef.current = null` 리셋 포함. 정합.

**4. `reloadSuccessMessage` banner (L365~L373)**
`role="status"` + `data-testid="partner-order-edit-reload-success"` 정확히 선언됨. `conflictMessage` 가 null 인 상태에서만 success banner 표시 — 두 배너 동시 출력 불가 구조. 정합.

**5. UUID fallback 가드**
L124: `query.data?.orderNumber ?? '조회 중'` — pageTitle 설정.
L160: `query.data?.orderNumber ?? '조회 중'` — 상단 badge 표시.
`orderNumber ?? id` 패턴 없음 (T5 regex 검증 통과). 정합.

**6. `sales.module.css` `.successBanner` (L981~L990)**
`--color-success-50`/`--color-success-200`/`--color-success-700` 디자인 토큰 + fallback hex 병기. `--state-danger*` 토큰을 쓴 `.errorBanner` 와 대칭 구조. 정합.

**7. Playwright T5 (L76~L83)**
`reloadSuccessMessage` 문자열 포함, `partner-order-edit-reload-success` testid, `'조회 중'` 문자열, `not.toMatch(/orderNumber \?\? id/)` 4개 assertion 모두 코드와 1:1 대응 확인. 정합.

### 사이클 3 신규 발견

**FE-C1 (경고): `handleConflictReload` deps 배열에 `query` 객체 직접 참조**
L121 `}, [query, syncFormFromData])` — `query` 는 `useQuery` 반환 객체 전체로, 매 렌더마다 새 참조가 생성된다. 결과적으로 `handleConflictReload` useCallback 이 매 렌더에서 재생성된다. 기능 버그는 아니지만 성능 비효율. `query.refetch` 만 deps에 포함(`[query.refetch, syncFormFromData]`)하도록 다음 사이클 수정 권고.

**FE-C2 (정보): designer 사이클 2 P1 미반영 상태**
`clients/web/design-system/src/components/Input/Input.module.css` 에 `:read-only` 시각 스타일 미추가. Designer가 사이클 3 필수 반영으로 요청한 항목이나 이 PR 범위 내 변경 없음. 별도 슬라이스 또는 사이클 4 처리 필요.

### 긍정 사항

- 사이클 1·2 에서 도입된 native input 전면 제거 → DS `Input`/`Select`/`Modal`/`Button` 일관 적용 유지.
- `reloadSuccessTimerRef` + cleanup 이중 보호 패턴은 멀티탭 환경에서도 안전.
- T5 assertion 이 코드 구조를 정확히 반영하는 static contract 방식으로 작성됨.

### 종합

FE-D1 완전 해소, 사이클 2.5 신규 fix 7개 항목 전체 정합. FE-C1은 기능 무결하나 성능 비효율 → 다음 사이클 권고 수준. FE-C2 는 Designer P1 과 연동하여 별도 슬라이스 처리.

**APPROVE** — 사이클 4 불필요 (FE-C1·C2 는 후속 슬라이스 백로그 등록)

**frontend-engineer agent — 2026-05-17**
