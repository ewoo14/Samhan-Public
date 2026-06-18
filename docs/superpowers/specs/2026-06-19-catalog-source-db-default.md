# CATALOG_SOURCE=db 기본 전환 (3번 완결 — estimate-app 시트 의존 0)

> 2026-06-19 개발책임자 결정. 기초품목↔견적품목/카탈로그 DB 에픽 마무리. 방식='코드 기본 db + 프로덕션 명시 sheet override'.

## 0. 목표 / 배경
estimate-app 카탈로그 소스 기본값을 sheet→**db** 전환(의도된 default). DB 모드 = product-service 벌크 endpoint(`db-catalog.js` 완비, G1#502·F3#505·슬3-1 변동DC parity 313/313 검증). 🚨 **프로덕션(Render quote.samhan-air.com)은 백엔드 미배포**(endpoint sync:false placeholder·product-service URL 없음·시트 직접연동, Phase 11 AWS 예정) → 전역 db 전환 시 프로덕션 깨짐. **프로덕션은 명시 sheet override 로 보호**, Phase 11 백엔드 배포 시 cutover.

## 1. 변경 (Codex)
- **`clients/web/estimate-app/lib/code.js`** (~1848): `process.env.CATALOG_SOURCE || 'sheet'` → **`|| 'db'`**. 주석(~1843) 갱신(기본 db, 시트는 명시 opt-out).
- **`clients/web/estimate-app/.env.example`** (~60): `CATALOG_SOURCE=sheet` → **`db`** + 주석(기본 db; 백엔드 미도달 환경은 sheet 명시).
- **`infrastructure/render/render.yaml`** samhan-estimate-app envVars: **`CATALOG_SOURCE: sheet` 명시 추가**(주석: Phase 11 백엔드 배포 전 프로덕션 시트 유지 — 배포 후 db 로 전환/제거). cafe24 등 타 배포도 백엔드 미도달이면 동일 sheet 명시 필요 점검.
- **`infrastructure/render/deploy-checklist.md`** (+ cafe24 README 해당 시): Phase 11 cutover 항목 — 백엔드(product-service 등) 배포·estimate-app env(PRODUCT_SERVICE_URL/DC_CONFIG/SAMHAN_INTERNAL_TOKEN) 등록 후 CATALOG_SOURCE sheet override 제거 → 프로덕션 DB.
- 테스트: code.js 기본값 db 단언(가능 시 jest). 기존 DB-mode 테스트 회귀 0.

## 2. parity / 안전
- DB 모드 카탈로그 출력 = sheet 모드 동일(슬3-1 313/313·G1·F3 검증). 기본 db 전환은 **로컬/dev/db-도달 환경**에서 DB 소비, 프로덕션은 명시 sheet(무변경·안전).
- 견적 금액 무변경(소스만, parity 검증됨).

## 3. 검증
- 로컬 Docker 실QA: CATALOG_SOURCE 미설정(코드 기본 db) estimate-app 기동 → DB 카탈로그 로드·견적 정상(공청판넬/세트 전개 등 실 화면). env 미설정 시 db 기본 확인.
- render.yaml CATALOG_SOURCE=sheet override 명시 확인(프로덕션 시트 유지).
- jest/회귀 green.

## 4. 리뷰
조기 PR → Codex flip → Opus 리뷰(flip 정확성·프로덕션 override 안전·parity) + Codex 교차 → Docker 실QA → 머지. (소규모 config·parity-safe → 포커스 리뷰.)
