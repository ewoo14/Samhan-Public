# A1 — 공통 결재 엔진 일반화 (approval-core 추출 + groupware 이관) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **구현 주체**: 코드(entity/service/test/gradle/sql)는 **Codex 가 구현**([[feedback_codex_implements_claude_reviews]]), Claude 는 리뷰·검증·commit 대행. 본 plan/spec/메모리는 Claude 작성.
>
> spec: [docs/superpowers/specs/2026-06-21-approval-engine-a1-design.md](../specs/2026-06-21-approval-engine-a1-design.md)

**Goal:** 그룹웨어에 갇힌 결재 chain 엔진(승인/반려/순차/상태전이)을 `shared:approval-core` @MappedSuperclass 베이스로 추출하고, groupware `ApprovalLine`/`ApprovalStep` 을 그 베이스 상속으로 이관하되 라이브 결재 동작을 무손실 보존한다.

**Architecture:** collab-core 와 동일 모듈 패턴(@MappedSuperclass 베이스 + 제네릭 서비스 + Port, concrete @Entity/@Table/Flyway 는 소비 서비스 잔류). 단 **base = 스칼라 컬럼 + 무상태 step 로직**(steps 컬렉션·@Version·그룹웨어 전용 필드는 concrete 소유 — Hibernate 가 @MappedSuperclass 의 제네릭 @OneToMany 를 매핑 못 하므로). step 모델은 `stepType(CREATOR|GROUP|USER)` union 으로 일반화하되 A1 은 **USER 모드만** 실배선(groupware 회귀 보존), GROUP/CREATOR 컬럼은 nullable 로 선반영(A2/A4 에서 enforce).

**Tech Stack:** Java 17 · Spring Boot 3 · Hibernate 6 / JPA · Flyway · Gradle multi-project · JUnit 5 + AssertJ · Testcontainers(PostgreSQL) · React/Vitest(FE 회귀).

## Global Constraints

- **BaseEntity 7 audit + Soft Delete 의무** — 모든 entity/@MappedSuperclass 는 `com.samhanair.logis.common.entity.BaseEntity` 상속, `@SQLRestriction("is_deleted = false")` ([[project_build_conventions]]).
- **한국어 Javadoc 의무** — 모든 신규 클래스/공개 메서드 한국어 Javadoc.
- **도메인 메서드 chain** — 직접 setter 금지, 도메인 메서드로 상태 전이(현 ApprovalLine 패턴 유지).
- **Flyway**: 신규 V*.sql 만 추가(적용된 마이그 불변 [[feedback_applied_migration_immutable]]). **additive nullable 컬럼만**, 비결정적 backfill 컬럼은 **NOT NULL 절대 금지**([[feedback_no_backlog_strict]] 아닌 [[project_build_conventions]] 정책 + spec §5). push 전 **fresh Postgres probe** 검증([[feedback_migration_fresh_postgres_probe]]).
- **`approverUserId` ↔ 기존 `approver_id` 컬럼 매핑** — 컬럼명 변경 없음(`@Column(name = "approver_id")`).
- **DTO JSON 계약 불변** — `ApprovalLineAdminResponse` 의 JSON 필드명(`approverId` 등)은 그대로 유지하여 FE 계약 무변경(entity 필드는 `approverUserId` 로 바뀌어도 DTO 가 절연).
- **A1 = USER stepType 만 실배선** — groupware 는 항상 `StepType.USER`. GROUP/CREATOR 는 컬럼만 nullable 선반영, 로직 미구현(A2/A4).
- **approval-core 는 realtime 비의존** — collab 의 `@ConditionalOnBean(RealtimeBroker)` 복붙 금지(spec §3).
- **빌드 명령은 `./gradlew`** (Bash 도구에서 `cmd /c gradlew.bat` 미작동, 검증됨). 프로젝트는 비-한글 경로(C:\dev\Samhan-Public)라 `test` 태스크 정상.
- **커밋 한국어**([[feedback_korean_commits]]), `git update-index --chmod=+x gradlew` 불요(기존 추적).

---

## File Structure

**신규 (`shared/approval-core/`)**
- `build.gradle` — java-library, api `shared:common`, jpa/persistence/autoconfigure, compileOnly hibernate-core, lombok. **realtime 비의존**.
- `src/main/java/com/samhanair/logis/approval/ApprovalStatus.java` — 이관(groupware → approval-core).
- `.../approval/ApprovalStepStatus.java` — 이관.
- `.../approval/StepType.java` — 신규 enum(CREATOR/GROUP/USER).
- `.../approval/ApprovalLineBase.java` — @MappedSuperclass, 스칼라 + chain 로직.
- `.../approval/ApprovalStepBase.java` — @MappedSuperclass, step 컬럼 + step 전이.
- `.../approval/ApprovalRepositoryPort.java` — 영속성 SPI(제네릭).
- `.../approval/ApprovalLineService.java` — 제네릭 엔진 서비스(A2 slip 첫 소비, A1 은 fake 테스트).
- `.../approval/ApprovalCoreAutoConfiguration.java` — 모듈 경계 anchor(realtime 비의존).
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `src/test/java/.../approval/{ApprovalStepBaseTest,ApprovalLineBaseTest,ApprovalLineServiceTest}.java` — fake concrete 단위테스트.

**수정 (`services/groupware-service/`)**
- `build.gradle` — `implementation project(':shared:approval-core')` 추가.
- `domain/ApprovalLine.java` — `extends ApprovalLineBase`(스칼라 제거, content/template/version/steps/overlay 잔류).
- `domain/ApprovalStep.java` — `extends ApprovalStepBase`(컬럼 제거, @ManyToOne/@Id 잔류, createUser).
- `domain/ApprovalStatus.java`, `domain/ApprovalStepStatus.java` — **삭제**(approval-core 로 이관).
- enum 을 import 하던 전 파일 — import 경로 갱신(`groupware.domain` → `approval`).
- `service/ApprovalLineService.java` · `dto/ApprovalLineAdminResponse.java` — accessor 갱신(`getApproverId`→`getApproverUserId`), JSON 필드명 불변.
- `src/main/resources/db/migration/V8__approval_core_generalization.sql` — 신규(additive nullable).

**수정 (root)**
- `settings.gradle` — `shared:approval-core` 등록.

---

## Task 1: approval-core 모듈 생성 + 공유 enum 이관

groupware 의 결재 enum 2종을 approval-core 로 옮겨 단일 소스화하고, 빈 모듈을 빌드 그래프에 등록한다. 이 태스크 끝에서 **groupware 가 새 import 경로로 컴파일·기존 테스트 통과**(behavior-neutral)해야 한다.

