# estimate-app 담당자 검색 FE 배선

> 2026-06-19 백로그 해소. BE 인프라 완비(directory.js `fetchManagers` → user-service `/internal/users/employees`, code.js `getManagersForInput` RPC export + `preloadDirectoryCache_` MGR_V1 캐시). FE UI 만 미배선(정적 `<th>담당자` + 출고전표 `${d.manager}` 렌더만, 입력/검색 폼 부재). 거래처 검색 패턴 1:1 재사용.

## 변경 (Codex — `clients/web/estimate-app/views/index.ejs`)
1. **담당자 입력 필드**: 주문정보 폼(거래처 custSearch ~1618-1725 인근)에 `id="managerSearch"` input + 자동완성 드롭다운 div(거래처 `custSuggestions` 패턴). 위치=요청사항 전.
2. **`initManagerSearch()`**: `initCustomerSearch()`(~15823-15953) 미러. `google.script.run.getManagersForInput()`(RPC, 서버 라우트 불요) → `MANAGERS` 글로벌 배열 → input 이벤트 필터 → 드롭다운 렌더 → 선택 시 value+dataset.code 저장. 부트스트랩 `initCustomerSearch()` 호출부(~19331)에 `initManagerSearch()` 추가.
3. **제출 수집**: `sendOrderFromUi()`(~14700) 필드 수집에 `managerSearch` value(+code) 추가 → 주문/전표 payload 의 manager 필드에 포함. 스냅샷(`saveQuoteSnapshot`) work-state 에 자연 포함되면 유지(별도 스키마 변경 불요).
4. 담당자 = **선택 입력**(필수 아님, 거래처처럼 optional). 출고전표 `${d.manager}` 가 입력값 반영되는지 확인.

## parity / 안전
- 순수 추가(견적 금액·계산 무관). 거래처 검색 동작 불변. RPC/엔드포인트 신규 없음(기존 export 재사용).
- managerSearch 미입력 시 기존과 동일(빈 manager).

## 검증
- estimate-app 기동(CATALOG_SOURCE 무관, user-service 도달 필요) → 담당자 검색 입력 시 드롭다운(실 행정직원) + 선택 → 값 반영 + 출고전표 미리보기 담당자 표시. **Docker/로컬 실QA 스크린샷**.
- 거래처 검색 회귀 없음 확인.

## 리뷰
조기 PR → Codex → Opus 리뷰(패턴 정합·제출 수집·회귀) + 필요시 Codex 교차 → 실QA → 머지. 소규모 FE.
