---
name: project-estimate-spec-data-sources
description: estimate-app 사양맵은 마지막 시트 소스, 구성품 개별 사양 가용성(상업멀티 전부/싱글 실내외 부재)
metadata:
  type: project
---

estimate-app(종합견적서) 사양 데이터 아키텍처 (2026-06-15 #3 정찰 확정):

- **카탈로그는 #30 으로 DB 전환 완료**(`CATALOG_SOURCE=db` → product-service `/products/internal/estimate-catalog/*`), 그러나 **사양맵(SPEC_DETAIL_MAP, `lib/code.js` `getSpecDetailMap_`)은 유일하게 시트(Google Sheets) 소스로 잔존** — code.js:1704 주석 "사양맵...시트 read 유지(후속 PR)". 사양맵 시트→DB 치환은 미수행 후속 과제.
- **ProductSpec(1:N) 적재 규칙**(`ProductSheetSyncService`): **사양 보유 탭(홈멀티/싱글세트/상업멀티 = 부모 카탈로그)만** spec_key=헤더로 적재(blocklist). **구성품 탭(싱글 구성품/상업멀티 구성)은 specText(짧은 분류 라벨)만**.
- **구성품 개별 사양 가용성**(probe `clients/web/estimate-app/scripts/spec-coverage-probe.mjs`): **상업멀티 구성품 266/266 전체 사양 보유**(구성품이 카탈로그 모델). **싱글세트는 INDOOR 0/191·OUTDOOR 0/115·PANEL 4/4·REMOTE 3/11** — 싱글세트 전용 실내기/실외기 카세트는 부모 카탈로그 미등재라 시트·DB 모두 개별 사양 부재. 단 **세트 spec 엔트리에 inSize/outSize/inWeight/outWeight 등 물리치수는 실내기/실외기 분리 보유**(성능은 세트 통합값, 시스템 단위라 분리불가).
- ✅ **estimate-catalog `/components` 에 구성품 ProductSpec 노출 완료**(#486 `6a3de57f`): `ComponentRow.specs` additive. estimate-app `renderComponentSpecs_` 가 세트 모달에 "구성품별 사양" 섹션 렌더(소스 우선순위 ①DB specs→②SPEC_DETAIL_MAP[model][scope]→③싱글 실내/외 세트 spec 물리치수). 데스크톱 주문/전표 구성품 사양은 여전히 미구현(후속, BE 확장 재사용).

**#486 실측 발견 (재사용 주의):**
- **🪤 상업멀티 카탈로그 `unit` 은 전부 `EA`**(SET 단위 없음). 상업 "세트 실외기" 판별에 `unit==='SET'` 쓰면 항상 false → 미동작(이게 P1 미렌더 유발). 상업 세트 판별 = `catL==='실외기'` + `explodeCommSets_` 구성품 보유로. 구성품 없는 실외기는 `explodeCommSets_` 무-parts fallback(자체 row, `isSetFallback`)이라 별도 skip 필요(over-trigger 가드).
- **🪤 구성품 `kind` = BE enum 영문**(INDOOR/OUTDOOR/PANEL/REMOTE/MATERIAL/ACCESSORY/FOOT). 사용자 노출 시 한글 매핑 필수(`KIND_KO`). legacy 시트 모드만 한글.
- **🪤 데이터 품질**: ① 싱글 판넬/리모컨 일부 DB `spec_key` 오라벨(예 `냉방성능(정격)` 키에 타공사이즈 `Ø1020` 값 — #445/#485 자동채움). ② 상업 combo 모듈(16HP/20HP)은 kind=ACCESSORY(스키마 DEFAULT + `ProductSheetSyncService.matchKind` 폴백 — 소스 시트 `구분` 에 "실외" 미명시). **둘 다 데이터 정리 슬라이스 대상**(코드 충실 표시 중). 정리 시 `matchKind` GHP/HP→OUTDOOR 규칙 또는 시트 보정.

**활용**: 세트 구성품 사양/사양맵 관련 작업 시 — 상업멀티는 DB에서 전체 사양 가능(137/137·각 12~13 spec), 싱글세트 실내기/실외기는 물리치수+라벨까지만(성능 합성 금지 [[feedback_no_fake_data_ever]]). 진짜 per-component 성능은 제조사 카탈로그 신규 수집 필요. #1 사양명 드롭박스 = 본 `/components` BE 패턴(spec-key distinct) 재사용. [[project_replaces_ecount_gas_was_exporter]] [[project_sheets_to_db_full_migration]] 정합.
