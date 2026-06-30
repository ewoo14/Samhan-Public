# 협업 코-에디팅 S3-4 — 그룹웨어 결재(approval) 메모 coedit

## 목적
S3-0 공용 토대 위에 그룹웨어 결재(groupware-service `ApprovalLine`) 상세 '협업 메모' 실시간 동시편집. slip/주문/견적/회계 패턴 1:1. 1차=메모 단일필드.

## 구현 범위 (최소 델타 — S3-3 동형, 리스크 최저)
- **BE**(groupware-service): `GroupwareApprovalCollabController` coedit 3엔드포인트(`GET /coedit`·`POST /coedit/update`·`POST /coedit/awareness`) + `CollabCoeditService` 주입 + **`@RequirePermission(groupware.approvals VIEW/UPDATE)` 단독 가드(옵션 A — 협업메모=collab 사이드채널, 결재 상태머신/locked 미적용)**. approvalId=직접 UUID(PK), `ensureApprovalExists`. DTO 3종. `ApprovalCollabIT` coedit 5케이스(RED→GREEN). Flyway 0(collab=in-memory relay).
- **FE**(desktop): `GroupwareApprovalCollaborationPanel` `CollaborativeTextField`('협업 메모', basePath=`/admin/groupware/approvals/{enc(id)}`, readOnly=`!canWrite`) + 보조설명("제목"·"내용"·"결재의견" 저장필드 명시). `mock.ts` approval coedit 3핸들러. `coedit.test`.
- **CI**: ci.yml phase9-10 무필터 전체실행 → ApprovalCollabIT 자동커버 + `ApprovalCollabIT skipped=0 hard gate`.

## 듀얼리뷰 (Opus ↔ Codex 0수렴)
- **Opus 5-agent**: FE 0·BE 0·DevOps 0 BLOCKING / **Design HIGH 1**(보조설명 결재 저장필드 명시 — 결재의견과 coedit 메모 혼동 차단) → Opus fix. (Design HIGH-2 수정버튼 위계·MED 패널구조 = pre-existing S4c 결재 패널 → defer)
- **Codex 라운드**: **0수렴**(새 결함 0; 결재 상태머신·권한 aspect 포함 재검).

## 계약 / 검증
- 게이트웨이 `/api/v1/admin/groupware/**` → `/admin/groupware/approvals/{id}/collab/coedit` 정합. `/groupware/approvals/new`=별도 CreatePage(T04 회피). `ApiResponse` `$.data.updates`. UUID 비노출.
- **BE: CI ApprovalCollabIT green**(Testcontainers, skipped=0 hard gate). **FE vitest 2/2**. **데스크톱 패널 실 스샷 2컷**. CI 전 잡 green. 라이브 standalone relay BLOCKED(env; BE end-to-end = CI Testcontainers IT + 동일 `CollabCoeditService` 의 S3-2 라이브 12케이스 기실증). 증적 `docs/qa/coedit-s3-4-approval/`.

## 후속
- **S3-5 배차 → #16 협업 에픽 종결**(slip·주문·견적·회계·결재·배차 6문서 coedit 완결).
- 비블로킹(별도 트랙): 결재 패널 폴리시(수정버튼 위계·코멘트 에러 병합·헤더 approvalNo·locked 메시지) · 공용 `CollaborativeTextField` cosmetic · `ApprovalCollabIT` in-memory @BeforeEach reset.