**Files:**
- Create: `shared/approval-core/build.gradle`
- Create: `shared/approval-core/src/main/java/com/samhanair/logis/approval/ApprovalStatus.java`
- Create: `shared/approval-core/src/main/java/com/samhanair/logis/approval/ApprovalStepStatus.java`
- Create: `shared/approval-core/src/main/java/com/samhanair/logis/approval/StepType.java`
- Create: `shared/approval-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `shared/approval-core/src/main/java/com/samhanair/logis/approval/ApprovalCoreAutoConfiguration.java`
- Delete: `services/groupware-service/src/main/java/com/samhanair/logis/groupware/domain/ApprovalStatus.java`
- Delete: `services/groupware-service/src/main/java/com/samhanair/logis/groupware/domain/ApprovalStepStatus.java`
- Modify: `settings.gradle`, `services/groupware-service/build.gradle`
- Modify: groupware 의 `ApprovalStatus`/`ApprovalStepStatus` import 전 파일

**Interfaces:**
- Produces: `com.samhanair.logis.approval.ApprovalStatus {PENDING, IN_PROGRESS, APPROVED, REJECTED, WITHDRAWN}`, `ApprovalStepStatus {PENDING, APPROVED, REJECTED}`, `StepType {CREATOR, GROUP, USER}`.

- [ ] **Step 1: settings.gradle 에 모듈 등록**

`include` 목록의 `shared:collab-core` 아래에 추가, projectDir 매핑도 추가:

```gradle
include 'shared:approval-core'
```
```gradle
project(':shared:approval-core').projectDir            = file('shared/approval-core')
```

- [ ] **Step 2: approval-core build.gradle 작성**

```gradle
/*
 * shared:approval-core — 전 전표 공용 결재 엔진(@MappedSuperclass 베이스 + 제네릭 서비스).
 *
 * 모듈 경계 (collab-core 와 동형):
 *   * @MappedSuperclass 베이스(line/step)만
 *   * consumer-주입 Port 위의 제네릭 서비스
 *   * concrete @Entity / @Table / Flyway 는 각 소비 서비스 잔류
 *
 * collab-core 와 달리 realtime(SSE) 의존 없음 — 결재 알림은 소비 서비스가 배선.
 */
plugins {
    id 'java-library'
    id 'io.spring.dependency-management'
}

dependencyManagement {
    imports {
        mavenBom org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES
    }
}

