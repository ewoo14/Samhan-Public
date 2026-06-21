# A2-1 결재라인 설정 메뉴 + 선언적 approval_line_config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **구현 주체**: 코드는 **Codex 구현**([[feedback_codex_implements_claude_reviews]]), Claude 빌드/테스트/커밋 대행. plan/spec/메모리는 Claude.
>
> spec: [docs/superpowers/specs/2026-06-21-approval-line-config-a2-design.md](../specs/2026-06-21-approval-line-config-a2-design.md)

**Goal:** 인사 그룹에 "결재라인 설정" 메뉴를 신설해, 전표 종류별 결재 역할(작성자/출고인/검수인)에 권한 그룹과 필수여부를 **선언적으로 중앙 정의·저장**한다(enforcement 는 A2-2).

**Architecture:** auth-service 가 `approval_line_config` 단일 테이블(전표종류별 역할 카탈로그)을 호스팅하고 admin CRUD 를 노출. 데스크톱은 권한그룹 관리 형제 페이지로 역할별 권한 그룹 지정 UI 를 제공. config 는 `group_page_permissions` 를 건드리지 않는 **선언적 저장**(split-truth 회피) — A2-2 가 소비.

**Tech Stack:** Java 17 · Spring Boot 3 / JPA · Flyway · Testcontainers(PostgreSQL) · React/Vite/TanStack Query · @samhan/design-system · Vitest.

## Global Constraints

- **BaseEntity 7 audit + Soft Delete 의무** · **한국어 Javadoc** · **도메인 메서드 chain**(직접 setter 금지) ([[project_build_conventions]]).
- **Flyway additive**: 신규 V*.sql 만(auth 다음 = **V61**). `ADD ... IF NOT EXISTS`/`CREATE TABLE IF NOT EXISTS`(V42 컨벤션). 적용된 마이그 불변([[feedback_applied_migration_immutable]]). push 전 **fresh Postgres probe**([[feedback_migration_fresh_postgres_probe]]).
- **page-code = 일반 page-code**(`admin.approval-line-config`, MANAGEMENT_PAGE_CODES **미편입** — `updateDelegations` 하드코딩 회피). seed=MASTER+MANAGER.
- **config 는 group_page_permissions 미조작**(선언적 approval_line_config 만). enforcement=A2-2.
- **작성자 역할(CREATOR)=전표 `requesterId`** 해석(A2-2/소비측). config 는 step_type=CREATOR 마킹만.
- **StepType = `shared:approval-core` enum 재사용**(auth-service 에 `implementation project(':shared:approval-core')` 추가).
- **FE canAccess page-code = 실제 BE @RequirePermission 정확 일치**([[feedback_fe_canaccess_pagecode_be_match]]). **MASTER bypass QA 함정** 회피 — 게이트 검증은 비-MASTER 위임 MANAGER 계정([[feedback_enforcement_real_http_test]]).
- 빌드=`./gradlew`. 커밋 한국어. FE 타입검증=`npm run typecheck`([[feedback_desktop_typecheck_command]]).

---

## File Structure

**신규 (auth-service)**
- `domain/ApprovalLineConfig.java` — @Entity(approval_line_config), 전표종류별 역할 1행. 도메인 메서드 assignGroup/clearGroup/setRequired.
- `repository/ApprovalLineConfigRepository.java`
- `service/ApprovalLineConfigService.java` — listRoles(docType)/updateRole(id, groupId, required) + group 이름 resolve.
- `web/ApprovalLineConfigController.java` — `/auth/admin/approval-line-configs` GET/PUT, @RequirePermission(admin.approval-line-config).
- `web/dto/ApprovalLineRoleView.java`, `web/dto/UpdateApprovalLineRoleRequest.java`
- `src/main/resources/db/migration/V61__approval_line_config.sql` — 테이블 + 출고 seed + page-code grant seed.
- Test: `service/ApprovalLineConfigServiceTest.java`(unit) · `it/ApprovalLineConfigControllerIT.java`(Testcontainers 실 HTTP).

**수정 (auth-service)**
- `domain/PageCode.java` — `ADMIN_APPROVAL_LINE_CONFIG` 추가(line 144 이후).
- `build.gradle` — `implementation project(':shared:approval-core')`.

**수정 (shared)**
- `shared/collab-core/.../CollabDocumentType.java` — (이미 SLIP_OUTBOUND 등 보유, 변경 없음 — 확인만).

**신규/수정 (desktop)**
- `src/renderer/api/approvalLineConfigApi.ts` — fetch/update + DOC_TYPES.
- `src/renderer/routes/ApprovalLineConfigPage.tsx` — 설정 페이지.
- `src/renderer/routes/index.tsx` — 라우트 + PermissionGuard.
- `src/renderer/components/AppLayout.tsx` — 인사 카테고리 SidebarLink(:1093-1150).
- `src/renderer/routes/PermissionMatrixPage.tsx`(또는 `permissionPageCatalog`) — page-code 카탈로그 등록.
- Test: `src/renderer/routes/__tests__/ApprovalLineConfigPage.test.tsx`(vitest).

---

## Task 1: BE 토대 — PageCode + 테이블 + 엔티티 (Flyway V61)

