---
name: project-global-collab-epic
description: §7 전역 협업 에픽 — 수정완료(1-인) 모델 + collab-core 문서별 롤아웃 (slip 레퍼런스 머지 완료)
metadata:
  type: project
---

§7 전역 협업 = 플랫폼 전역 협업 에픽 (개발책임자 확정, 2026-06-13). 대부분 메뉴 화면(전표·견적·회계전표·주문·배차·미배차/가배차·**그룹웨어 결재** 등)에 협업 기능 = **수정완료(1-인) + 코멘트 + diff + 알림**.

**핵심 모델 (절대 혼동 금지)**: 제안/수락(2-인) 아님 = **문서 수정(1-인)**. 확정/완료 상태 문서에서 권한자 본인이 "수정"→편집→"수정완료" = **즉시 커밋**(별도 승인자 없음·잠금 우회·다필드 1버전). 완료 상태일 때 기존 "완료" 버튼이 "수정" 버튼으로 대체됨. 구글 독스 "제안" 기능은 **diff 시각화(무엇이 어떻게 바뀌었는지 한눈에)** 벤치마킹 목적으로 언급된 것이지 2-인 승인 플로우가 아님. 기존 edit-request(수정 요청) 플로우는 **완전 대체**(삭제 요청만 보존). guardCollabModifiable 은 물리종결 상태만 409(slip=SHIPPING/DELIVERED/CANCELED/REJECTED).

**알림 규칙**: 수정완료 시 **기여자(작성·수정·코멘트 참여 이력) + 다음 결재자(있으면, 없으면 skip)** 에게 발송. 현재 수정자 self-skip. 수신자 ID 가 username/사원코드면 **username→UUID resolve**(auth `/auth/internal/accounts/by-login`). 트랜잭션 내 동기 best-effort(기존 SlipEditRequestService 패턴 일관, AFTER_COMMIT 금지 — @Transactional IT 롤백 시 미발화). slip 의 다음 결재자 = 출고인(dispatcherUserId)·검수인(inspectorUserId).

**공유 모듈**: `shared:collab-core` — CollabCommentService<T>·CollabSuggestionService<T>·DocumentCollaborationPort(loadSnapshot/applyChangeSet/restoreSnapshot/canPropose/canDecide/resolveNotificationRecipients)·CollabDocumentType ENUM(DISPATCH_TASK·SLIP_OUTBOUND·SLIP_INBOUND·ACCOUNTING_VOUCHER·PARTNER_ORDER·ESTIMATE). 문서별 서비스가 Port 구현 + Config 배선.

**롤아웃**: 문서별 슬라이스(개발책임자 "이번 슬라이스에 모두 종료" → PM 제안 per-document 롤아웃 수용). **슬라이스 0 = 입출고전표 레퍼런스 = 머지 완료**(PR #474, `30b0ce93a`, 2026-06-13). 다음 = 회계전표/주문/견적/배차/그룹웨어 결재 등에 collab-core 패턴 복제. presence(동시 접속자+사용자별 랜덤 색상)=후속 슬라이스로 분리.

**슬라이스 1 = 회계전표(ACCOUNTING_VOUCHER) = 머지 완료** (PR #475, `4e644241c`, 2026-06-13): 엔티티=`Journal`(분개), DRAFT→POSTED→REVERSED. 확정/완료=POSTED, COLLAB_LOCKED={REVERSED}. **수정완료 편집=적요(description)+라인메모(line.{lineNo}.memo, 1-based)만**(차대변 금액/계정 불변=역분개, 원장키 400). **알림=기여자만**(결재자 없음). page-code `accounting.journals` 재사용. **collab-core 근본fix**: `CollabCoreAutoConfiguration @AutoConfigureAfter(RealtimeAutoConfiguration)`(auto-config broker 의존 서비스 publisher 누락 방지 — 에픽 전체 이득). 다모델 Round A(Opus)/B(Codex, 실서버 DTO normalize 적발)/C(Opus) 0 차단 수렴. **+ 회계 문서번호 슬래시 표준화**(개발책임자 "슬래 모두" — 생성기 4종·forward V37·JournalSeeder seq-UUID 분리, [[feedback_slip_order_number_format]]). FE=기존 `JournalDetailPage.tsx`. Flyway V36(collab) + V37(번호).

**슬라이스 2 = 주문(PARTNER_ORDER) = 머지 완료** (PR #476, `7ea401da9`, 2026-06-13): collab-core 복제. 편집=memo+dueDate+라인remark만(핵심 불변·400), COLLAB_LOCKED={CANCELED,CONVERTED,CONFIRMING}, 알림=기여자만, page-code sales.partner-order 재사용, 주문번호 이미 슬래시. **lineKey=활성라인 1-based index → PartnerOrder.lines `@OrderBy("createdAt ASC, id ASC")` 결정성 필수**. **실서버 P1**: collab 컨트롤러 `@PathVariable UUID` → FE 하이픈 path-id(toOrderPathId) 400 → `@PathVariable String`+`PartnerOrderIdResolver.findByIdentifier`(EditController 패턴) 양형 해석. mock(단순 id) 미검출·실서버/dual-model 적발. Round A(Opus)/B(Codex)/C(Opus) 0 차단.

**다음 슬라이스 후보**: 견적(ESTIMATE)·배차(DISPATCH_TASK)·그룹웨어 결재. 문서 순서는 개발책임자 확인(슬라이스마다 sequencing 질문).

**🔭 후속 큐(비차단, §7 범위 밖)**: partner-order **버전이력(PartnerOrderRevisionController)·realtime(PartnerOrderRealtimeController) 컨트롤러가 `@PathVariable UUID`** 라 FE orderNumber 하이픈 path-id 에서 400(선존 버그, "버전 이력 불러오지 못함"). 수정 시 **controller 에 PartnerOrderRepository 주입하면 web-slice `@WebMvcTest`(JPA repo 미포함) 컨텍스트 로드 깨짐** → resolve-in-service 패턴(서비스가 String id 해석) 또는 @MockBean PartnerOrderRepository 동반. 별개 슬라이스.

**collab 슬라이스 공통 검증 패턴(확립)**: ①BE collab IT(실 Postgres) — 수정완료+이력·잠금 409·핵심키/빈changeSet 400·알림 기여자(+Revision actor) resolve·username→UUID·다중라인 불변·CHECK ②desktop typecheck+collab playwright+전체 playwright ③**실서버 Docker QA**(게이트웨이 :8080·dev_master·VITE_MOCK_MODE off·하이픈 path-id, 수정완료·diff·코멘트 9컷, 불변 SHA URL 인라인) ④각 라운드 PR 게시. **실서버 QA 가 mock-미검출 계약버그(DTO normalize·orderId 경로) 적발 — 머지 전 필수**.

**워크플로우**: 각 슬라이스 = [[temp-multimodel-workflow]] (기획 → Codex 개발 → 순차 5-agent 라운드[각 PR게시+실서버 스크린샷] → 다음 리뷰어 0에러까지 사이클 → PM 최종점검+머지). 용어는 [[comment-not-collab-comment]](사용자 노출=「코멘트」, 영문 식별자 collab-core 유지).
