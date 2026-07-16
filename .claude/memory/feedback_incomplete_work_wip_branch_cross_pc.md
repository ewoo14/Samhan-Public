---
name: feedback_incomplete_work_wip_branch_cross_pc
description: 타 PC 재개(집↔회사) 세션 정리 시 미완·미검증 산출물은 stash 가 아니라 원격 WIP 브랜치로 격리 — stash 는 원격에 안 넘어가 타 PC 가 못 봄. feature 브랜치는 청결 유지.
metadata:
  type: feedback
---

세션을 **다른 PC 에서 재개**(집↔회사)하는 조건으로 정리할 때, 워킹트리에 남은 **미완·미검증 산출물**(중단된 fix 에이전트 결과 등)의 처리:

**🚫 `git stash` 금지 — 원격에 안 넘어간다.** stash 는 로컬 전용이라 타 PC 는 `git pull` 로 절대 못 받는다. 같은 PC 재개면 stash 참조가 유효하지만([[feedback_codex_detached_write_settle]] 의 codex-exec stash 선례), **타 PC 재개면 무의미**하다.

**✅ 원격 WIP 브랜치로 격리한다:**
```bash
git checkout -b wip/<issue>-<what>-incomplete   # 현재 워킹트리(미완) 흡수
git add -A && git commit -F <msg>               # "WIP·미검증·신뢰불가·참조용" 명시
git push -u origin wip/<issue>-<what>-incomplete
git checkout <feature-branch>                    # 워킹트리 clean·HEAD 원위치
```
결과: **feature 브랜치 원격 HEAD 는 마지막 검증본 그대로**(CI·리뷰 대상 청결 유지·미검증 커밋 미오염), 미완은 별도 브랜치로 타 PC 가 **참조 가능**.

**Why:** 미완 산출물을 feature 브랜치에 커밋하면 CI red·리뷰 혼선·"완료 착각" 위험. 버리면 에이전트 작업 손실. WIP 브랜치가 [[feedback_canonical_workflow]] 청결과 작업 보존을 동시 충족. (2026-07-16 #809 R8 fix 2차: "R8-QA-12 유닛 테스트 추가" 직전 중단·gradle 미검증 12파일을 `wip/809-r8-fix2-incomplete` 로 격리, feature 는 `e8f558cd4` 청결 유지.)

**How to apply — 핸드오프에 반드시 박제:** ① WIP 브랜치명·SHA ② **"완료로 착각 금지·미검증"** 경고 ③ **fresh 재디스패치**하되 WIP diff 는 참조만([[feedback_codex_detached_write_settle]] 정신) ④ feature HEAD SHA + 원격 sync(0 0) 확인. 다음 세션은 [[feedback_agent_origin_main_sync]] 대로 **`git pull` + sync 카운트 먼저 읽어** stale 핸드오프 물림 방지. 🚨 이번 세션 초반 실제로 로컬 16커밋 뒤처진 걸 못 읽고 stale "R4 착수" 핸드오프를 믿어 R4/R8 혼동 발생 → git pull 후 R8 이 정답으로 판명.