전표종류별 결재 역할 카탈로그 테이블과 엔티티, 신규 page-code 를 추가한다. 끝에서 **fresh Postgres probe + auth 컴파일** 통과.

**Files:**
- Modify: `services/auth-service/.../domain/PageCode.java`, `services/auth-service/build.gradle`
- Create: `domain/ApprovalLineConfig.java`, `repository/ApprovalLineConfigRepository.java`, `db/migration/V61__approval_line_config.sql`

**Interfaces:**
- Produces: `PageCode.ADMIN_APPROVAL_LINE_CONFIG` (code `admin.approval-line-config`). `ApprovalLineConfig`(getDocumentType/getSequence/getLabel/getStepType/getApproverGroupId/isRequired + `assignGroup(UUID)`/`clearGroup()`/`changeRequired(boolean)`). `ApprovalLineConfigRepository.findByDocumentTypeOrderBySequenceAsc(String)`.

- [ ] **Step 1: PageCode 상수 추가**

`PageCode.java` line 144(`GROUPWARE_APPROVAL_TEMPLATES(...)`) 다음에:

```java
    /** 결재라인 설정 — 전표종류별 결재 역할에 권한 그룹/필수여부 중앙 정의(인사 그룹). */
    ADMIN_APPROVAL_LINE_CONFIG("admin.approval-line-config", "결재라인 설정"),
```

> MANAGEMENT_PAGE_CODES(:639-642)에는 **추가하지 않는다**(일반 page-code, 위임 가능).

- [ ] **Step 2: build.gradle 에 approval-core 의존 추가**

`services/auth-service/build.gradle` 의 `implementation project(':shared:security')` 등 옆에:

```gradle
    implementation project(':shared:approval-core')
```

- [ ] **Step 3: ApprovalLineConfig 엔티티 작성**

```java
package com.samhanair.logis.auth.domain;

import com.samhanair.logis.approval.StepType;
import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 전표 종류별 결재 역할 1건(선언적 카탈로그). 결재라인 설정 메뉴가 역할에 권한 그룹/필수여부를 지정한다.
 *
 * <p>enforcement(게이트/명시 결재)는 본 config 를 소비하는 슬라이스(A2-2 등)가 수행한다. 본 엔티티는
 * {@code group_page_permissions} 를 건드리지 않는 선언적 정의만 보관한다(권한그룹 관리와 진실원 분리).
 */
@Entity
@Getter
@Table(name = "approval_line_config")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ApprovalLineConfig extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 전표 종류 — CollabDocumentType name (SLIP_OUTBOUND 등). */
    @Column(name = "document_type", nullable = false, updatable = false, length = 40)
    private String documentType;

    /** 역할 순서(0-base). */
    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    /** 역할 표시 명칭(작성자/출고인/검수인). */
    @Column(name = "label", nullable = false, length = 50)
    private String label;

    /** 결재자 식별 방식(CREATOR=전표 작성자 자동 / GROUP=권한 그룹 / USER). */
    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, updatable = false, length = 20)
    private StepType stepType;

    /** GROUP 역할의 지정 권한 그룹(nullable — 미지정 또는 CREATOR). */
    @Column(name = "approver_group_id")
    private UUID approverGroupId;

    /** 결재 필수여부(E11). */
    @Column(name = "required", nullable = false)
    private boolean required;

    /** GROUP 역할에 권한 그룹 지정. CREATOR 역할은 거부. */
    public void assignGroup(UUID groupId) {
        if (this.stepType != StepType.GROUP) {
            throw new IllegalStateException("권한 그룹은 GROUP 역할에만 지정할 수 있습니다: " + this.label);
        }
        this.approverGroupId = groupId;
    }

    /** 권한 그룹 해제. */
    public void clearGroup() {
        this.approverGroupId = null;
    }

    /** 필수여부 변경. */
    public void changeRequired(boolean required) {
        this.required = required;
    }
}
```

- [ ] **Step 4: Repository 작성**

```java
package com.samhanair.logis.auth.repository;

import com.samhanair.logis.auth.domain.ApprovalLineConfig;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalLineConfigRepository extends JpaRepository<ApprovalLineConfig, UUID> {
    /** 전표 종류별 역할을 sequence 오름차순으로 조회(활성 행만 — @SQLRestriction). */
    List<ApprovalLineConfig> findByDocumentTypeOrderBySequenceAsc(String documentType);
}
```

- [ ] **Step 5: Flyway V61 작성** (테이블 + 출고 seed + page-code grant seed)

