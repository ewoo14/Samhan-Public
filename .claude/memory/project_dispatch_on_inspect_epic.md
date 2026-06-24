---
name: project_dispatch_on_inspect_epic
description: 검수완료→배차발송 에픽 — 출고전표 검수인 결재 완료 시 아로로지스/타배송사 발송. 견적 결재 제외(개발책임자). 슬1 머지(#590), 슬2~4 잔여.
metadata:
  type: project
---

# 검수 완료 → 배차 발송 (아로로지스 / 타배송사) 에픽

2026-06-24 brainstorming(superpowers). A2 결재 enforcement 잔여 검토 중 개발책임자가 재정의: **견적은 결재 불필요(스코프 제외)**, **배차는 enforcement 아님 = "출고전표 검수인 결재(OUTBOUND_INSPECT) 완료 → 배차 발송(아로로지스 또는 타배송사)" 워크플로우 연동**. spec=`docs/superpowers/specs/2026-06-24-dispatch-on-inspect-external-carrier-design.md`.

## 확정 결정 (개발책임자 2026-06-24)
- D1 견적 제외, 배차만. D2 검수 완료 → "발송 대기" 목록 → 운영자가 채널 선택 발송(자동 아님). D3 채널=아로로지스(자체)/타배송사(외부기사). D4 타배송사 발송=문자(SMS)+인쇄(배차의뢰서) 둘 다. D5 외부기사/배송사 **마스터** 등록. D6 묶음=아로로지스 기존 차량그룹 / 타배송사 기사별. D7 슬1 UX=**기존 "배차현황"(dispatch.board) 통합**(별도 페이지 아님).
- 비목표: 외부배송사 시스템 REST 연동 / 타배송사 배송완료 회신 추적 / 자동 채널결정 / 자동 발송 / arologis 배차 흐름 재설계.

## 슬라이스
- **슬1 ✅ 머지(PR #590, main bc52cbda)**: 배차현황 미배차 목록에 **검수 완료 게이트** + 검수자/검수일시 노출. predicate=`slipType=OUTBOUND AND status=COMPLETED AND inspectorUserId/inspectorSignedAt NOT NULL AND dispatchStatus=UNDISPATCHED`(SlipRepository.findDispatchReadyOutboundSlips). DispatchTaskBoardQueryService N+1 dedup(distinct inspectorUserId→Map)+graceful. SlipBoardResponse inspectorName/inspectorSignedAt(UUID 미노출). FE UnDispatchedSlipList 검수자/검수일시/배송지/수령자(KST 직접포맷·null '-'). arologis/Flyway/page-code/enum 무변경.
- **슬2 잔여**: 외부기사/배송사 마스터(external_carrier CRUD + 관리 메뉴 + page-code 권한/시드 + FE). Flyway V## 신규.
- **슬3 잔여**: 타배송사 문자(SMS) 발송(external_dispatch/external_dispatch_slip + notification-service 재사용 + dispatchStatus 전이 + 발송대기 채널 분기 UI).
- **슬4 잔여**: 타배송사 인쇄 배차의뢰서(A4 PrintLayout).

## 슬1 교훈
- 검수 상태머신: complete()=PROCESSING→INSPECTING, inspect()=INSPECTING→COMPLETED(inspectorUserId/SignedAt 기록). "검수 완료"=COMPLETED+inspector 필드.
- 기존 "배차현황"(DispatchBoardPage)에 미배차 목록(GET /admin/dispatch-board/undispatched-slips)+차량그룹+arologis 발송(DispatchTaskCompletionService) 이미 구현 → 슬1=게이트+노출만 추가.
- 타배송사 채널 전무(arologis 단일). 배차안내 SMS 인프라(notification-service DispatchBatchSendService/Aligo) 재사용 가능(슬3).
- 검수자명 resolve=UserInternalClient.resolveFullName 단건만(batch 없음)→페이지 내 distinct dedup. graceful=catch(Exception)(SlipService 패턴).
