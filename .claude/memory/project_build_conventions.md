---
name: Build & Code Conventions
description: Build tool, language version, mandatory entity audit fields, and Soft Delete policy for SamhanLogis
type: project
---

**Build**: Gradle multi-project (Groovy DSL). Root `settings.gradle` includes each microservice and `shared:common`. Java 17 (Spring Boot 3.x baseline).

**BaseEntity (mandatory for ALL entities, all services)** — 7 audit fields, defined in `shared/common`:
- createdAt, createdBy (NOT NULL, immutable)
- modifiedAt, modifiedBy
- deletedAt, deletedBy
- isDeleted (BOOLEAN, NOT NULL, default false)

JPA Auditing wires timestamps automatically. createdBy/modifiedBy/deletedBy are pulled from JWT subject by AuditorAware bean.

**Soft Delete only** — physical DELETE is forbidden across the entire codebase. Use `isDeleted=true` + `deletedAt` + `deletedBy`. Apply `@Where(clause = "is_deleted = false")` and global Hibernate filter so reads ignore tombstones automatically. Why: legal audit + recovery + 감사로그 무결성. How to apply: any new entity must extend BaseEntity; any DELETE statement in a repo is a review-blocker.

**7-tier roles (Role enum, in shared/common)**:
MASTER, DEVELOPER, MANAGER, SALES, ACCOUNTANT, WAREHOUSE, INVENTORY. MASTER is the only role that can change permissions. CEO (김미선) = MASTER; 전무(장영구) = MANAGER.

**Approval flow**: 일반 사원 → 팀장 → 대표실(전무/대표) → MASTER 최종 결재. 팀장 직접 기안은 팀장 단계 생략.

**Korean account codes**: 일반기업회계기준 표준 (자산 100, 부채 200, 자본 300, 수익 400, 비용 500/800/900) — seed at system init, see project_plan.md §3.6.

**Numbering for slips**: display `YYYY/MM/DD - {seq}`, internal UUID always present and never shown to users.