```sql
-- V61__approval_line_config.sql
-- A2-1 결재라인 설정 — 전표종류별 결재 역할 카탈로그(선언적) + 출고 seed + page-code grant.

CREATE TABLE IF NOT EXISTS approval_line_config (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    document_type       VARCHAR(40)  NOT NULL,
    sequence            INT          NOT NULL,
    label               VARCHAR(50)  NOT NULL,
    step_type           VARCHAR(20)  NOT NULL,
    approver_group_id   UUID,
    required            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(50)  NOT NULL DEFAULT 'system',
    modified_at         TIMESTAMP,
    modified_by         VARCHAR(50),
    deleted_at          TIMESTAMP,
    deleted_by          VARCHAR(50),
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT approval_line_config_pk PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_line_config_doctype_seq_active
    ON approval_line_config (document_type, sequence)
    WHERE is_deleted = FALSE;

-- 출고전표 기본 결재 역할 seed (권한 그룹 미지정 — 메뉴에서 MASTER 가 지정)
INSERT INTO approval_line_config (id, document_type, sequence, label, step_type, required, created_by)
SELECT gen_random_uuid(), v.document_type, v.sequence, v.label, v.step_type, TRUE, 'v61-seed'
FROM (VALUES
    ('SLIP_OUTBOUND', 0, '작성자', 'CREATOR'),
    ('SLIP_OUTBOUND', 1, '출고인', 'GROUP'),
    ('SLIP_OUTBOUND', 2, '검수인', 'GROUP')
) AS v(document_type, sequence, label, step_type)
WHERE NOT EXISTS (
    SELECT 1 FROM approval_line_config a
    WHERE a.document_type = v.document_type AND a.sequence = v.sequence AND a.is_deleted = FALSE
);

-- admin.approval-line-config page-code 를 MASTER/MANAGER 기본 그룹에 부여(VIEW+UPDATE)
INSERT INTO group_page_permissions
    (id, group_id, page_code, can_view, can_create, can_update, can_delete, can_restore, can_download, can_print,
     created_at, created_by, is_deleted)
SELECT gen_random_uuid(), roles.group_id, 'admin.approval-line-config',
       TRUE, FALSE, TRUE, FALSE, FALSE, FALSE, FALSE, NOW(), 'v61-seed', FALSE
FROM (VALUES
    ('00000000-0000-0000-0000-000000000100'::uuid),  -- MASTER
    ('00000000-0000-0000-0000-000000000101'::uuid)   -- MANAGER
) AS roles(group_id)
WHERE NOT EXISTS (
    SELECT 1 FROM group_page_permissions g
    WHERE g.group_id = roles.group_id AND g.page_code = 'admin.approval-line-config' AND g.is_deleted = FALSE
);
```

> ⚠️ MASTER/MANAGER 그룹 UUID(`...0100`/`...0101`)는 `V43__seed_role_groups.sql` 의 시드값. probe 시 그 시드가 먼저 적용돼야 함. group_page_permissions 컬럼 7-action 명은 V42 DDL 과 대조 확인.

- [ ] **Step 6: 컴파일 + fresh Postgres probe**

Run: `./gradlew :services:auth-service:compileJava`
Expected: BUILD SUCCESSFUL.

probe(별도 컨테이너에 V1~V60 적용 후 V61):
```bash
docker run -d --name a2-probe -e POSTGRES_DB=auth_db -e POSTGRES_USER=samhan -e POSTGRES_PASSWORD=samhan -p 55440:5432 postgres:16
for i in $(seq 1 30); do docker exec a2-probe pg_isready -U samhan -d auth_db >/dev/null 2>&1 && break; sleep 1; done
P="docker exec -i a2-probe psql -v ON_ERROR_STOP=1 -U samhan -d auth_db"
for f in $(ls services/auth-service/src/main/resources/db/migration/V*.sql | sort -V); do $P -q -f - < "$f" || { echo FAIL $f; break; }; done
$P -c "SELECT document_type, sequence, label, step_type, required FROM approval_line_config ORDER BY sequence;"
$P -c "SELECT group_id, can_view, can_update FROM group_page_permissions WHERE page_code='admin.approval-line-config';"
docker rm -f a2-probe
```
Expected: 출고 3역할 + page-code grant 2행(MASTER/MANAGER). 전 마이그 ON_ERROR_STOP 통과.

- [ ] **Step 7: Commit**

```bash
git add services/auth-service/src/main/java/com/samhanair/logis/auth/domain/PageCode.java \
  services/auth-service/build.gradle \
  services/auth-service/src/main/java/com/samhanair/logis/auth/domain/ApprovalLineConfig.java \
  services/auth-service/src/main/java/com/samhanair/logis/auth/repository/ApprovalLineConfigRepository.java \
  services/auth-service/src/main/resources/db/migration/V61__approval_line_config.sql
git commit -m "feat(auth): approval_line_config 테이블+엔티티+page-code (A2-1 토대, V61)"
```

---

## Task 2: ApprovalLineConfigService (TDD)

역할 조회 + 역할별 권한 그룹/필수 갱신 로직. **fake repository 단위 TDD**.

**Files:**
- Create: `service/ApprovalLineConfigService.java`, `web/dto/ApprovalLineRoleView.java`
- Test: `service/ApprovalLineConfigServiceTest.java`

