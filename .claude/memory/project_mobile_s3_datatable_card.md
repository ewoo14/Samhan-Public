---
name: mobile-s3-datatable-card
description: 모바일 슬3 ✅완결·머지(PR #598, main 1d195b74) — 공용 DataTable CSS 카드화로 56 리스트화면 모바일 자동전환. 라이브QA가 ⑤ MAJOR 정적주장 교정. 다음=슬4 폼/모달/상세
metadata:
  type: project
---

# 모바일 에픽② 슬3 — DataTable 모바일 카드화 ✅완결 (2026-06-25 PR #598, main 1d195b74)

공용 `clients/web/design-system/src/components/DataTable/DataTable.tsx` 의 `<td>` 에 `data-label={col.header}` 1개 + `DataTable.module.css` `@media(max-width:768px)` 카드 블록 → **56개 DataTable 리스트 화면이 ≤768px 에서 행=카드(라벨-값) 자동 전환**. 데스크탑(>768px)/인쇄 무변동(신규 CSS 전부 @media 한정). CSS-only·FE-only·Flyway 0. 개발책임자 "DataTable 카드화 집중(폼/모달=슬4)·스크린샷 추후 보정" 결정.

## 카드 패턴 (재사용)
- `td::before { content: attr(data-label) }` 좌측 라벨 + 값 우측. `table/thead/tbody/tr/td → display:block`, thead clip 시각숨김, `.tr`=카드(border/radius/padding/shadow), `.td`=flex space-between. `td[data-label=""]` 빈헤더 액션셀=`content:none`+`justify-content:flex-end`(우측). 긴 값(이메일/URL)=`min-width:0;overflow-wrap:anywhere;word-break:break-word`(④fix). [[responsive-drawer-offscreen-a11y]]와 함께 모바일 패턴 축적.

## 듀얼리뷰 교훈 (박제)
- **라이브QA가 정적 리뷰의 MAJOR를 교정**: ⑤ Codex가 "와이드 고정폭 래퍼(minWidth:1760)→모바일 가로 스크롤"을 MAJOR로 적발했으나, 라이브 캡처(월별손익 390px)는 scrollWidth=390 = **클립이지 스크롤 아님**(슬2 `.app-main overflow-x:hidden`이 클립) + 해당 화면 데이터없음 에러. 정적 리뷰 단독이면 과대평가. **개발책임자 "리뷰마다 라이브QA" 규칙의 가치 재입증**([[feedback_qa_docker_real_test]]) — OPUS 라운드 fix를 mock gate로만 검증한 것 지적받고 라이브 보강.
- **와이드 매트릭스(~7 회계보고서: 월별손익 1760·홈택스 1400·채권채무 1280·DC설정 1500·PhotoAudit 1120)는 공용 카드화 범위 밖** → 12개월×계정 매트릭스는 화면별 모바일 설계 필요 = **슬4**. 공용 DataTable 카드화(~50 일반 리스트)는 견고(슬립·거래처·외부기사 라이브 검증).
- UI 개발용어 노출 제거(SlipListPage "(legacy)"·발주OCR hint) 동반.

## 다음 (개발책임자 지정 대기)
- **슬4 — 화면별 모바일**: 입력 폼(~9 전용+다수) 1열·모달/다이얼로그(18) 풀스크린·상세(10) 반응형·**와이드 회계보고서(~7) + 원시 table 화면(권한 매트릭스 등)**.
- 최종=PWA(설치/오프라인)+iOS/Android 하이브리드 패키징. 전 메뉴 모바일 최적화는 슬4~슬5+패키징 잔여(슬3=리스트 한 축 완료).

관련: [[feedback_canonical_workflow]] · [[feedback_pm_auto_merge_authority]] · [[feedback_platform_branch_build_time_flag]]
