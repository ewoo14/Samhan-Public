# Samhan Public (삼한 퍼블릭) — Claude Code 진입점

> 본 파일은 Claude Code 세션 시작 시 자동 로드됩니다 (project memory).
>
> **프로젝트 정식 명칭 = Samhan Public** (2026-06-06 확정 — GitHub 레포 `ewoo14/Samhan-Public`, Gradle `samhan-public`).
>
> **운영 단위 명칭 (2026-05-14 결정)**:
> - **Samhan Public** (삼한 퍼블릭) = 14 service 묶음(모노레포 전체)의 정식 명칭
> - **아로로지스** (arologis) = Samhan Public 마이크로서비스에서 분리된 독립 운영 단위 (Phase 10.5, [project_arologis_independent.md](.claude/memory/project_arologis_independent.md))
> - `SamhanLogis` = **`com.samhanair.logis.*` 패키지 네임스페이스**(기술 식별자, rename 비대상). 프로젝트/제품 명칭 아님. ※ 로컬 working dir 폴더명은 `Samhan-Public` 으로 통일 (집 PC 2026-06-06 완료, 회사 PC 변경 예정).

---

## 1. 메모리 시스템

본 repo 의 **30+ 개 Claude 메모리 규칙** 은 `.claude/memory/` 에 git tracked 되어 있어 양 PC (집/회사) 간 자동 동기화됩니다.

| 파일 | 용도 |
|---|---|
| [.claude/memory/MEMORY.md](.claude/memory/MEMORY.md) | 메모리 인덱스 (1줄 hook + 링크) |
| [.claude/memory/feedback_*.md](.claude/memory/) | 사용자 피드백 / 규칙 (PR 회고 기반) |
| [.claude/memory/project_*.md](.claude/memory/) | 프로젝트 컨텍스트 (Phase / 도메인 전략 등) |
| [.claude/memory/user_role.md](.claude/memory/user_role.md) | 사용자 역할 (개발책임자) |

### 양 PC 동기화 절차

```powershell
# 회사 PC 에서 메모리 받기 (git pull 후 1회)
git pull
.\scripts\sync-claude-memory.ps1

# 메모리 수정 후 다른 PC 로 전달
git add .claude/memory/
git commit -m "memory: <변경 내용>"
git push
```

> 사용자 홈 auto-memory 경로 (`C:\Users\<user>\.claude\projects\C--dev-Samhan-Public\memory\`) 는 working dir 경로에서 파생되는 Claude Code 빌트인이라 직접 변경 불가 — sync 스크립트가 repo → 홈 단방향 복사. (폴더명 `Samhan-Public` rename 에 따라 2026-06-06 경로 갱신 — 회사 PC 도 폴더 rename 후 동일 경로 적용됨.)

---

## 2. 작업 핸드오프

PC 이동 직전에 반드시 갱신:

- **[docs/handoff/CURRENT-WORK.md](docs/handoff/CURRENT-WORK.md)** — 현재 진행 슬라이스 + 다음 단계 1~3개 + 미해결 결정

새 PC 에서 Claude 첫 세션 시작 시 이 파일만 읽으면 즉시 컨텍스트 회복.

---

## 3. 회사 PC 첫 셋업

- **[docs/dev-environment-setup-multi-pc.md](docs/dev-environment-setup-multi-pc.md)** — 회사 PC 1회 셋업 가이드 (`.env`, Docker, 이카운트 raw 재다운로드 등)
- **Codex 사용**: `mcp__codex__codex` MCP 도구 (Plugin 폐기, 2026-05-17 사용자 정정). `claude mcp list` 로 `codex: codex mcp-server - ✓ Connected` 확인.

---

## 4. 핵심 규칙 (메모리에 상세)

본 repo 의 모든 작업은 `.claude/memory/` 의 규칙을 따릅니다. 특히:

- 🚨 **표준 워크플로우 (단일 진실원)** ([feedback_canonical_workflow.md](.claude/memory/feedback_canonical_workflow.md)) — 2026-06-23 개발책임자 확정. Opus 기획+PR개설 → Codex 개발+리뷰게시 → (Opus 5-agent[FE/BE/Design/DevOps/QA, QA=Docker 라이브QA+스샷]+fix+TM통합리뷰게시 → Codex 5-agent+QA라이브+fix+TM통합리뷰게시) **error/skip/backlog 0수렴까지 반복** → PM확인+CI → PM머지. 🚫**듀얼리뷰 병렬금지(순차)**·단축금지·**리뷰=실QA동반**. 과거 워크플로우 변동내역 통합·폐기.
- **한국어 커밋/PR** ([feedback_korean_commits.md](.claude/memory/feedback_korean_commits.md))
- **UUID 사용자 비공개** ([feedback_uuid_no_user_visibility.md](.claude/memory/feedback_uuid_no_user_visibility.md))
- **BaseEntity 7 audit + Soft Delete** ([project_build_conventions.md](.claude/memory/project_build_conventions.md))
- **아로로지스 독립 운영 단위** ([project_arologis_independent.md](.claude/memory/project_arologis_independent.md)) — 2026-05-14
- **아로로지스 명칭 규칙** ([feedback_arologis_name.md](.claude/memory/feedback_arologis_name.md)) — 한국어 표기 "아로로지스" 정식
- **Samhan Public 명칭 규칙** ([feedback_samhan_public_name.md](.claude/memory/feedback_samhan_public_name.md)) — 외부 호칭 통일
- **Codex CLI MCP 서버 사용** ([feedback_codex_plugin_setup.md](.claude/memory/feedback_codex_plugin_setup.md)) — 2026-05-17 사용자 정정. **`mcp__codex__codex` 도구** 사용 (Plugin 폐기). review = `sandbox: "read-only"`, fix = `sandbox: "workspace-write"` 또는 `danger-full-access`. ⚠️ **듀얼리뷰는 순차**(Opus 라운드 완료·게시 후 Codex 라운드) — 동시 실행 금지. → [feedback_canonical_workflow.md](.claude/memory/feedback_canonical_workflow.md)
