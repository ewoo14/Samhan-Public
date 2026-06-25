---
name: mobile-s4a-modal-fullscreen
description: 모바일 슬4a ✅완결·머지(PR #599, main 8e0eb84a) — 공용 Modal+CsvUploadDialog+자체 dialog 3건 모바일 풀스크린 + 공용 Tabs 가로스크롤. 라이브QA가 매 라운드 실결함 단독적발. 다음=슬4b 폼 1열
metadata:
  type: project
---

# 모바일 에픽② 슬4a — 공용 Modal 모바일 풀스크린 ✅완결 (2026-06-25 PR #599, main 8e0eb84a)

≤768px(--bp-md) 모바일에서 모달을 **풀스크린**으로. 데스크탑(>768px)/인쇄 무변동. FE-only·Flyway 0·CSS-only(.tsx 무변경 또는 className 전환만). 슬3(DataTable 카드화) 다음.

## 최종 커버리지 (전 모달)
- **공용 Modal**(Modal.module.css, 32화면): @media 풀스크린(backdrop padding0·dialog 100%/100dvh·min-width0[size-xl 980 override]·border-radius0·header/footer sticky·safe-area).
- **CsvUploadDialog**(별도 design-system 컴포넌트, ④): 동일 @media — 공용 Modal CSS Modules 해시 스코핑상 미전파라 자체 파일에 추가.
- **자체 inline dialog 3건**(앱-레벨, ⑤+전수): EditWarehouseModal·DepositMatchPage DepositDetailModal·InboundInspectionDialog lightbox → overlay/dialog **inline style→CSS Module 전환 + @media 풀스크린**. 확인 alertdialog(InboundInspection)는 풀스크린 대신 `max-width:min(400px,calc(100vw-32px))` 오버플로 가드(작은 확인창 풀스크린은 과함).
- **공용 Tabs**(Tabs.module.css): `.tablist overflow-x:auto`+`.tab flex-shrink:0` — 탭 바 가로스크롤(모달 내 탭 우측 잘림 해소). 넘칠 때만 스크롤=데스크탑 무변동.

## 🔑 교훈 (박제)
- **라이브 QA가 매 리뷰 라운드 실결함을 단독 적발**: ④ CsvUploadDialog 풀스크린 누락 / ⑤ 자체 dialog 2건 / 전수 InboundInspection / **탭 바 우측 잘림(개발책임자 캡처 적발)**. 정적 리뷰·build·mock gate 전부 통과한 것들. → "리뷰마다 라이브QA"의 가치 반복 입증.
- **라이브QA = 리뷰 라운드 귀속**(구현단계 독립 Task 아님): 개발책임자 지적("구현단계서 왜?"). 각 라운드 리뷰→fix→**그 fix 라이브 재캡처**가 게이트. [[qa-docker-real-test]] 정정.
- **자체 dialog 전수 grep 함정**: `role=dialog` 파일에서 `<Modal` 동시사용 파일을 제외필터로 빼면 그 파일 내 별도 inline dialog를 누락(초기 2건 과소집계). **role=dialog/alertdialog 전수**가 정답(InboundInspection은 Modal도 쓰면서 자체 lightbox 보유). 전수=3건.
- inline style은 @media 불가 → 풀스크린 필요한 자체 dialog는 overlay/dialog만 CSS Module화(데스크탑 값 보존)+@media.

## 다음 (개발책임자 지정 대기)
- **슬4b — 입력 폼 1열**(모바일 다열 폼→1열). 모달 껍데기(슬4a)·리스트(슬3) 다음 = 폼 콘텐츠.
- 후속: 상세 페이지 반응형·와이드 회계보고서(슬3 MAJOR 이월)·PWA·네이티브 패키징.

관련: [[mobile-s3-datatable-card]] · [[feedback_canonical_workflow]] · [[feedback_pm_auto_merge_authority]] · [[qa-docker-real-test]]