dependencies {
    api project(':shared:common')

    api 'org.springframework.boot:spring-boot-autoconfigure'
    api 'org.springframework.boot:spring-boot'
    api 'org.springframework.data:spring-data-jpa'
    api 'jakarta.persistence:jakarta.persistence-api'

    compileOnly 'org.hibernate.orm:hibernate-core'

    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    compileOnly "org.projectlombok:lombok:${lombokVersion}"
    annotationProcessor "org.projectlombok:lombok:${lombokVersion}"

    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.3'
    testImplementation 'org.assertj:assertj-core:3.26.3'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 3: enum 3종 작성** (groupware 의 두 enum 을 패키지 `com.samhanair.logis.approval` 로 옮기고 Javadoc 보존, StepType 신규)

`ApprovalStatus.java` / `ApprovalStepStatus.java` 는 기존 groupware 파일 본문을 그대로 옮기되 `package com.samhanair.logis.approval;` 로 변경. 신규 `StepType.java`:

```java
package com.samhanair.logis.approval;

/**
 * 결재 단계의 결재자 식별 방식.
 *
 * <ul>
 *   <li>{@link #USER} — 특정 사원 1명 직접 지정(approverUserId). 그룹웨어 자유형 결재.</li>
 *   <li>{@link #GROUP} — 권한 그룹(approverGroupId 표시 + requiredPageCode enforce).
 *       그룹의 결재 page-code 를 계승한 사원이면 누구나 승인(A2 배선).</li>
 *   <li>{@link #CREATOR} — 전표 작성자(createdBy) 본인 단계(A4 배선).</li>
 * </ul>
 *
 * <p>A1 은 {@link #USER} 만 실배선하고 GROUP/CREATOR 는 컬럼만 선반영한다.
 */
public enum StepType {
    CREATOR,
    GROUP,
    USER
}
```

- [ ] **Step 4: ApprovalCoreAutoConfiguration + imports 작성** (모듈 경계 anchor — realtime 비의존, A1 은 등록 빈 없음)

```java
package com.samhanair.logis.approval;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * shared:approval-core 자동 설정 진입점(모듈 경계 anchor).
 *
 * <p>본 module 은 @MappedSuperclass 베이스 + 제네릭 서비스만 제공한다. 구체 서비스 bean 은 소비
 * service 가 자기 entity/repository 타입으로 등록한다(collab-core 와 동일). collab-core 와 달리
 * realtime broker 의존이 없으므로 조건부 빈을 두지 않는다(결재 알림은 소비 서비스가 배선).
 */
@AutoConfiguration
public class ApprovalCoreAutoConfiguration {
}
```

imports 파일 내용(1줄):

```
com.samhanair.logis.approval.ApprovalCoreAutoConfiguration
```

- [ ] **Step 5: groupware enum 삭제 + import 경로 갱신**

groupware 의 `domain/ApprovalStatus.java`·`domain/ApprovalStepStatus.java` 삭제. groupware build.gradle 의존 추가(`implementation project(':shared:collab-core')` 아래):

```gradle
    implementation project(':shared:approval-core')
```

`com.samhanair.logis.groupware.domain.ApprovalStatus`/`ApprovalStepStatus` 를 import/참조하던 모든 파일의 import 를 `com.samhanair.logis.approval.ApprovalStatus`/`ApprovalStepStatus` 로 교체. 후보 grep:

```bash
grep -rl "groupware.domain.ApprovalStatus\|groupware.domain.ApprovalStepStatus\|import com.samhanair.logis.groupware.domain.Approval" services/groupware-service/src
```

같은 패키지(`groupware.domain`) 내에서 enum 을 import 없이 참조하던 `ApprovalLine.java`/`ApprovalStep.java` 는 명시 import 추가 필요(패키지가 달라짐).

- [ ] **Step 6: 컴파일 + groupware 기존 테스트 회귀 확인**

Run: `./gradlew :shared:approval-core:compileJava :services:groupware-service:test`
Expected: BUILD SUCCESSFUL. groupware 기존 `ApprovalLineServiceTest` 등 전부 PASS(enum 이관은 behavior-neutral).

- [ ] **Step 7: Commit**

```bash
git add shared/approval-core settings.gradle services/groupware-service/build.gradle services/groupware-service/src
git commit -F - <<'EOF'
feat(approval-core): 모듈 생성 + 결재 enum 이관 (ApprovalStatus/StepStatus + StepType)

groupware 의 ApprovalStatus/ApprovalStepStatus 를 shared:approval-core 로 단일 소스화.
StepType(CREATOR|GROUP|USER) 신규. realtime 비의존 autoconfig anchor. behavior-neutral.
EOF
```

---

## Task 2: ApprovalStepBase (@MappedSuperclass)

step 의 컬럼·전이 로직을 베이스로 추출. **fake concrete 로 단위 TDD**(DB 불요). USER 모드 매칭 + 승인/반려 전이를 검증.

**Files:**
- Create: `shared/approval-core/src/main/java/com/samhanair/logis/approval/ApprovalStepBase.java`
- Test: `shared/approval-core/src/test/java/com/samhanair/logis/approval/ApprovalStepBaseTest.java`

**Interfaces:**
- Produces: `ApprovalStepBase` (abstract @MappedSuperclass). 패키지-가시 메서드 `void approve(UUID actorUserId)`, `void reject(UUID actorUserId, String reason)`, `boolean matchesActor(UUID actorUserId)`; protected `void initUserStep(UUID approverUserId, int sequence)`; @Getter 로 `getStepType/getApproverUserId/getApproverGroupId/getRequiredPageCode/getApprovedByUserId/getSequence/getStatus/getDecidedAt/getReason/getSignaturePngSnapshot/getSignedAt`.

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.samhanair.logis.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovalStepBaseTest {

    /** DB 없이 베이스 로직만 검증하기 위한 테스트 전용 concrete. */
    static class FakeStep extends ApprovalStepBase {
        static FakeStep user(UUID approverUserId, int sequence) {
            FakeStep s = new FakeStep();
            s.initUserStep(approverUserId, sequence);
            return s;
        }
    }

    @Test
    void initUserStep_은_USER타입_PENDING_으로_생성한다() {
        UUID approver = UUID.randomUUID();
        FakeStep step = FakeStep.user(approver, 0);
        assertThat(step.getStepType()).isEqualTo(StepType.USER);
        assertThat(step.getApproverUserId()).isEqualTo(approver);
        assertThat(step.getSequence()).isZero();
        assertThat(step.getStatus()).isEqualTo(ApprovalStepStatus.PENDING);
        assertThat(step.getApprovedByUserId()).isNull();
    }

    @Test
    void matchesActor_은_USER모드에서_approverUserId_동일성으로_판정한다() {
        UUID approver = UUID.randomUUID();
        FakeStep step = FakeStep.user(approver, 0);
        assertThat(step.matchesActor(approver)).isTrue();
        assertThat(step.matchesActor(UUID.randomUUID())).isFalse();
    }

    @Test
    void approve_은_APPROVED전이_실승인자_처리시각을_기록한다() {
        UUID approver = UUID.randomUUID();
        FakeStep step = FakeStep.user(approver, 0);
        step.approve(approver);
        assertThat(step.getStatus()).isEqualTo(ApprovalStepStatus.APPROVED);
        assertThat(step.getApprovedByUserId()).isEqualTo(approver);
        assertThat(step.getDecidedAt()).isNotNull();
    }

    @Test
    void reject_은_REJECTED전이_사유를_기록한다() {
        UUID approver = UUID.randomUUID();
        FakeStep step = FakeStep.user(approver, 0);
        step.reject(approver, "보완 필요");
        assertThat(step.getStatus()).isEqualTo(ApprovalStepStatus.REJECTED);
        assertThat(step.getReason()).isEqualTo("보완 필요");
        assertThat(step.getDecidedAt()).isNotNull();
    }

    @Test
    void 이미_처리된_단계는_재처리를_거부한다() {
        UUID approver = UUID.randomUUID();
        FakeStep step = FakeStep.user(approver, 0);
        step.approve(approver);
        assertThatThrownBy(() -> step.approve(approver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 처리된");
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:approval-core:test --tests '*ApprovalStepBaseTest'`
Expected: FAIL (컴파일 실패 — `ApprovalStepBase` 미존재).

- [ ] **Step 3: ApprovalStepBase 구현**

```java
package com.samhanair.logis.approval;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결재 chain 단계의 공통 베이스(전 전표 공용). 컬럼과 단계 전이 로직만 보유하고,
 * 부모 결재선으로의 {@code @ManyToOne} 역참조·{@code @Id} 는 소비 서비스 concrete @Entity 가 소유한다
 * (Hibernate 가 @MappedSuperclass 의 per-service 관계 타입을 매핑하지 못하므로).
 *
 * <p>결재자 식별은 {@link StepType} 으로 분기한다 — A1 은 {@link StepType#USER}(approverUserId)만
 * 실배선하고, GROUP(approverGroupId/requiredPageCode)·CREATOR 는 컬럼만 nullable 로 선반영한다.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ApprovalStepBase extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", length = 20)
    private StepType stepType;

    /** USER 모드 결재자 사원 UUID. 기존 컬럼 {@code approver_id} 에 매핑(컬럼명 불변). */
    @Column(name = "approver_id")
    private UUID approverUserId;

    /** GROUP 모드 권한 그룹 UUID(표시·설정용, A2). */
    @Column(name = "approver_group_id")
    private UUID approverGroupId;

    /** GROUP 모드 결재 권한 page-code(enforce 용, A2). */
    @Column(name = "required_page_code", length = 100)
    private String requiredPageCode;

    /** 실제 승인 처리자 user UUID — approve 시 기록. */
    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    /** chain 순서(0-base ASC). */
    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStepStatus status;

    /** 처리 시각(승인/반려). */
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** 반려 사유(REJECTED 인 경우만 의미). */
    @Column(name = "reason", length = 500)
    private String reason;

    /** 결재 시점 동결 서명 PNG(A3 에서 채움). list 조회 부하 회피 위해 LAZY. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "signature_png_snapshot")
    private byte[] signaturePngSnapshot;

    /** 서명 동결 시각(A3). */
    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    /** USER 모드 단계 초기화 — concrete create 가 호출. */
    protected void initUserStep(UUID approverUserId, int sequence) {
        if (approverUserId == null) {
            throw new IllegalArgumentException("approverUserId 필수");
        }
        this.stepType = StepType.USER;
        this.approverUserId = approverUserId;
        this.sequence = sequence;
        this.status = ApprovalStepStatus.PENDING;
    }

    /** 액터가 본 단계의 결재 권한자인지(A1=USER 모드 동일성). GROUP/CREATOR 는 A2/A4. */
    boolean matchesActor(UUID actorUserId) {
        return this.stepType == StepType.USER
                && this.approverUserId != null
                && this.approverUserId.equals(actorUserId);
    }

    /** 본 단계 승인. 호출 흐름은 {@link ApprovalLineBase#approve(UUID)} 가 보장. */
    void approve(UUID actorUserId) {
        ensurePending();
        this.status = ApprovalStepStatus.APPROVED;
        this.approvedByUserId = actorUserId;
        this.decidedAt = LocalDateTime.now();
    }

    /** 본 단계 반려. */
    void reject(UUID actorUserId, String reason) {
        ensurePending();
        this.status = ApprovalStepStatus.REJECTED;
        this.approvedByUserId = actorUserId;
        this.reason = reason;
        this.decidedAt = LocalDateTime.now();
    }

    private void ensurePending() {
        if (this.status != ApprovalStepStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 결재 단계입니다: " + this.status);
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :shared:approval-core:test --tests '*ApprovalStepBaseTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/approval-core/src
git commit -m "feat(approval-core): ApprovalStepBase @MappedSuperclass + USER 모드 전이 TDD"
```

---

## Task 3: ApprovalLineBase (@MappedSuperclass) — chain 엔진

결재선 chain 로직(currentStep/approve/reject/withdraw/상태전이)을 베이스로 추출. steps 컬렉션은 보유하지 않고 abstract `stepsView()` 로 읽는다. **fake concrete + fake step 으로 TDD**.

**Files:**
- Create: `shared/approval-core/src/main/java/com/samhanair/logis/approval/ApprovalLineBase.java`
- Test: `shared/approval-core/src/test/java/com/samhanair/logis/approval/ApprovalLineBaseTest.java`

**Interfaces:**
- Consumes: `ApprovalStepBase`(Task 2), `ApprovalStatus`/`ApprovalStepStatus`(Task 1).
- Produces: `ApprovalLineBase` (abstract @MappedSuperclass). `public ApprovalStepBase currentStep()`, `public void approve(UUID actorUserId)`, `public void reject(UUID actorUserId, String reason)`, `public void withdraw(UUID actorUserId)`; protected `void initBase(String approvalNo, UUID requesterId, String title)`, `void replaceTitle(String)`, `void linkDocument(String documentType, UUID documentId)`, abstract `List<? extends ApprovalStepBase> stepsView()`; @Getter `getApprovalNo/getRequesterId/getTitle/getDocumentType/getDocumentId/getStatus`.

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.samhanair.logis.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovalLineBaseTest {

    static class FakeStep extends ApprovalStepBase {
        static FakeStep user(UUID approverUserId, int sequence) {
            FakeStep s = new FakeStep();
            s.initUserStep(approverUserId, sequence);
            return s;
        }
    }

    /** steps 컬렉션을 자체 보유하는 테스트 전용 concrete(= concrete @Entity 역할 모사). */
    static class FakeLine extends ApprovalLineBase {
        final List<FakeStep> steps = new ArrayList<>();
        static FakeLine open(String no, UUID requester, String title) {
            FakeLine l = new FakeLine();
            l.initBase(no, requester, title);
            return l;
        }
        FakeStep appendUser(UUID approverUserId) {
            FakeStep s = FakeStep.user(approverUserId, steps.size());
            steps.add(s);
            return s;
        }
        @Override
        protected List<? extends ApprovalStepBase> stepsView() {
            return steps;
        }
    }

    @Test
    void open_은_PENDING_으로_시작하고_currentStep_은_첫_PENDING_이다() {
        UUID requester = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        FakeLine line = FakeLine.open("2026/06/21-1", requester, "지출결의");
        line.appendUser(a1);
        line.appendUser(a2);
        assertThat(line.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(line.currentStep().getApproverUserId()).isEqualTo(a1);
    }

    @Test
    void approve_순차_종합전이_IN_PROGRESS_그리고_APPROVED() {
        UUID requester = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        FakeLine line = FakeLine.open("2026/06/21-1", requester, "지출결의");
        line.appendUser(a1);
        line.appendUser(a2);

        line.approve(a1);
        assertThat(line.getStatus()).isEqualTo(ApprovalStatus.IN_PROGRESS);
        assertThat(line.currentStep().getApproverUserId()).isEqualTo(a2);

        line.approve(a2);
        assertThat(line.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(line.currentStep()).isNull();
    }

    @Test
    void 현재단계_결재자가_아니면_거부한다() {
        UUID requester = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        FakeLine line = FakeLine.open("2026/06/21-1", requester, "지출결의");
        line.appendUser(a1);
        assertThatThrownBy(() -> line.approve(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결재자가 아닙니다");
    }

    @Test
    void reject_은_즉시_REJECTED_이고_종료상태는_재처리_거부() {
        UUID requester = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        FakeLine line = FakeLine.open("2026/06/21-1", requester, "지출결의");
        line.appendUser(a1);
        line.reject(a1, "보완 필요");
        assertThat(line.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThatThrownBy(() -> line.approve(a1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 종료된");
    }

    @Test
    void withdraw_는_요청자_본인만_가능하다() {
        UUID requester = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        FakeLine line = FakeLine.open("2026/06/21-1", requester, "지출결의");
        line.appendUser(a1);
        assertThatThrownBy(() -> line.withdraw(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("요청자 본인만");
        line.withdraw(requester);
        assertThat(line.getStatus()).isEqualTo(ApprovalStatus.WITHDRAWN);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:approval-core:test --tests '*ApprovalLineBaseTest'`
Expected: FAIL (컴파일 실패 — `ApprovalLineBase` 미존재).

- [ ] **Step 3: ApprovalLineBase 구현**

```java
package com.samhanair.logis.approval;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전 전표 공용 결재선 베이스. 스칼라 컬럼 + chain 종합 전이 로직만 보유한다.
 *
 * <p>steps 컬렉션(@OneToMany)·@Version·@Id·서비스 전용 필드(content/template 등)는 소비 서비스
 * concrete @Entity 가 소유한다(Hibernate 가 @MappedSuperclass 의 per-service @OneToMany 를 매핑하지
 * 못하므로). 베이스는 {@link #stepsView()} 추상 accessor 로 단계 목록을 읽어 chain 로직을 수행한다.
 *
 * <p>전표 연계는 loose ref — {@link #documentType}/{@link #documentId}(둘 다 nullable, FK 없음).
 * 전표 비연계 결재(그룹웨어 독립형)는 둘 다 null.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ApprovalLineBase extends BaseEntity {

    private static final int TITLE_MAX_LENGTH = 200;

    /** 결재문서번호 — 전표번호 표준 {@code yyyy/MM/dd-N}. */
    @Column(name = "approval_no", nullable = false, length = 30)
    private String approvalNo;

    /** 요청자 user UUID. */
    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 연계 전표 종류(loose ref, A2+). 독립형 결재는 null. */
    @Column(name = "document_type", length = 40)
    private String documentType;

    /** 연계 전표 UUID(loose ref, A2+). 독립형 결재는 null. */
    @Column(name = "document_id")
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStatus status;

    /** concrete 가 보유한 단계 목록 read-only view. chain 로직이 이를 통해 단계를 읽는다. */
    protected abstract List<? extends ApprovalStepBase> stepsView();

    /** 베이스 스칼라 초기화 — concrete factory 가 호출. status=PENDING. */
    protected void initBase(String approvalNo, UUID requesterId, String title) {
        if (approvalNo == null || approvalNo.isBlank()) {
            throw new IllegalArgumentException("approvalNo 필수");
        }
        if (requesterId == null) {
            throw new IllegalArgumentException("requesterId 필수");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 필수");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("결재 제목은 " + TITLE_MAX_LENGTH + "자 이하여야 합니다");
        }
        this.approvalNo = approvalNo;
        this.requesterId = requesterId;
        this.title = title;
        this.status = ApprovalStatus.PENDING;
    }

    /** 제목 교체(협업 수정완료 overlay 용). concrete 가 guard 후 호출. */
    protected void replaceTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("결재 제목은 필수입니다");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("결재 제목은 " + TITLE_MAX_LENGTH + "자 이하여야 합니다");
        }
        this.title = title;
    }

    /** 전표 연계(loose ref) 지정. */
    protected void linkDocument(String documentType, UUID documentId) {
        this.documentType = documentType;
        this.documentId = documentId;
    }

    /** 현재 처리해야 할 단계(status=PENDING 중 sequence 최소). 종료/미존재 시 null. */
    public ApprovalStepBase currentStep() {
        return stepsView().stream()
                .filter(s -> s.getStatus() == ApprovalStepStatus.PENDING)
                .map(s -> (ApprovalStepBase) s)
                .findFirst()
                .orElse(null);
    }

    /** 결재자 승인. 본인 단계 + 미종료 검증 후 단계 승인 + chain 종합 전이. */
    public void approve(UUID actorUserId) {
        ensureMutable();
        ApprovalStepBase step = requireCurrentStepFor(actorUserId);
        step.approve(actorUserId);
        boolean allApproved = stepsView().stream()
                .allMatch(s -> s.getStatus() == ApprovalStepStatus.APPROVED);
        this.status = allApproved ? ApprovalStatus.APPROVED : ApprovalStatus.IN_PROGRESS;
    }

    /** 결재자 반려 — 즉시 REJECTED. */
    public void reject(UUID actorUserId, String reason) {
        ensureMutable();
        ApprovalStepBase step = requireCurrentStepFor(actorUserId);
        step.reject(actorUserId, reason);
        this.status = ApprovalStatus.REJECTED;
    }

    /** 요청자 본인 회수 — 종료 상태 거부. */
    public void withdraw(UUID actorUserId) {
        if (!this.requesterId.equals(actorUserId)) {
            throw new IllegalStateException("요청자 본인만 회수할 수 있습니다");
        }
        ensureMutable();
        this.status = ApprovalStatus.WITHDRAWN;
    }

    /** 종료 상태(APPROVED/REJECTED/WITHDRAWN) 인지. concrete overlay guard 등에서 재사용. */
    protected boolean isTerminal() {
        return this.status == ApprovalStatus.APPROVED
                || this.status == ApprovalStatus.REJECTED
                || this.status == ApprovalStatus.WITHDRAWN;
    }

    private void ensureMutable() {
        if (isTerminal()) {
            throw new IllegalStateException("이미 종료된 결재선입니다: " + this.status);
        }
    }

    private ApprovalStepBase requireCurrentStepFor(UUID actorUserId) {
        ApprovalStepBase step = currentStep();
        if (step == null) {
            throw new IllegalStateException("처리 대기 중인 결재 단계가 없습니다");
        }
        if (!step.matchesActor(actorUserId)) {
            throw new IllegalStateException("현재 결재 단계의 결재자가 아닙니다");
        }
        return step;
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :shared:approval-core:test --tests '*ApprovalLineBaseTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/approval-core/src
git commit -m "feat(approval-core): ApprovalLineBase chain 엔진(currentStep/approve/reject/withdraw) TDD"
```

---

## Task 4: ApprovalRepositoryPort + 제네릭 ApprovalLineService<L>

재사용 엔진 표면 — 소비 서비스가 주입할 영속성 Port + 그 위의 제네릭 서비스(findById/approve/reject/withdraw + 예외 변환). **A1 은 fake port 로 단위검증**(groupware 는 자기 서비스 유지, A2 slip 이 첫 실소비). YAGNI 주의 — create/list/응답매핑은 서비스마다 다르므로 제네릭에 넣지 않는다.

**Files:**
- Create: `shared/approval-core/src/main/java/com/samhanair/logis/approval/ApprovalRepositoryPort.java`
- Create: `shared/approval-core/src/main/java/com/samhanair/logis/approval/ApprovalLineService.java`
- Test: `shared/approval-core/src/test/java/com/samhanair/logis/approval/ApprovalLineServiceTest.java`

**Interfaces:**
- Consumes: `ApprovalLineBase`(Task 3).
- Produces: `interface ApprovalRepositoryPort<L extends ApprovalLineBase> { Optional<L> findById(UUID); L save(L); Optional<L> findByDocument(String documentType, UUID documentId); }`; `class ApprovalLineService<L extends ApprovalLineBase>` with `L approve(UUID id, UUID actorUserId)`, `L reject(UUID id, UUID actorUserId, String reason)`, `L withdraw(UUID id, UUID actorUserId)`, `L getOrThrow(UUID id)`.

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.samhanair.logis.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovalLineServiceTest {

    static class FakeStep extends ApprovalStepBase {
        static FakeStep user(UUID a, int seq) { FakeStep s = new FakeStep(); s.initUserStep(a, seq); return s; }
    }
    static class FakeLine extends ApprovalLineBase {
        final List<FakeStep> steps = new ArrayList<>();
        UUID id = UUID.randomUUID();
        static FakeLine open(String no, UUID req, String title, UUID... approvers) {
            FakeLine l = new FakeLine();
            l.initBase(no, req, title);
            for (UUID a : approvers) l.steps.add(FakeStep.user(a, l.steps.size()));
            return l;
        }
        @Override protected List<? extends ApprovalStepBase> stepsView() { return steps; }
    }
    /** in-memory fake port. */
    static class FakePort implements ApprovalRepositoryPort<FakeLine> {
        final Map<UUID, FakeLine> store = new HashMap<>();
        @Override public Optional<FakeLine> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public FakeLine save(FakeLine line) { store.put(line.id, line); return line; }
        @Override public Optional<FakeLine> findByDocument(String t, UUID d) {
            return store.values().stream()
                    .filter(l -> t.equals(l.getDocumentType()) && d.equals(l.getDocumentId())).findFirst();
        }
    }

    @Test
    void approve_는_조회후_도메인_승인하고_저장한다() {
        UUID req = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        FakePort port = new FakePort();
        FakeLine line = FakeLine.open("2026/06/21-1", req, "지출", a1);
        port.save(line);
        ApprovalLineService<FakeLine> service = new ApprovalLineService<>(port);

        FakeLine result = service.approve(line.id, a1);
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void getOrThrow_는_미존재시_IllegalArgumentException() {
        ApprovalLineService<FakeLine> service = new ApprovalLineService<>(new FakePort());
        assertThatThrownBy(() -> service.getOrThrow(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결재선을 찾을 수 없습니다");
    }

    @Test
    void findByDocument_는_loose_ref_로_조회한다() {
        UUID req = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        FakePort port = new FakePort();
        FakeLine line = FakeLine.open("2026/06/21-1", req, "출고", a1);
        line.linkDocument("SLIP_OUTBOUND", docId);
        port.save(line);
        ApprovalLineService<FakeLine> service = new ApprovalLineService<>(port);
        assertThat(service.findByDocument("SLIP_OUTBOUND", docId)).isPresent();
    }
}
```

> `linkDocument` 는 protected 라 같은 패키지(test) 의 FakeLine 에서 호출 가능(테스트가 `com.samhanair.logis.approval` 패키지).

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:approval-core:test --tests '*ApprovalLineServiceTest'`
Expected: FAIL (컴파일 — Port/Service 미존재).

- [ ] **Step 3: Port + 제네릭 서비스 구현**

`ApprovalRepositoryPort.java`:

```java
package com.samhanair.logis.approval;

import java.util.Optional;
import java.util.UUID;

/**
 * 결재선 영속성 SPI — 소비 서비스가 자기 repository 로 구현해 주입한다(collab-core Port 패턴).
 *
 * @param <L> 소비 서비스 concrete 결재선 타입
 */
public interface ApprovalRepositoryPort<L extends ApprovalLineBase> {

    Optional<L> findById(UUID approvalId);

    L save(L line);

    /** 전표 연계(loose ref) 조회 — (documentType, documentId) 로 결재선 1건. */
    Optional<L> findByDocument(String documentType, UUID documentId);
}
```

`ApprovalLineService.java`:

```java
package com.samhanair.logis.approval;

import java.util.Optional;
import java.util.UUID;

/**
 * 전 전표 공용 결재 엔진 서비스 — Port 위에서 승인/반려/회수/조회를 수행한다.
 *
 * <p>생성/목록/응답 매핑은 서비스마다 다르므로(채번·사용자검증·DTO) 본 제네릭에 넣지 않는다.
 * 소비 서비스는 자기 @Service 에서 본 클래스를 concrete 타입으로 인스턴스화해 재사용한다
 * (A2 slip-service 가 첫 실소비; groupware 는 자기 ApprovalLineService 유지).
 *
 * @param <L> concrete 결재선 타입
 */
public class ApprovalLineService<L extends ApprovalLineBase> {

    private final ApprovalRepositoryPort<L> repository;

    public ApprovalLineService(ApprovalRepositoryPort<L> repository) {
        this.repository = repository;
    }

    /** 단건 조회. 미존재 시 {@link IllegalArgumentException}(소비 서비스가 도메인 예외로 변환). */
    public L getOrThrow(UUID approvalId) {
        return repository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("결재선을 찾을 수 없습니다: " + approvalId));
    }

    /** 전표 연계(loose ref) 조회. */
    public Optional<L> findByDocument(String documentType, UUID documentId) {
        return repository.findByDocument(documentType, documentId);
    }

    /** 결재자 승인 — 현재 단계 결재자만 허용. */
    public L approve(UUID approvalId, UUID actorUserId) {
        L line = getOrThrow(approvalId);
        line.approve(actorUserId);
        return repository.save(line);
    }

    /** 결재자 반려. */
    public L reject(UUID approvalId, UUID actorUserId, String reason) {
        L line = getOrThrow(approvalId);
        line.reject(actorUserId, reason);
        return repository.save(line);
    }

    /** 요청자 회수. */
    public L withdraw(UUID approvalId, UUID actorUserId) {
        L line = getOrThrow(approvalId);
        line.withdraw(actorUserId);
        return repository.save(line);
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew :shared:approval-core:test`
Expected: PASS (전체 approval-core 테스트 green).

- [ ] **Step 5: Commit**

```bash
git add shared/approval-core/src
git commit -m "feat(approval-core): ApprovalRepositoryPort + 제네릭 ApprovalLineService TDD(fake port)"
```

---

## Task 5: groupware 도메인 이관 + Flyway V8 + BE 회귀

groupware `ApprovalLine`/`ApprovalStep` 을 베이스 상속으로 리팩터링하고, additive nullable 마이그(V8)를 추가하며, 라이브 결재 동작을 **기존 BE 테스트로 무손실 회귀**. DTO 는 entity accessor 갱신하되 JSON 계약 불변.

**Files:**
- Modify: `services/groupware-service/.../domain/ApprovalLine.java`, `domain/ApprovalStep.java`
- Modify: `services/groupware-service/.../service/ApprovalLineService.java`, `dto/ApprovalLineAdminResponse.java`(및 `getApproverId` 참조 파일)
- Create: `services/groupware-service/src/main/resources/db/migration/V8__approval_core_generalization.sql`

**Interfaces:**
- Consumes: `ApprovalLineBase`/`ApprovalStepBase`/`StepType`(Task 1-3).
- Produces: groupware `ApprovalLine extends ApprovalLineBase`(content/template/version/steps/overlay 잔류, `appendStep(UUID)`/`open(...)`/`getStepsView()` 보존), `ApprovalStep extends ApprovalStepBase`(`@ManyToOne ApprovalLine` + `createUser(line, approverUserId, sequence)`).

- [ ] **Step 1: Flyway V8 작성** (additive nullable; step_type 만 결정적 backfill 후 NOT NULL)

```sql
-- V8__approval_core_generalization.sql
-- A1 공통 결재 엔진 일반화 — approval-core 베이스 상속에 필요한 additive 컬럼.
-- 원칙: 기존 행 무손상. 비결정적 결재자그룹/실승인자/서명 컬럼은 NOT NULL 절대 금지.
--       step_type 만 기존 행=USER 로 결정적 backfill 후 NOT NULL.

-- 1) approval_lines — 전표 연계(loose ref). 독립형 결재는 NULL.
ALTER TABLE approval_lines ADD COLUMN document_type VARCHAR(40);
ALTER TABLE approval_lines ADD COLUMN document_id   UUID;

-- 2) approval_steps — step 모델 일반화 컬럼(전부 nullable ADD).
ALTER TABLE approval_steps ADD COLUMN step_type            VARCHAR(20);
ALTER TABLE approval_steps ADD COLUMN approver_group_id    UUID;
ALTER TABLE approval_steps ADD COLUMN required_page_code   VARCHAR(100);
ALTER TABLE approval_steps ADD COLUMN approved_by_user_id  UUID;
ALTER TABLE approval_steps ADD COLUMN signature_png_snapshot BYTEA;
ALTER TABLE approval_steps ADD COLUMN signed_at            TIMESTAMP;

-- step_type 결정적 backfill — 기존 그룹웨어 단계는 전부 USER(특정 사원 직접 지정).
UPDATE approval_steps SET step_type = 'USER' WHERE step_type IS NULL;
ALTER TABLE approval_steps ALTER COLUMN step_type SET NOT NULL;
```

> `signed_at` 은 같은 테이블의 기존 `decided_at`(TIMESTAMP)과 일관되게 TIMESTAMP. KST 전역표준은 postgres GUC(PR #479)로 처리되며, 결재 시각 컬럼의 timestamptz 통일은 기존 decided_at 동반 마이그가 필요해 A1 additive 범위 밖(후속 별도 패스).

- [ ] **Step 2: ApprovalStep concrete 리팩터링**

```java
package com.samhanair.logis.groupware.domain;

import com.samhanair.logis.approval.ApprovalStepBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 그룹웨어 결재 chain 단계(concrete @Entity). 컬럼·전이 로직은 {@link ApprovalStepBase} 가 보유하고,
 * 본 클래스는 @Id 와 부모 {@link ApprovalLine} 으로의 @ManyToOne 역참조만 소유한다.
 */
@Entity
@Getter
@Table(name = "approval_steps")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ApprovalStep extends ApprovalStepBase {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "approval_line_id", nullable = false, updatable = false)
    private ApprovalLine approvalLine;

    private ApprovalStep(ApprovalLine line, UUID approverUserId, int sequence) {
        this.approvalLine = line;
        initUserStep(approverUserId, sequence);
    }

    /** USER 모드 단계 생성 — caller = {@link ApprovalLine#appendStep}. */
    static ApprovalStep createUser(ApprovalLine line, UUID approverUserId, int sequence) {
        return new ApprovalStep(line, approverUserId, sequence);
    }
}
```

- [ ] **Step 3: ApprovalLine concrete 리팩터링** (스칼라 제거, content/template/version/steps/overlay 잔류)

```java
package com.samhanair.logis.groupware.domain;

import com.samhanair.logis.approval.ApprovalLineBase;
import com.samhanair.logis.approval.ApprovalStatus;
import com.samhanair.logis.approval.ApprovalStepStatus;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * 그룹웨어 결재선(concrete @Entity). 스칼라/상태/chain 로직은 {@link ApprovalLineBase} 가 보유하고,
 * 본 클래스는 @Id·@Version·steps 컬렉션·그룹웨어 전용 필드(content/template/overlay)를 소유한다.
 */
@Entity
@Getter
@Table(name = "approval_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ApprovalLine extends ApprovalLineBase {

    private static final Set<ApprovalStatus> COLLAB_LOCKED_STATUSES =
            EnumSet.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED, ApprovalStatus.WITHDRAWN);
    private static final int CONTENT_MAX_LENGTH = 2000;

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "content", length = 2000)
    private String content;

    @Column(name = "template_id")
    private UUID templateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_values", columnDefinition = "jsonb")
    private String fieldValuesJson;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "approvalLine", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<ApprovalStep> steps = new ArrayList<>();

    private ApprovalLine(String approvalNo, UUID requesterId, String title, String content) {
        initBase(approvalNo, requesterId, title);
        validateContentLength(content);
        this.content = content;
        this.version = 0L;
    }

    /** 신규 결재선 발의. status=PENDING, chain 미부여(caller 가 {@link #appendStep} 호출 의무). */
    public static ApprovalLine open(String approvalNo, UUID requesterId, String title, String content) {
        return new ApprovalLine(approvalNo, requesterId, title, content);
    }

    @Override
    protected List<? extends com.samhanair.logis.approval.ApprovalStepBase> stepsView() {
        return this.steps;
    }

    /** 결재 chain 에 USER 단계 추가. sequence 0-base 자동, 요청자 본인 차단. */
    public ApprovalStep appendStep(UUID approverUserId) {
        if (approverUserId == null) {
            throw new IllegalArgumentException("approverId 필수");
        }
        if (approverUserId.equals(getRequesterId())) {
            throw new IllegalArgumentException("요청자 본인은 결재자가 될 수 없습니다");
        }
        ApprovalStep step = ApprovalStep.createUser(this, approverUserId, this.steps.size());
        this.steps.add(step);
        return step;
    }

    /** 결재 chain 의 현재 시점 snapshot — 외부 호출자가 list 조작 불가. */
    public List<ApprovalStep> getStepsView() {
        return Collections.unmodifiableList(this.steps);
    }

    /** 협업 수정완료 가능 상태 검증(종료 상태 409 차단). */
    public void guardCollabModifiable() {
        if (COLLAB_LOCKED_STATUSES.contains(getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "협업 수정완료가 불가능한 상태입니다: " + getStatus());
        }
    }

    /** 협업 수정완료로 결재 제목을 덮어쓴다. */
    public ApprovalLine overlayTitle(String title) {
        guardCollabModifiable();
        try {
            replaceTitle(title);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, ex.getMessage());
        }
        return this;
    }

    /** 협업 수정완료로 결재 본문을 덮어쓴다. */
    public ApprovalLine overlayContent(String content) {
        guardCollabModifiable();
        validateContentLength(content);
        this.content = content;
        return this;
    }

    /** 결재유형 템플릿과 동적 필드 값을 적용한다. */
    public ApprovalLine applyTemplateValues(UUID templateId, String fieldValuesJson) {
        this.templateId = templateId;
        this.fieldValuesJson = fieldValuesJson;
        return this;
    }

    /** 협업 수정완료로 동적 필드 값 JSON 을 갱신한다. */
    public ApprovalLine overlayFieldValues(String fieldValuesJson) {
        guardCollabModifiable();
        this.fieldValuesJson = fieldValuesJson;
        return this;
    }

    private static void validateContentLength(String content) {
        if (content != null && content.length() > CONTENT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "결재 본문은(는) " + CONTENT_MAX_LENGTH + "자 이하여야 합니다");
        }
    }
}
```

> 주의: `getStatus()`/`getTitle()`/`getApprovalNo()`/`getRequesterId()` 는 베이스 @Getter 가 제공(concrete 에서 그대로 호출). `approve/reject/withdraw/currentStep` 도 베이스 상속 — 시그니처 동일(`approve(UUID)` 등)이라 service 호출부 무변경.

- [ ] **Step 4: service/DTO accessor 갱신** (entity 필드명 변경 흡수, JSON 계약 불변)

`ApprovalLineService.resolveDisplayNames` 의 `step.getApproverId()` → `step.getApproverUserId()`. 그 외 groupware 코드에서 `getApproverId()` 호출처를 grep 해 전부 `getApproverUserId()` 로:

```bash
grep -rn "getApproverId()" services/groupware-service/src/main
```

`ApprovalLineAdminResponse.from(...)` 등 DTO 매핑에서 step 의 결재자 UUID 를 읽는 부분을 `getApproverUserId()` 로 교체하되, **JSON 필드명(record component / @JsonProperty)은 `approverId` 그대로 유지**(FE 계약 절연). DTO 가 `step.getApproverId()` 를 호출하고 있었다면 그 호출만 바꾸고 직렬화 키는 불변.

- [ ] **Step 5: groupware 풀 테스트 회귀** (실 Postgres Testcontainers + 단위)

Run: `./gradlew :services:groupware-service:test`
Expected: BUILD SUCCESSFUL — 기존 `ApprovalLineServiceTest`, `GroupwarePermissionControllerIT` 등 전부 PASS. (Windows Testcontainers skip 시 [[feedback_testcontainers_windows_docker]] `DOCKER_HOST=tcp://localhost:2375` 우회; skip 되면 Task 7 probe 가 실 DB 검증 담당.)

- [ ] **Step 6: Commit**

```bash
git add services/groupware-service/src
git commit -F - <<'EOF'
feat(groupware): ApprovalLine/Step 을 approval-core 베이스 상속으로 이관 + V8

base=스칼라+chain 로직 / concrete=@Id·@Version·steps·content/template/overlay 잔류.
V8 additive nullable(document_type/id, step_type backfill USER→NOT NULL, approver_group_id/
required_page_code/approved_by_user_id/signature_png_snapshot/signed_at). DTO JSON 계약 불변.
EOF
```

---

## Task 6: groupware FE 회귀 검증 (vitest + typecheck)

USER 모드 유지 + DTO JSON 계약 불변이므로 FE 소스 변경은 **없어야** 한다. 결재 관련 FE 단위테스트·타입검사가 green 임을 확인해 base 이관이 FE 계약을 깨지 않았음을 박제([[feedback_fe_guard_removal_contract_tests]] 정신 — BE 계약 변경 시 FE 회귀 필수).

**Files:**
- Verify only: `clients/desktop/src/**/groupwareApproval*`, `*ApprovalDoc*`, `approvalDoc.test.ts`

- [ ] **Step 1: 결재 FE 테스트 파일 식별**

```bash
grep -rln "approverId\|ApprovalLine\|결재" clients/desktop/src --include=*.test.ts --include=*.test.tsx
```

- [ ] **Step 2: 타입검사** ([[feedback_desktop_typecheck_command]] — raw tsc 아닌 npm script)

Run: `cd clients/desktop && npm run typecheck`
Expected: 0 errors.

- [ ] **Step 3: 결재 vitest 실행** ([[feedback_playwright_local_version_skew]] — desktop cwd, 로컬 설치본 사용)

Run: `cd clients/desktop && npx vitest run src --reporter=basic` (또는 식별된 결재 테스트 파일만 지정)
Expected: 결재 관련 테스트 PASS (FE 무변경 → green 유지).

- [ ] **Step 4: 회귀 결과 기록 (소스 변경 0 확인)**

```bash
git status --porcelain clients/desktop
```
Expected: 빈 출력(FE 소스 무변경). 변경이 있으면 base 이관이 JSON 계약을 깬 것 → Task 5 DTO 매핑 재점검.

> FE 소스 변경이 없으므로 본 태스크는 commit 없음(검증 게이트). 변경이 불가피했다면 별도 commit.

---

## Task 7: fresh Postgres probe (V8) + standalone 부팅 검증

[[feedback_migration_fresh_postgres_probe]] — Windows Testcontainers skip 이 마이그 오류를 가리므로 **fresh Postgres 에 라이브 유사 픽스처(approver_id 채워진 기존 행)를 seed 한 상태로 V8 직접 적용** 후 NOT NULL 미강제·기존행 무손상을 실증하고, jar standalone 부팅으로 @MappedSuperclass 컬럼↔DDL validate 통과를 확인.

**Files:**
- Verify only (런타임 검증, 산출 파일 없음)

- [ ] **Step 1: probe용 Postgres 컨테이너 기동**

```bash
docker run -d --name approval-probe -e POSTGRES_DB=groupware_db -e POSTGRES_USER=samhan -e POSTGRES_PASSWORD=samhan -p 55432:5432 postgres:16
```

- [ ] **Step 2: V1~V7 적용 + 라이브 유사 픽스처 seed**

groupware 의 V1~V7 을 순서대로 적용(Flyway 또는 `cat`):

```bash
for f in $(ls services/groupware-service/src/main/resources/db/migration/V[1-7]*.sql | sort -V); do
  echo "applying $f"; cat "$f" | docker exec -i approval-probe psql -v ON_ERROR_STOP=1 -U samhan -d groupware_db
done
```

기존 결재선/단계 1건 seed(step_type 없는 USER 행):

```bash
docker exec -i approval-probe psql -v ON_ERROR_STOP=1 -U samhan -d groupware_db <<'SQL'
INSERT INTO approval_lines (id, approval_no, requester_id, title, status, version, created_at, created_by, is_deleted)
VALUES ('11111111-1111-1111-1111-111111111111','2026/06/21-1','22222222-2222-2222-2222-222222222222','레거시 결재','IN_PROGRESS',0, now(),'seed', FALSE);
INSERT INTO approval_steps (id, approval_line_id, approver_id, sequence, status, created_at, created_by, is_deleted)
VALUES ('33333333-3333-3333-3333-333333333333','11111111-1111-1111-1111-111111111111','44444444-4444-4444-4444-444444444444',0,'PENDING', now(),'seed', FALSE);
SQL
```

> approval_no/version 은 V4 추가분이라 V1~V7 적용 후 컬럼 존재. 시드 컬럼이 실제 스키마와 어긋나면 V4 의 NOT NULL 백필 컬럼을 반영해 조정.

- [ ] **Step 3: V8 적용 + 무손상 검증**

```bash
cat services/groupware-service/src/main/resources/db/migration/V8__approval_core_generalization.sql \
  | docker exec -i approval-probe psql -v ON_ERROR_STOP=1 -U samhan -d groupware_db

docker exec -i approval-probe psql -U samhan -d groupware_db -c \
  "SELECT step_type, approver_id, approver_group_id, approved_by_user_id, signed_at FROM approval_steps;"
```
Expected: ON_ERROR_STOP 통과(syntax/제약 위반 0). 기존 행 = `step_type='USER'`, `approver_id` 보존, `approver_group_id/approved_by_user_id/signed_at` = NULL. `ALTER ... SET NOT NULL` 성공(기존행 backfill 됨).

- [ ] **Step 4: standalone 부팅 validate** (선택, Docker 스택 가용 시)

groupware jar 를 probe DB(55432) 가리켜 기동, Hibernate 가 @MappedSuperclass 컬럼↔실 DDL 을 validate 통과하는지 확인([[feedback_standalone_boot_real_qa]]). 기동 로그에 매핑/검증 에러 0.

```bash
./gradlew :services:groupware-service:bootJar
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/groupware_db \
SPRING_DATASOURCE_USERNAME=samhan SPRING_DATASOURCE_PASSWORD=samhan \
java -jar services/groupware-service/build/libs/groupware-service.jar --spring.profiles.active=local
```
Expected: ApplicationContext 기동 성공(결재 entity 매핑 검증 통과). 확인 후 종료.

- [ ] **Step 5: probe 정리**

```bash
docker rm -f approval-probe
```

- [ ] **Step 6: 최종 검증 + 메모리/핸드오프 기록은 머지 후**

Run: `./gradlew :shared:approval-core:test :services:groupware-service:test`
Expected: 양 모듈 BUILD SUCCESSFUL.

> 본 태스크는 검증 게이트(산출 없음). 결과는 PR 본문 + 라이브 캡처로 보고. 메모리/핸드오프 갱신은 머지 후 PM 이 수행.

---

## Self-Review

**1. Spec coverage** (spec 2026-06-21-approval-engine-a1-design.md 대조):
- §2 결정1 분산(approval-core 추출) → Task 1-4. ✓
- 결정2 groupware 즉시 이관(additive, BE+FE 회귀) → Task 5(BE)+Task 6(FE)+Task 7(probe). ✓
- 결정3 loose ref(document_type/id) → ApprovalLineBase(Task 3) + V8(Task 5). ✓
- 결정4 서명 컬럼 자리 nullable LAZY → ApprovalStepBase signaturePngSnapshot/signedAt(Task 2) + V8. ✓
- 결정6 E8 page-code → StepType.GROUP + requiredPageCode 컬럼 선반영(Task 2). enforce 는 A2(범위 외, 명시). ✓
- 결정7 A1 축소(slip 제외) → slip 태스크 없음. ✓
- §3 base/concrete 분리(steps·@Version·전용필드 concrete) → Task 3/5 명시. ✓
- §4 stepType union, 전 컬럼 nullable, NOT NULL 금지 → Task 2 + V8(step_type 만 결정적 NOT NULL). ✓
- §5 Flyway additive·fresh probe → Task 5 V8 + Task 7. ✓
- §7 BE+FE 회귀 → Task 5/6. ✓
- §9 후속(알림/E11/E2·E5/CREATOR enforce)은 A1 비포함 — 태스크 없음 정당(spec 이 후속으로 명시). ✓
- **갭 점검**: 제네릭 ApprovalLineService/Port 는 spec §8 "approval-core(base/service/port/autoconfig)" 충족(Task 4). autoconfig=anchor(Task 1). groupware 가 제네릭 서비스를 실소비하지 않는 점은 YAGNI 로 Task 4 노트에 명시(A2 slip 첫 소비) — 의도된 설계.

**2. Placeholder scan**: "TBD/TODO/적절히 처리" 없음. 모든 코드 스텝에 실 코드. grep 대체 스텝(Task1-S5, Task5-S4)은 기계적 import/accessor 교체라 대상 파일이 환경별이라 grep 으로 지정(플레이스홀더 아님 — 명령+교체 규칙 명시). ✓

**3. Type consistency**:
- `approve(UUID)`·`reject(UUID,String)`·`withdraw(UUID)`·`currentStep()` — Task 3 정의, Task 4 서비스·Task 5 concrete 상속 동일 시그니처. ✓
- `getApproverUserId()`(Task 2) ↔ Task 5-S4 DTO/service 교체 대상 일치. ✓
- `ApprovalStep.createUser(line, approverUserId, sequence)`(Task 5-S2) ↔ `ApprovalLine.appendStep`(Task 5-S3) 호출 일치. ✓
- `stepsView()` 반환 `List<? extends ApprovalStepBase>` — Task 3 abstract ↔ Task 5 concrete override 일치. ✓
- `ApprovalRepositoryPort<L>` findById/save/findByDocument(Task 4) ↔ 서비스 사용 일치. ✓
- `linkDocument(String,UUID)` protected(Task 3) ↔ Task 4 테스트 호출(같은 패키지) 가능. ✓

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-06-21-approval-engine-a1.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — 태스크별 fresh subagent 디스패치, 태스크 간 리뷰, 빠른 반복. 단 코드 구현은 [[feedback_codex_implements_claude_reviews]] 에 따라 **Codex 디스패치**(Claude 는 컴파일·테스트·커밋 대행 + 듀얼리뷰).

**2. Inline Execution** — 이 세션에서 executing-plans 로 배치 실행 + 체크포인트.

**어느 방식으로 진행할까요?**