**Interfaces:**
- Consumes: `ApprovalLineConfigRepository`(Task 1), `PermissionGroupRepository`(group 이름 resolve, 기존).
- Produces: `record ApprovalLineRoleView(UUID id, int sequence, String label, StepType stepType, UUID approverGroupId, String approverGroupName, boolean required)`; `ApprovalLineConfigService.listRoles(String documentType)`, `updateRole(UUID id, UUID approverGroupId, boolean required)`.

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.samhanair.logis.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.samhanair.logis.approval.StepType;
import com.samhanair.logis.auth.domain.ApprovalLineConfig;
import com.samhanair.logis.auth.repository.ApprovalLineConfigRepository;
import com.samhanair.logis.auth.repository.PermissionGroupRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalLineConfigServiceTest {

    @Mock ApprovalLineConfigRepository repository;
    @Mock PermissionGroupRepository groupRepository;
    @InjectMocks ApprovalLineConfigService service;

    /** 리플렉션으로 테스트 픽스처 엔티티 생성(생성자 protected). */
    static ApprovalLineConfig role(int seq, String label, StepType type) {
        try {
            ApprovalLineConfig c = ApprovalLineConfig.class.getDeclaredConstructor().newInstance();
            for (var e : List.of("id", UUID.randomUUID(), "documentType", "SLIP_OUTBOUND",
                    "sequence", seq, "label", label, "stepType", type, "required", true).toString().isEmpty() ? List.of() : List.<Object>of()) {}
            set(c, "id", UUID.randomUUID()); set(c, "documentType", "SLIP_OUTBOUND");
            set(c, "sequence", seq); set(c, "label", label); set(c, "stepType", type); set(c, "required", true);
            return c;
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    static void set(Object o, String f, Object v) throws Exception {
        Field fld = ApprovalLineConfig.class.getDeclaredField(f); fld.setAccessible(true); fld.set(o, v);
    }

    @Test
    void listRoles_은_sequence순_역할을_반환한다() {
        when(repository.findByDocumentTypeOrderBySequenceAsc("SLIP_OUTBOUND"))
                .thenReturn(List.of(role(0, "작성자", StepType.CREATOR), role(1, "출고인", StepType.GROUP)));
        List<ApprovalLineRoleView> views = service.listRoles("SLIP_OUTBOUND");
        assertThat(views).hasSize(2);
        assertThat(views.get(0).label()).isEqualTo("작성자");
        assertThat(views.get(1).stepType()).isEqualTo(StepType.GROUP);
    }

    @Test
    void updateRole_은_GROUP역할에_권한그룹과_필수를_갱신한다() {
        UUID id = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        ApprovalLineConfig group = role(1, "출고인", StepType.GROUP);
        when(repository.findById(id)).thenReturn(Optional.of(group));
        when(repository.save(group)).thenReturn(group);
        ApprovalLineRoleView view = service.updateRole(id, groupId, false);
        assertThat(view.approverGroupId()).isEqualTo(groupId);
        assertThat(view.required()).isFalse();
    }

    @Test
    void updateRole_은_CREATOR역할에_권한그룹지정시_거부한다() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(role(0, "작성자", StepType.CREATOR)));
        assertThatThrownBy(() -> service.updateRole(id, UUID.randomUUID(), true))
                .hasMessageContaining("GROUP 역할");
    }

    @Test
    void updateRole_은_미존재시_404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateRole(id, null, true))
                .hasMessageContaining("찾을 수 없습니다");
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :services:auth-service:test --tests '*ApprovalLineConfigServiceTest'`
Expected: FAIL (컴파일 — Service/View 미존재).

- [ ] **Step 3: View DTO + Service 구현**

`web/dto/ApprovalLineRoleView.java`:
```java
package com.samhanair.logis.auth.web.dto;

import com.samhanair.logis.approval.StepType;
import java.util.UUID;

/** 결재라인 설정 역할 응답 — UUID 비공개 가드: approverGroupName 은 표시용, group UUID 는 picker 선택값. */
public record ApprovalLineRoleView(
        UUID id, int sequence, String label, StepType stepType,
        UUID approverGroupId, String approverGroupName, boolean required) {}
```

`service/ApprovalLineConfigService.java`:
```java
package com.samhanair.logis.auth.service;

import com.samhanair.logis.auth.domain.ApprovalLineConfig;
import com.samhanair.logis.auth.repository.ApprovalLineConfigRepository;
import com.samhanair.logis.auth.repository.PermissionGroupRepository;
import com.samhanair.logis.auth.web.dto.ApprovalLineRoleView;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결재라인 설정 — 전표종류별 역할 조회 + 역할별 권한 그룹/필수 갱신(선언적). */
@Service
@RequiredArgsConstructor
public class ApprovalLineConfigService {

    private final ApprovalLineConfigRepository repository;
    private final PermissionGroupRepository groupRepository;

    /** 전표 종류별 결재 역할(sequence 순). */
    @Transactional(readOnly = true)
    public List<ApprovalLineRoleView> listRoles(String documentType) {
        return repository.findByDocumentTypeOrderBySequenceAsc(documentType).stream()
                .map(this::toView)
                .toList();
    }

