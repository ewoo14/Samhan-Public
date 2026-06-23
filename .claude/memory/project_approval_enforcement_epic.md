# A2 전 전표 명시 결재 enforcement 에픽

2026-06-21~22 야간 자율. 결재라인 설정(approval_line_config)의 결재자(그룹∪개인)를 **전표 처리 액션의 권한 게이트로 강제**(B-게이트: 액션·자동채움 무변경 + 권한 게이트만).

## 완료 (4 전표, 머지)
- **A2-1~A2-1c** (#552~#555): 결재라인 설정 메뉴(인사 그룹 중앙통제) + approval_line_config + action_key 앵커 + approval_line_approver(다중 결재자 그룹+개인 캡슐).
- **A2-2** (#556·#557): **출고전표** accept(OUTBOUND_DISPATCH)/inspect(OUTBOUND_INSPECT) 게이트 + DI 가드 테스트.
- **A2-3** (#558): **입고전표** accept(INBOUND_RECEIVE)/inspect(INBOUND_INSPECT). slip 게이트 slipType 일반화.
- **A2-4** (#559): **주문(PARTNER_ORDER)** convert-to-slip(PARTNER_ORDER_CONVERT). partner-order-service 신규 ApprovalLineAuthorizeClient.
- **A2-5** (PR #589, 2026-06-24): **회계전표** 게시(ACCOUNTING_JOURNAL/JOURNAL_POST) — `JournalService.post` 게이트 + accounting ApprovalLineAuthorizeClient + auth **V68 per-row 멱등 시드**(approver 미시드=opt-in). 라이브 3-phase 200/403/200 실증. ※ "회계전표 B-게이트 POOR(정책 필요)" 평가는 **개발책임자 결정으로 B-게이트 확장 채택**(옵션1)하여 해소 — 작성자=게시자 분리는 admin UI 결재자 지정으로 통제. FE 403 한국어 메시지 전파(Codex 단독 적발).

## 재사용 패턴 (확립)
auth `POST /auth/internal/approval-line/authorize`{documentType,actionKey,userId}→{configured,allowed}(X-Internal-Token, **generic**) ← 각 서비스 `ApprovalLineAuthorizeClient`(loadBalanced RestClient·**운영 생성자 @Autowired 필수**·parse fail-closed·MockRestServiceServer 계약·**DI 가드 테스트**) → **액션 직전 게이트**(opt-in: configured=false 통과·system bypass·configured&&!allowed→403). `V## 시드`(documentType 역할·action_key·WHERE NOT EXISTS 멱등). FE `DOC_TYPES`+mock 시드. AbstractPostgresIT `@MockBean(configured=false)` 회귀 차단.

## 잔여 후보 모델 평가 (회계전표 ✅A2-5 해소, 잔여 3종)
- **견적**: POOR/EXPLICIT(send/accept=거래처-facing 외부응답, estimate-app 별 아키텍처).
- **배차**: POOR(복잡 상태머신·arologis 외부 회신).
- **그룹웨어 결재**: 이미 자체 결재선(EXPLICIT chain) — approval-line config 중복/보완 검토.
→ 잔여 3종은 **명시 결재 chain(순차 승인) 모델** 신규 설계 필요(또는 그룹웨어처럼 기존 chain 보완). 개발책임자 "어느 것/어떤 모델" 지정 시 brainstorming 부터. 회계전표는 B-게이트 확장으로 충분(A2-5). ([[restclient-contract-test-false-green]] · [[migration-fresh-postgres-probe]])
