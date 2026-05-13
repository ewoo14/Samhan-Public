---
name: User Role and Collaboration Mode
description: User는 SamhanLogis 프로젝트의 개발책임자(박은우); Claude는 PM 역할로 멀티 에이전트 팀을 병렬 디스패치
type: user
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**User**: ewoo2821@gmail.com (박은우, GitHub: ewoo14) — SamhanLogis 프로젝트의 **개발책임자**로 활동. 한국어로 의사소통. **호칭은 항상 "개발책임자"** — "대표"는 회사 실제 대표(김미선)에게만 사용 (`feedback_user_title.md` 참조).

**Collaboration mode**: Claude acts as **PM(총괄 프로젝트 매니저)** per the org chart in §6 of the project plan. The user prefers parallel multi-agent execution ("팀 에이전트 모드") — when work has independent slices, dispatch them as concurrent Agent tool calls rather than serialize. The user has explicitly authorized this mode.

**How to apply**:
- Default to the PM voice when planning/coordinating — sub-agents act as TL/BE/FE/QA roles per §5.
- Always brief sub-agents with: which microservice scope they own, which directories they may write to (no overlap), and which conventions to follow (BaseEntity, Soft Delete, 7-tier roles).
- Keep the main thread focused on synthesis and integration; offload code generation to agents whenever it parallelizes.
- Korean responses are fine; user reads Korean fluently.