    /** 역할에 권한 그룹/필수 갱신. CREATOR 역할 그룹 지정은 거부. groupId=null 이면 그룹 해제. */
    @Transactional
    public ApprovalLineRoleView updateRole(UUID id, UUID approverGroupId, boolean required) {
        ApprovalLineConfig role = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "결재 역할을 찾을 수 없습니다: " + id));
        try {
            if (approverGroupId == null) {
                role.clearGroup();
            } else {
                role.assignGroup(approverGroupId);
            }
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, ex.getMessage());
        }
        role.changeRequired(required);
        return toView(repository.save(role));
    }

    private ApprovalLineRoleView toView(ApprovalLineConfig role) {
        String groupName = role.getApproverGroupId() == null ? null
                : groupRepository.findById(role.getApproverGroupId())
                        .map(g -> g.getName()).orElse(null);
        return new ApprovalLineRoleView(role.getId(), role.getSequence(), role.getLabel(),
                role.getStepType(), role.getApproverGroupId(), groupName, role.isRequired());
    }
}
```

> 테스트 헬퍼의 리플렉션 `role(...)` 은 plan 의 군더더기를 제거하고 `set(...)` 6필드만 세팅하도록 정리(위 Step1 의 첫 for 루프 잔재 삭제). 실제 작성 시 `set` 호출 6줄만 남길 것.

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :services:auth-service:test --tests '*ApprovalLineConfigServiceTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add services/auth-service/src/main/java/com/samhanair/logis/auth/service/ApprovalLineConfigService.java \
  services/auth-service/src/main/java/com/samhanair/logis/auth/web/dto/ApprovalLineRoleView.java \
  services/auth-service/src/test/java/com/samhanair/logis/auth/service/ApprovalLineConfigServiceTest.java
git commit -m "feat(auth): ApprovalLineConfigService listRoles/updateRole TDD"
```

---

## Task 3: ApprovalLineConfigController + 실 HTTP IT

admin 엔드포인트(GET 역할/PUT 갱신) + `@RequirePermission`. **Testcontainers 실 HTTP IT** — 비-MASTER 권한 검증([[feedback_enforcement_real_http_test]]).

**Files:**
- Create: `web/ApprovalLineConfigController.java`, `web/dto/UpdateApprovalLineRoleRequest.java`
- Test: `it/ApprovalLineConfigControllerIT.java`

**Interfaces:**
- Consumes: `ApprovalLineConfigService`(Task 2).
- Produces: `GET /auth/admin/approval-line-configs?documentType=...` → `ApiResponse<List<ApprovalLineRoleView>>`; `PUT /auth/admin/approval-line-configs/{id}` body `{approverGroupId?:UUID, required:boolean}` → `ApiResponse<ApprovalLineRoleView>`.

- [ ] **Step 1: 실패 IT 작성** (기존 auth IT base — AbstractPostgresIT 또는 @SpringBootTest 패턴 모방)

```java
package com.samhanair.logis.auth.it;

import static org.assertj.core.api.Assertions.assertThat;

// ... 기존 auth IT base(@SpringBootTest + Testcontainers + MockMvc) 상속/모방.
class ApprovalLineConfigControllerIT extends AbstractAuthPostgresIT {

    @Test
    void GET_역할목록_VIEW권한자_200_3역할() throws Exception {
        // V61 seed = SLIP_OUTBOUND 3역할. admin.approval-line-config VIEW 보유 계정(MANAGER) 헤더.
        var res = mockMvc.perform(get("/auth/admin/approval-line-configs?documentType=SLIP_OUTBOUND")
                .header("X-User-Id", MANAGER_ACCOUNT_ID)              // 비-MASTER (게이트 실검증)
                .header("X-Is-System-Master", "false"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(res).contains("작성자").contains("출고인").contains("검수인");
    }

    @Test
    void PUT_출고인역할_그룹지정_200() throws Exception {
        UUID roleId = /* SLIP_OUTBOUND 출고인 행 id 조회 */ ;
        UUID groupId = /* 임의 권한그룹(예 창고원 ...0103) */ ;
        mockMvc.perform(put("/auth/admin/approval-line-configs/" + roleId)
                .header("X-User-Id", MANAGER_ACCOUNT_ID).header("X-Is-System-Master", "false")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approverGroupId\":\"" + groupId + "\",\"required\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void GET_권한없는계정_403() throws Exception {
        mockMvc.perform(get("/auth/admin/approval-line-configs?documentType=SLIP_OUTBOUND")
                .header("X-User-Id", NO_PERMISSION_ACCOUNT_ID).header("X-Is-System-Master", "false"))
                .andExpect(status().isForbidden());
    }
}
```

> ⚠️ 기존 auth IT base 의 정확한 권한 셋업(@RequirePermission 실 enforce 경로 — DynamicPermissionClient/DirectDynamicPermissionClient)을 따른다. **@MockBean 으로 check() stub 금지** — V61 seed 의 실 group_page_permissions 계승으로 200/403 이 나야 [[enforcement-real-http-test]] 충족. MANAGER 계정은 V61 seed 로 admin.approval-line-config 보유.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :services:auth-service:test --tests '*ApprovalLineConfigControllerIT'`
Expected: FAIL (컨트롤러 미존재 404 또는 컴파일).

- [ ] **Step 3: Request DTO + Controller 구현**

`web/dto/UpdateApprovalLineRoleRequest.java`:
```java
package com.samhanair.logis.auth.web.dto;

import java.util.UUID;

/** 역할 갱신 요청 — approverGroupId null=그룹 해제. */
public record UpdateApprovalLineRoleRequest(UUID approverGroupId, boolean required) {}
```

`web/ApprovalLineConfigController.java`:
```java
package com.samhanair.logis.auth.web;

import com.samhanair.logis.auth.service.ApprovalLineConfigService;
import com.samhanair.logis.auth.web.dto.ApprovalLineRoleView;
import com.samhanair.logis.auth.web.dto.UpdateApprovalLineRoleRequest;
import com.samhanair.logis.common.response.ApiResponse;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.RequirePermission;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 결재라인 설정 admin 엔드포인트(인사 그룹, admin.approval-line-config). */
@RestController
@RequestMapping("/auth/admin")
@RequiredArgsConstructor
public class ApprovalLineConfigController {

