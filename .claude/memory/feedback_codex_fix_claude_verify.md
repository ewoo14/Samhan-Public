---
name: codex-fix-claude-verify
description: 옵션 A — 2026-05-21 폐기. 옵션 C ([[cycle-n2-mandatory]]) 로 대체. 본 메모리는 history 참조용.
metadata:
  type: feedback
---

# Codex fix → Claude verify (옵션 A — 폐기 2026-05-21)

> **2026-05-21 폐기**: MIG-14 사례에서 옵션 A 의 약점 발견 (1f fix 후 Codex 가 1f 변경 review 안 함, CI 만 의존). 사용자 결정으로 **옵션 C ([[cycle-n2-mandatory]]) 로 대체**.
>
> 본 메모리는 옵션 C 가 어떤 약점을 보완했는지 history 참조용. 다음 슬라이스부터 옵션 C 적용.

> 사용자 명시 (2026-05-21 초기): "사이클1에서 코덱스 리뷰 후 fix된 이후 클로드가 다시 리뷰하는지 문의" → 옵션 A 채택 → MIG-14 사례 회고 → 옵션 C 로 전환.

**Why:** 기존 워크플로우 (Claude review → Claude fix → Codex review → Codex fix → CI → 머지) 는 Codex fix 결과를 누구도 cross-check 안 함 → Codex 가 본인 review 한 결과를 본인이 fix (자기참조). Codex fix 가 새 결함 (사이드 이펙트 / 의도 어긋남 / dead code) 을 도입해도 CI 의 컴파일/단위 회귀만 잡힘. 양쪽 cross-check 완전성 보장 안 됨.

**How to apply:**

기존 10단계 → **12단계 확장**:

```
1.  Claude 5-agent 병렬 review
2.  TM Claude 통합 PR comment (즉시, head SHA 명시)
3.  Claude fix (결함 0 시 skip 가능)
4.  commit + push (head 갱신)
5.  Codex 5-section 병렬 review (1c push 후 새 head)
6.  TM Codex 통합 PR comment (즉시, head SHA 명시)
7.  Codex fix (workspace-write)
8.  commit + push (head 갱신)
9.  ✨ **Claude verify (BE + QA agent spot-check, fix diff 만)** — 5-agent 전체 재실행 X
10. ✨ **MAJOR/P0 발견 시 1f Claude fix + commit + push** (작은 결함은 후속 PR 백로그 회고만)
11. 사이클 종료 조건: 잔존 결함 0 + CI watch PASS
12. 종료 시 PM 마지막 종합 + 자동 머지 / 미충족 시 사이클 N+1 (최대 N=3)
```

**핵심 규칙**:

- **단계 9 Claude verify 범위**: Codex 1e fix 의 `git diff origin/main..HEAD` 결과만 BE + QA agent 에 전달 → fix 영향 파일 spot-check. 5-agent 전체 재실행 (FE/Designer/DevOps) 금지 (시간 낭비).
- **단계 10 fix 임계치**: **MAJOR / P0 만** 1f fix 진입. P1 이하는 PR comment 회고만 명시 + 후속 슬라이스 백로그 (잔존 결함 0 원칙 완화).
- **사이클 1 안에서 1f-Claude-fix → 1g-Codex-verify 무한 반복 금지** — 1f 후 CI green 시 즉시 머지 (사이클 2 진입 의무 X)
- **사이클 N=2 진입 조건**: 1f Claude fix 후 Codex 1g verify 가 다시 P0/MAJOR 발견 시만 사이클 2 (사실상 사이클 N+1 의무 완화)

**예외 (단계 9 skip 가능)**:
- Codex 1e fix 가 문서/주석/typo 만 (`*.md`, javadoc, `// 주석`) — 코드 변경 X
- Codex 1e fix 가 trailing whitespace / formatter 정정만

**관련**: [[dual-5agent-review]] 사이클 N=1 안 완료 의무 + [[user-merge-authority]] PM 자동 머지 + [[qa-docker-real-test]] QA Docker 의무
