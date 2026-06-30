# 협업 코-에디팅 S3-5 — 배차(dispatch) 메모 coedit (#16 마지막)

## 목적
S3-0 공용 토대 위에 배차(slip-service `DispatchTask`) 상세 모달 '협업 메모' 실시간 동시편집. estimate/journal/approval 패턴. → **#16 라이브 coedit 에픽 종결**(6문서 완결).

## 구현 범위 (최소 델타 — 형제와 BE 클래스명·FE 구조 다름)
- **BE**(slip-service): `DispatchCollabCommentController` coedit 3엔드포인트 + `CollabCoeditService` 주입 + **`@RequirePermission(dispatch.board VIEW/UPDATE)` 단독 가드**(DispatchPermissionGuard 부재 · guardCollabModifiable[commit-edit 전용] 미적용 — coedit relay=in-memory). taskId=직접 UUID(`existsByIdAndIsDeletedFalse` 소프트삭제). DTO 3종. `DispatchCollabIT` coedit 5케이스. Flyway 0.
- **FE**(desktop): `DispatchTaskDetailModal` **독립 `<section aria-label="협업 메모">`**(Opus fix — "수정 이력" 섹션 밖 분리, commit-edit '비고'/수정버튼 혼동 차단) `CollaborativeTextField`(basePath=`/admin/dispatch-tasks/{enc(id)}`, readOnly=`!canAccess('dispatch.board','update')`) + 보조설명. `mock.ts` dispatch coedit 3핸들러. `coedit.test`.
- **CI (⚠️ CRITICAL)**: ci.yml + nightly slip-it-core 에 `slip.it.dispatch.*` 필터 등재 — **기존 11개 dispatch IT 가 CI 미실행이던 구조적 false-green 동반 해소** + `DispatchCollabIT skipped=0 hard gate`.

## 듀얼리뷰 (Opus ↔ Codex 0수렴)
- **Opus 5-agent**: FE 0·BE 0·DevOps 0 BLOCKING / **Design HIGH 2**(coedit 독립섹션 이동[+FE MED 동일·LOW 수정버튼 혼동 동반해소]·보조설명 따옴표)+MED(토큰/margin) → Opus fix.
- **Codex 라운드**: **0수렴**(새 결함 0; 배차 상태머신·coedit 무관성 포함 재검).

## 검증 (★ 풀 라이브 — S3 최고 커버리지)
- **BE: DispatchCollabIT 16/16**(Testcontainers PG16, skipped=0 hard gate) · dispatch_collab CHECK 제약 실 Postgres 검증.
- **라이브 relay round-trip 실 HTTP**(게이트웨이:8080·MASTER·실 task, S3-5 JAR 재빌드): GET 빈→update×2→awareness→GET 2건(awareness 미포함)→null/빈 body 400 전부 PASS.
- **FE vitest 2/2** · CI slip-it-core SUCCESS(11 dispatch IT 신규활성 전수통과) · Desktop Playwright SUCCESS. 데스크톱 보드 스샷 BLOCKED(로컬 Vite env, 정직; CI Playwright 대체). 증적 `docs/qa/coedit-s3-5-dispatch/`.

## #16 라이브 코-에디팅 에픽 종결
**6문서 coedit 완결**: slip(S3-0)·주문(S3-1 #681)·견적(S3-2 #682)·회계(S3-3 #683)·결재(S3-4 #684)·배차(S3-5 #685). 메모리 `project_global_collab_epic.md` #16 절 박제. **다음 = #17 단가변동(종합견적서+주문서).**
- 비블로킹 후속(별도 트랙): 공용 `CollaborativeTextField` cosmetic·mock awareness body 검증(cross-slice)·결재 패널 폴리시.