    private final ApprovalLineConfigService service;

    /** 전표 종류별 결재 역할 조회. */
    @GetMapping("/approval-line-configs")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.VIEW)
    public ApiResponse<List<ApprovalLineRoleView>> listRoles(@RequestParam String documentType) {
        return ApiResponse.ok(service.listRoles(documentType));
    }

    /** 역할에 권한 그룹/필수 갱신. */
    @PutMapping("/approval-line-configs/{id}")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.UPDATE)
    public ApiResponse<ApprovalLineRoleView> updateRole(
            @PathVariable UUID id, @RequestBody UpdateApprovalLineRoleRequest request) {
        return ApiResponse.ok(service.updateRole(id, request.approverGroupId(), request.required()));
    }
}
```

> import 경로(ApiResponse/RequirePermission/PermissionAction)는 기존 `PermissionGroupController` 와 동일 패키지 사용 — 그 파일의 실제 import 로 정합.

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :services:auth-service:test --tests '*ApprovalLineConfigControllerIT'`
Expected: PASS (3 tests, 실 Postgres + 실 권한 계승으로 200/403).

- [ ] **Step 5: auth 전체 test 회귀 + Commit**

Run: `./gradlew :services:auth-service:test`
Expected: BUILD SUCCESSFUL(기존 회귀 0).

```bash
git add services/auth-service/src/main/java/com/samhanair/logis/auth/web/ApprovalLineConfigController.java \
  services/auth-service/src/main/java/com/samhanair/logis/auth/web/dto/UpdateApprovalLineRoleRequest.java \
  services/auth-service/src/test/java/com/samhanair/logis/auth/it/ApprovalLineConfigControllerIT.java
git commit -m "feat(auth): ApprovalLineConfigController + 실HTTP IT(비-MASTER 200/403)"
```

---

## Task 4: 데스크톱 결재라인 설정 페이지

API 클라이언트 + 설정 페이지 + 라우트 + 사이드바 + page-code 카탈로그.

**Files:**
- Create: `src/renderer/api/approvalLineConfigApi.ts`, `src/renderer/routes/ApprovalLineConfigPage.tsx`
- Modify: `src/renderer/routes/index.tsx`, `src/renderer/components/AppLayout.tsx`, page-code 카탈로그(`PermissionMatrixPage.tsx`/`permissionPageCatalog`)
- Test: `src/renderer/routes/__tests__/ApprovalLineConfigPage.test.tsx`

**Interfaces:**
- Consumes: 기존 `fetchPermissionGroups`(api/permissionGroupsApi), `apiClient`, design-system(Select/Button/Card/Toast), `usePermissions`.
- Produces: `fetchApprovalLineRoles(documentType): Promise<ApprovalLineRole[]>`, `updateApprovalLineRole(id, {approverGroupId, required}): Promise<ApprovalLineRole>`, `ApprovalLineConfigPage`.

- [ ] **Step 1: API 클라이언트 작성**

```ts
// src/renderer/api/approvalLineConfigApi.ts
import { apiClient } from './client'
import type { ApiEnvelope } from './types'

export type StepType = 'CREATOR' | 'GROUP' | 'USER'

export interface ApprovalLineRole {
  id: string
  sequence: number
  label: string
  stepType: StepType
  approverGroupId: string | null
  approverGroupName: string | null
  required: boolean
}

/** 결재라인 설정 대상 전표 종류(A2-1=출고만 seed). */
export const DOC_TYPES: { value: string; label: string }[] = [
  { value: 'SLIP_OUTBOUND', label: '출고전표' },
]

export async function fetchApprovalLineRoles(documentType: string): Promise<ApprovalLineRole[]> {
  const res = await apiClient.get<ApiEnvelope<ApprovalLineRole[]>>(
    `/auth/admin/approval-line-configs?documentType=${encodeURIComponent(documentType)}`,
  )
  return res.data.data ?? []
}

export async function updateApprovalLineRole(
  id: string,
  payload: { approverGroupId: string | null; required: boolean },
): Promise<ApprovalLineRole> {
  const res = await apiClient.put<ApiEnvelope<ApprovalLineRole>>(
    `/auth/admin/approval-line-configs/${id}`,
    payload,
  )
  return res.data.data
}
```

> `ApiEnvelope`/`apiClient` import 경로는 기존 `permissionGroupsApi.ts` 와 동일하게 맞춘다.

- [ ] **Step 2: 페이지 컴포넌트 작성** (권한그룹 관리 페이지 패턴 모방)

