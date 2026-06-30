# 협업 코-에디팅 S3-3 — 회계전표(Journal) 메모 coedit

## 목적
S3-0 공용 토대 위에 회계전표(accounting-service `Journal`) 상세 '협업 메모' 실시간 동시편집. slip/주문/견적 패턴 1:1. 1차=메모 단일필드.

## 구현 범위 (최소 델타 — 세 도메인 중 가장 단순, 특이 가드 없음)
- **BE**(accounting-service): `JournalCollabController` coedit 3엔드포인트(`GET /coedit`·`POST /coedit/update`·`POST /coedit/awareness`) + `CollabCoeditService` 주입 + **`@RequirePermission(accounting.journals VIEW/UPDATE)` 단독 가드**(견적 EstimatePermissionGuard 이중가드/X-Is-System-Master 미사용). journalId=직접 UUID, `ensureJournalExists`. DTO 3종. `JournalCollabIT` coedit 케이스(relay·awareness 미저장·VIEW/UPDATE 403·null/빈 400). Flyway 0(journal_collab 테이블 V36 기존, coedit=in-memory relay).
- **FE**(desktop): `JournalCollaborationPanel` `CollaborativeTextField`('협업 메모', basePath=`/accounting/journals/{enc(id)}`, readOnly=`!canWriteComments`) + 보조설명("적요"·"라인 메모" 저장필드 명시). `mock.ts` journal coedit 3핸들러. `coedit.test`.
- **CI**: ci.yml accounting+partner 무필터 전체실행 → JournalCollabIT 자동커버 + `JournalCollabIT skipped=0 hard gate`.

## 듀얼리뷰 (Opus ↔ Codex 0수렴)
- **Opus 5-agent**: FE 0·BE 0·DevOps 0 BLOCKING / **Design HIGH 1**(보조설명 저장필드 명시 — 편집폼 "라인 메모"(저장) vs "협업 메모"(coedit) 충돌 차단)+MED(readOnly→canWriteComments) → Opus fix.
- **Codex 라운드**: **0수렴**(새 결함 0). `line.{n}.memo` overflow 500 후보=이미 400 처리 확인.

## 계약 / 검증
- 게이트웨이 `/api/v1/accounting/**` → `/accounting/journals/{id}/collab/coedit` 정합. `/accounting/journals/new`=정적 `JournalFormPage`(T04 안전). `ApiResponse` `$.data.updates`. UUID 비노출.
- **BE: CI JournalCollabIT green**(Testcontainers, skipped=0 hard gate). **FE vitest 2/2**. CI 전 잡 green. 라이브 standalone relay + 데스크톱 스샷 = 로컬 env BLOCKED(정직; BE end-to-end = CI Testcontainers IT + 동일 `CollabCoeditService` 의 S3-2 라이브 12케이스로 기 실증).

## 후속
- **S3-4 그룹웨어 결재 → S3-5 배차** → #16 종결 → #17 단가변동.
- 비블로킹(별도 트랙): 공용 `CollaborativeTextField` cosmetic + `--color-brand-700` fallback(3패널 공통) + journalNo seed margin(CI-safe).
