# 사양(스펙) 후속 큐 — 2026-06-15 (개발책임자 순서: 2 → 3 → 1)

> PR #485(품목 등록/관리 고도화) 머지 완료 후속. 동적 사양 등록은 #485에 포함(ProductSpec 1:N, 사양 섹션 종류 무관 항상 표시). 아래 3건은 컨텍스트 소진으로 다음 세션 연속.

## #2 (먼저) — 종합견적서 사양 표시 실 캡처
**estimate-app 사양 메커니즘(정찰 완료, `clients/web/estimate-app`):**
- **모델명/품목명 셀 더블클릭 → `openSpecModalByItem(baseItem, ...)`** (사양 모달) — `views/index.ejs:6533,6537,6544`. specDetailMap=`lib/code.js:1757`.
- **세트(unit=SET) 규격 칸 = `-`**(index.ejs:6566). 일반품목은 자기 규격 표시.
- **세트상세(DETAIL) 모드**: `explodeSetParts`로 구성품 폭발 → 각 구성품이 자기 `.spec` 표시(index.ejs:2743/4787~).
- **세트 더블클릭 시 구성품 사양 전부 집계 표시 여부 = 미확정** → `openSpecModalByItem` 본문 확인 필요(세트일 때 partsForSet 사양 펼치는지).

**캡처 차단(게이트)**: estimate-app `:3000`/`:3100` → 302 `/login`(#31 접속 게이트 `checkUserAuth` = user-service **email 인증**, X-Internal-Token 재사용). 캡처하려면 POST `/login`(dev_master 이메일/세션) 통과 후 chromium 로드. **시드 확인됨: spec_key 741개, sample set "360 CST UV"/AC060CS6PBH1SY, single 276.** mock 금지 실데이터.
- 캡처 방법: estimate-app 로그인 세션 쿠키 획득 → Playwright/gstack 로 견적 화면 → 세트/일반 품목 더블클릭 사양 모달 캡처 → `docs/qa/.../estimate-spec-*.png`.

## #3 (다음) — 세트 조회 = 구성품 사양 집계 표시 (갭 구현)
세트상세 모드는 구성품 사양을 펼치나, **세트 1줄/더블클릭에서 구성품 사양 전부 보이진 않음**(규격 `-`). 요구: 세트 조회 시 구성품 사양도 모두 표시. estimate-app `openSpecModalByItem`(세트일 때 partsForSet 사양 집계) + 데스크톱/주문서 사양 표시 경로 확인. 데이터 기반 OK(세트·구성품 각 ProductSpec + BundleComponent 링크 + PR #445 세트 구성품 규격 자동채움).

## #1 (마지막) — 사양명 드롭박스 (시드 사양 기반)
사양 등록 시 `사양명` 자유입력 → **`select` 드롭다운**(기존 시드 spec_key 741개에서 선택). 구현: BE distinct spec-key endpoint(`GET /products/spec-keys` 등, ProductSpec distinct spec_key) + FE `ProductFormPage` 사양 행 `사양명`을 Select(+직접입력 허용 옵션). productFormModel/mock/vitest 동반.

## 워크플로우 (동일)
Opus 계획 → Codex 개발 → Opus 5-agent + Codex 교차 → CI green + Docker 실 QA(스크린샷, electron-vite dev 막힘→**렌더러 정적빌드+python http.server:5175+playwright real-qa.config** 우회 검증됨) → 머지.