```tsx
// src/renderer/routes/ApprovalLineConfigPage.tsx
import { Button, Card, Select } from '@samhan/design-system'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { DOC_TYPES, fetchApprovalLineRoles, updateApprovalLineRole } from '../api/approvalLineConfigApi'
import { fetchPermissionGroups } from '../api/permissionGroupsApi'
import { usePageTitle } from '../hooks/usePageTitle'

/** 결재라인 설정 — 전표 종류별 역할에 권한 그룹/필수 지정(선언적, enforcement=A2-2). */
export function ApprovalLineConfigPage() {
  usePageTitle('결재라인 설정')
  const queryClient = useQueryClient()
  const [docType, setDocType] = useState(DOC_TYPES[0].value)
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  const rolesQuery = useQuery({
    queryKey: ['admin', 'approval-line-config', docType],
    queryFn: () => fetchApprovalLineRoles(docType),
  })
  const groupsQuery = useQuery({
    queryKey: ['admin', 'permission-groups'],
    queryFn: fetchPermissionGroups,
  })
  const updateMutation = useMutation({
    mutationFn: (v: { id: string; approverGroupId: string | null; required: boolean }) =>
      updateApprovalLineRole(v.id, { approverGroupId: v.approverGroupId, required: v.required }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'approval-line-config', docType] })
      setToast({ type: 'success', message: '결재라인 설정을 저장했습니다.' })
    },
    onError: () => setToast({ type: 'error', message: '저장 중 오류가 발생했습니다.' }),
  })

  const groups = groupsQuery.data ?? []

  return (
    <Card>
      <h1>결재라인 설정</h1>
      <p style={{ color: '#667085', fontSize: 13 }}>
        전표 종류별 결재 역할에 권한 그룹과 필수여부를 지정합니다. (적용은 후속 슬라이스)
      </p>
      <Select value={docType} onChange={(e) => setDocType(e.target.value)} aria-label="전표 종류">
        {DOC_TYPES.map((d) => <option key={d.value} value={d.value}>{d.label}</option>)}
      </Select>

      <table data-testid="approval-line-role-table" style={{ width: '100%', marginTop: 16 }}>
        <thead><tr><th>순서</th><th>역할</th><th>권한 그룹</th><th>필수</th><th></th></tr></thead>
        <tbody>
          {(rolesQuery.data ?? []).map((role) => (
            <ApprovalRoleRow
              key={role.id}
              role={role}
              groups={groups}
              saving={updateMutation.isPending}
              onSave={(approverGroupId, required) =>
                updateMutation.mutate({ id: role.id, approverGroupId, required })}
            />
          ))}
        </tbody>
      </table>
      {toast && <div role="status" data-testid="approval-line-toast">{toast.message}</div>}
    </Card>
  )
}

function ApprovalRoleRow(props: {
  role: import('../api/approvalLineConfigApi').ApprovalLineRole
  groups: { id: string; name: string }[]
  saving: boolean
  onSave: (approverGroupId: string | null, required: boolean) => void
}) {
  const { role, groups } = props
  const isCreator = role.stepType === 'CREATOR'
  const [groupId, setGroupId] = useState(role.approverGroupId ?? '')
  const [required, setRequired] = useState(role.required)

  return (
    <tr data-testid={`approval-role-${role.label}`}>
      <td>{role.sequence + 1}</td>
      <td>{role.label}</td>
      <td>
        {isCreator ? (
          <span style={{ color: '#98a2b3' }}>전표 작성자 자동</span>
        ) : (
          <Select value={groupId} onChange={(e) => setGroupId(e.target.value)} aria-label={`${role.label} 권한 그룹`}>
            <option value="">(미지정)</option>
            {groups.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
          </Select>
        )}
      </td>
      <td>
        <input type="checkbox" checked={required} disabled={isCreator}
          onChange={(e) => setRequired(e.target.checked)} aria-label={`${role.label} 필수`} />
      </td>
      <td>
        {!isCreator && (
          <Button size="sm" disabled={props.saving}
            onClick={() => props.onSave(groupId === '' ? null : groupId, required)}>저장</Button>
        )}
      </td>
    </tr>
  )
}
```

> design-system 의 실제 export 명(Select/Button/Card props)은 기존 페이지 import 와 대조. Toast 컴포넌트가 있으면 inline div 대신 그것 사용. `usePageTitle` 경로는 기존 페이지에서 확인.

- [ ] **Step 3: 라우트 등록** (`routes/index.tsx`)

import 추가 + 라우터 배열에(권한그룹 라우트 옆):
```tsx
import { ApprovalLineConfigPage } from './ApprovalLineConfigPage'
```
```tsx
{
  path: '/admin/approval-line-config',
  element: (
    <PermissionGuard pageCode="admin.approval-line-config" action="view">
      <ApprovalLineConfigPage />
    </PermissionGuard>
  ),
},
```

- [ ] **Step 4: 사이드바 메뉴 추가** (`AppLayout.tsx` 인사 카테고리 :1093-1150)

`activeTargets` 배열에 `'/admin/approval-line-config'` 추가 + 권한위임 SidebarLink 다음에:
```tsx
<SidebarLink
  to="/admin/approval-line-config"
  show={dynamicCanAccess('admin.approval-line-config', 'view')}
  data-testid="sidebar-hr-approval-line-config"
>
  결재라인 설정
</SidebarLink>
```
> `dynamicCanAccess` 선언 형태는 같은 파일의 기존 `showPermissionAdmin` 등과 동일 패턴으로(상단에 `const showApprovalLineConfig = dynamicCanAccess('admin.approval-line-config', 'view')` 추출해도 됨).

- [ ] **Step 5: page-code 카탈로그 등록**

권한 매트릭스 카탈로그(`PermissionMatrixPage.tsx`/`permissionPageCatalog`)의 그룹웨어 또는 인사 묶음에 `'admin.approval-line-config'` 추가 + 라벨 맵에 `'admin.approval-line-config': '결재라인 설정'`. FE PageCode union 타입에도 추가([[feedback_fe_canaccess_pagecode_be_match]] — BE @RequirePermission 와 정확 일치).

- [ ] **Step 6: vitest 작성 + 타입검증**

`__tests__/ApprovalLineConfigPage.test.tsx` — 역할 테이블 렌더(작성자=자동 텍스트, 출고인/검수인=그룹 Select) + 저장 mutation 호출(mock api). 기존 페이지 테스트 패턴 모방([[feedback_inprocess_mock_principles]]).

Run: `cd clients/desktop && npm run typecheck && npx vitest run src/renderer/routes/__tests__/ApprovalLineConfigPage.test.tsx`
Expected: 0 type errors + vitest PASS.

- [ ] **Step 7: Commit**

```bash
git add clients/desktop/src/renderer/api/approvalLineConfigApi.ts \
  clients/desktop/src/renderer/routes/ApprovalLineConfigPage.tsx \
  clients/desktop/src/renderer/routes/index.tsx \
  clients/desktop/src/renderer/components/AppLayout.tsx \
  clients/desktop/src/renderer/routes/PermissionMatrixPage.tsx \
  clients/desktop/src/renderer/routes/__tests__/ApprovalLineConfigPage.test.tsx
git commit -m "feat(desktop): 결재라인 설정 페이지(인사 그룹) + API + 라우트 + page-code 카탈로그"
```

---

## Task 5: 통합 검증 (라이브 QA 토대)

**Files:** 검증만(산출 없음).

- [ ] **Step 1: 변경 모듈 전체 test**

Run: `./gradlew :services:auth-service:test` · `cd clients/desktop && npm run typecheck && npx vitest run`
Expected: 양쪽 green([[feedback_changed_module_full_test_before_push]]).

- [ ] **Step 2: fresh Postgres probe 재확인** (Task1 Step6 절차) — V61 seed + grant 무손상.

- [ ] **Step 3: 라이브 스택 부팅 + QA 캡처 토대** (듀얼리뷰 라운드의 QA 에이전트가 수행)
- 라이브 게이트웨이 + **비-MASTER 위임 MANAGER 로그인** → 인사 > 결재라인 설정 → 출고전표 출고인/검수인에 권한 그룹 지정 + 필수 토글 + 저장 → 재조회 persist 캡처. **MASTER bypass 회피**(MANAGER 계정으로 권한 게이트 실증).

---

## Self-Review

**1. Spec coverage** (spec 2026-06-21-approval-line-config-a2-design.md):
- §4 approval_line_config(전표종류별 역할 catalog, step_type/approver_group_id/required) → Task 1 엔티티+V61. ✓
- §4 작성자=CREATOR(requesterId 해석은 소비측) → step_type=CREATOR seed + 그룹 지정 거부. ✓
- §4 StepType 재사용 → approval-core 의존+enum. ✓
- §5 page-code admin.approval-line-config(일반, MANAGEMENT 미편입) + seed MASTER+MANAGER → Task1 PageCode+V61 grant. ✓
- §5 메뉴(인사 그룹, group picker, 필수 토글) → Task 4. ✓
- §5 canAccess=BE @RequirePermission 일치 → Task4 Step5 + Task3 IT. ✓
- §6 QA 비-MASTER → Task3 IT + Task5 Step3. ✓
- §3 group_page_permissions 미조작(선언적) → Service 가 approval_line_config 만 write. ✓
- §8 A2-2 요건 → 본 plan 범위 외(spec §8 박제). ✓

**2. Placeholder scan**: Task3 IT 의 `roleId/groupId 조회` 와 Task4 Step5/Step6 은 "기존 패턴 대조" 지시 — 실 코드 골격은 제공, 환경별 정확 import/base 클래스는 대조 필요(플레이스홀더 아님). Task2 Step3 리플렉션 헬퍼 잔재 삭제 명시.

**3. Type consistency**:
- `ApprovalLineRoleView`(id/sequence/label/stepType/approverGroupId/approverGroupName/required) — Task2 정의 ↔ Task3 응답 ↔ Task4 `ApprovalLineRole` 타입 1:1. ✓
- `updateRole(UUID id, UUID approverGroupId, boolean required)` — Task2 ↔ Task3 controller ↔ Task4 updateApprovalLineRole. ✓
- `StepType`(CREATOR/GROUP/USER) — approval-core enum(BE) ↔ FE union 'CREATOR'|'GROUP'|'USER'. ✓
- page-code `admin.approval-line-config` — PageCode enum ↔ @RequirePermission ↔ V61 seed ↔ FE PermissionGuard/catalog 전부 동일 문자열. ✓

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-06-21-approval-line-config-a2-1.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — 태스크별 fresh subagent, 태스크 간 리뷰. 코드 구현은 **Codex 디스패치**([[feedback_codex_implements_claude_reviews]]), Claude 빌드·테스트·커밋 대행 + 듀얼리뷰.

**2. Inline Execution** — executing-plans 배치 + 체크포인트.

**어느 방식으로 진행할까요?**
