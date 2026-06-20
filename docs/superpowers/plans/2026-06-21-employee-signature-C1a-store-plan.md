> **슬라이스 플랜** — 에픽 인덱스: [2026-06-21-employee-signature-stamp-plan.md](2026-06-21-employee-signature-stamp-plan.md) (Global Constraints · 공유 계약 · 실행 방식 · 구현시점 확인항목). 본 파일 = 단일 슬라이스 = 1 PR. Step 은 `- [ ]` 체크박스로 추적.

## Slice C1a: 서명 저장소 · 인증 경로 (user-service BE)

**PR boundary:** 이 슬라이스 = 1개 PR (`[FEAT] C1a 사원 서명 저장소·인증 경로 (user-service)`). user-service 단독 변경(Employee 4컬럼 + Flyway V10 + 도메인 메서드 + 감사 엔티티 + AdminUserController 2 엔드포인트 + InternalUserController 배치 엔드포인트 + DTO + Testcontainers IT + fresh Postgres probe). slip-service/desktop 변경 없음(C3에서 소비). 배포 순서상 user-service(C1) 먼저 → C3 미배선 구간 graceful fallback.

> **검증된 실 패턴 (정찰 완료)**:
> - `AdminUserController` 모든 메서드는 `@RequireDepartment(Department.EXECUTIVE_OFFICE)` + `@RequirePermission(page="admin.users", action=...)` **둘 다** 보유 (`AdminUserController.java:95-96,160-161,205-206`). 신규 서명 엔드포인트도 **반드시 둘 다** 붙인다 (이게 없으면 `UserPermissionControllerIT.assertDepartmentGate` 와 운영 게이트 불일치).
> - `disable` 엔드포인트가 `@RequirePermission(page="admin.users", action=PermissionAction.DELETE)` 사용 → seed 상 `admin.users` DELETE 는 MASTER seed 만 통과. 무효화 DELETE 도 동일 게이트로 "MASTER 한정" 충족 (신규 page-code/시드 0). ([feedback_pm_permission_autonomy], [feedback_role_naming_full])
> - `Employee.id` = assigned UUID (not generated), `auth-service.accounts.id` 와 1:1 = `Slip.createdBy`/`dispatcherUserId`/`inspectorUserId` 의 join key (`Employee.java:22-23,58-60`).
> - 감사 엔티티는 `@GeneratedValue @UuidGenerator` (RoleChangeHistory/SlipSignatureAudit 패턴, `RoleChangeHistory.java:35-37`).
> - 배치 internal 엔드포인트는 `@PreAuthorize("hasRole('MASTER')")` + X-Internal-Token (`InternalUserController.java:64,90,200`), `employeeRepository.findAllByIdIn(distinct)` 미러 (`InternalUserController.java:184,213`).
> - IT 베이스 = `AbstractPostgresIT` (싱글턴 컨테이너, Docker 미가용 skip), `@SpringBootTest(classes=UserServiceApplication.class)` + `@AutoConfigureMockMvc`, `@MockBean DynamicPermissionClient`/`AuthClient` (`InternalUserSearchControllerIT.java:30-42`).
> - ApiResponse 래퍼 = `$.data` 경로 (`ApiResponse.java:19`). ErrorCode 422 = `UNPROCESSABLE_ENTITY`, 400 = `INVALID_INPUT`, 409 = `CONFLICT` (`ErrorCode.java:11-16`).
> - SHA-256 hex + 50KB 가드 + base64 디코드 헬퍼 = `SlipSignatureService.java:435-460` 1:1 미러.

---

### Task C1a.1: SignatureChannel enum + Employee 4 서명 필드 + 도메인 메서드

**Files:**
- create `services/user-service/src/main/java/com/samhanair/logis/user/domain/SignatureChannel.java`
- modify `services/user-service/src/main/java/com/samhanair/logis/user/domain/Employee.java` (검증: 존재)
- create `services/user-service/src/test/java/com/samhanair/logis/user/domain/EmployeeSignatureTest.java`

**Interfaces:**
- Produces: `enum SignatureChannel { MOBILE_CANVAS, UPLOAD }` (slip 의 `PAPER_SCAN` 과 도메인 다름 — 혼용 금지)
- Produces: `Employee.registerSignature(byte[] png, String hash, SignatureChannel channel)` — 4필드 원자 set, 재등록=교체
- Produces: `Employee.invalidateSignature(String reason)` — 4필드 NULL, `signedAt==null` 이면 `BusinessException(CONFLICT)`
- Produces (getter): `Employee.getSignaturePng()` / `getSignatureHash()` / `getSignedAt()` / `getSignatureChannel()` (`@Getter` 자동)
- Consumes: `BaseEntity` 7 audit, `BusinessException`/`ErrorCode.CONFLICT`

- [ ] **Step 1: 실패 테스트 작성 — 도메인 메서드 단위 테스트.** 순수 JUnit (Spring 불필요), `Employee.create(...)` 로 fixture 생성 후 `registerSignature`/`invalidateSignature` 동작 검증.

  `services/user-service/src/test/java/com/samhanair/logis/user/domain/EmployeeSignatureTest.java`:
  ```java
  package com.samhanair.logis.user.domain;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;

  import com.samhanair.logis.common.exception.BusinessException;
  import com.samhanair.logis.common.exception.ErrorCode;
  import com.samhanair.logis.common.security.Role;
  import java.time.LocalDate;
  import java.util.UUID;
  import org.junit.jupiter.api.Test;

  /** Employee 서명 도메인 메서드 단위 테스트 — C1a. */
  class EmployeeSignatureTest {

      private Employee newEmployee() {
          Department department = Department.create("SIG_TEST", "서명테스트팀", 950);
          return Employee.create(UUID.randomUUID(), "sig01", "서명자", "사원",
                  Role.STAFF, department, false, LocalDate.of(2026, 1, 1), null, null);
      }

      @Test
      void registerSignature_4필드를_원자적으로_set한다() {
          Employee e = newEmployee();
          byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};

          e.registerSignature(png, "a".repeat(64), SignatureChannel.UPLOAD);

          assertThat(e.getSignaturePng()).isEqualTo(png);
          assertThat(e.getSignatureHash()).isEqualTo("a".repeat(64));
          assertThat(e.getSignatureChannel()).isEqualTo(SignatureChannel.UPLOAD);
          assertThat(e.getSignedAt()).isNotNull();
      }

      @Test
      void registerSignature_재등록은_기존_서명을_교체한다() {
          Employee e = newEmployee();
          e.registerSignature(new byte[] {1}, "a".repeat(64), SignatureChannel.UPLOAD);

          e.registerSignature(new byte[] {2, 3}, "b".repeat(64), SignatureChannel.MOBILE_CANVAS);

          assertThat(e.getSignaturePng()).containsExactly(2, 3);
          assertThat(e.getSignatureHash()).isEqualTo("b".repeat(64));
          assertThat(e.getSignatureChannel()).isEqualTo(SignatureChannel.MOBILE_CANVAS);
      }

      @Test
      void registerSignature_png_null이면_IllegalArgument() {
          Employee e = newEmployee();
          assertThatThrownBy(() -> e.registerSignature(null, "a".repeat(64), SignatureChannel.UPLOAD))
                  .isInstanceOf(IllegalArgumentException.class);
      }

      @Test
      void invalidateSignature_4필드를_NULL로_만든다() {
          Employee e = newEmployee();
          e.registerSignature(new byte[] {1}, "a".repeat(64), SignatureChannel.UPLOAD);

          e.invalidateSignature("오등록 정정");

          assertThat(e.getSignaturePng()).isNull();
          assertThat(e.getSignatureHash()).isNull();
          assertThat(e.getSignatureChannel()).isNull();
          assertThat(e.getSignedAt()).isNull();
      }

      @Test
      void invalidateSignature_미등록_상태면_CONFLICT() {
          Employee e = newEmployee();
          assertThatThrownBy(() -> e.invalidateSignature("사유"))
                  .isInstanceOf(BusinessException.class)
                  .extracting("errorCode")
                  .isEqualTo(ErrorCode.CONFLICT);
      }
  }
  ```
  > `BusinessException` 의 errorCode getter 명은 실제 클래스 확인 후 `extracting("errorCode")` 의 property 명 정렬(BusinessException 이 `getErrorCode()` 보유 시 그대로, 다르면 `.extracting(ex -> ((BusinessException) ex).getErrorCode())` 로 교체).

- [ ] **Step 2: 실행 → FAIL.** `SignatureChannel`/메서드 미존재로 컴파일 실패.
  ```
  ./gradlew :services:user-service:test --tests "com.samhanair.logis.user.domain.EmployeeSignatureTest"
  ```
  기대: COMPILE FAIL (`cannot find symbol: SignatureChannel`, `method registerSignature`).

- [ ] **Step 3: SignatureChannel enum 생성.**
  ```java
  package com.samhanair.logis.user.domain;

  import lombok.Getter;
  import lombok.RequiredArgsConstructor;

  /**
   * 사원 서명 입력 채널 — C1a (사원 서명 등록 에픽 §4.1).
   *
   * <ul>
   *   <li>{@link #MOBILE_CANVAS} — 모바일 핸드오프로 손그림 서명 (공개 웹앱 제출)</li>
   *   <li>{@link #UPLOAD} — 관리자 desktop 에서 이미지 업로드</li>
   * </ul>
   *
   * <p>VARCHAR(20) 컬럼 매핑 + DB CHECK 제약 IN 목록과 정확히 일치해야 한다
   * (CHECK(signature_channel IN ('MOBILE_CANVAS','UPLOAD'))). slip-service 의
   * 동명 enum(PAPER_SCAN 포함)과 도메인이 다르므로 혼용 금지.
   */
  @Getter
  @RequiredArgsConstructor
  public enum SignatureChannel {
      MOBILE_CANVAS("모바일 캔버스"),
      UPLOAD("이미지 업로드");

      private final String displayName;
  }
  ```

- [ ] **Step 4: Employee 에 4 필드 + 도메인 메서드 추가.** `Employee.java` 의 `ecountCode` 필드 다음(`:98` 이후)에 필드, `terminate(...)` 메서드(`:154-156`) 다음에 도메인 메서드 추가.

  import 구역에 추가:
  ```java
  import com.samhanair.logis.common.exception.BusinessException;
  import com.samhanair.logis.common.exception.ErrorCode;
  import java.time.LocalDateTime;
  ```
  필드 추가 (`ecountCode` 필드 다음):
  ```java
      // ----- 서명(인감) 필드 — C1a. 전부 nullable (미등록 = NULL). -----

      /** 서명 PNG 원본 bytes. 서비스 레이어 ≤50KB 가드(PNG_MAX_BYTES). bytea 매핑 명시. */
      @Column(name = "signature_png")
      private byte[] signaturePng;

      /** 서명 SHA-256 hex 64자 — 클라 계산·전송, 서버 재검증. */
      @Column(name = "signature_hash", length = 64)
      private String signatureHash;

      /** 최종 등록(관리) 시각. 결재란에는 표시 안 함(인감 모델). */
      @Column(name = "signed_at")
      private LocalDateTime signedAt;

      /** 서명 입력 채널 — MOBILE_CANVAS / UPLOAD. */
      @Enumerated(EnumType.STRING)
      @Column(name = "signature_channel", length = 20)
      private SignatureChannel signatureChannel;
  ```
  도메인 메서드 추가 (`terminate(...)` 다음, 클래스 닫기 전):
  ```java
      /**
       * 서명(인감) 등록 — 4필드 원자 set. 재등록 시 기존 서명을 교체한다.
       *
       * <p>직접 set 금지 컨벤션 준수 — 본 메서드만이 서명 4필드를 갱신한다. PNG 크기 가드/해시
       * 재검증/PNG magic-byte 검증은 서비스 레이어 책임(도메인은 순수 mutation).
       *
       * @param png 서명 PNG bytes (필수, 비어있으면 IllegalArgument)
       * @param hash SHA-256 hex 64자 (필수)
       * @param channel 입력 채널 (필수)
       * @throws IllegalArgumentException png/hash/channel null 또는 png 비어있음
       */
      public void registerSignature(byte[] png, String hash, SignatureChannel channel) {
          if (png == null || png.length == 0) {
              throw new IllegalArgumentException("signaturePng 은 필수입니다");
          }
          if (hash == null || hash.isBlank()) {
              throw new IllegalArgumentException("signatureHash 는 필수입니다");
          }
          if (channel == null) {
              throw new IllegalArgumentException("signatureChannel 은 필수입니다");
          }
          this.signaturePng = png;
          this.signatureHash = hash;
          this.signatureChannel = channel;
          this.signedAt = LocalDateTime.now();
      }

      /**
       * 서명(인감) 무효화 — 서명 4필드 NULL. 미등록 상태에서 호출 시 CONFLICT(409).
       *
       * <p>audit INVALIDATE 행 적재는 서비스 레이어 책임. 직전 hash/channel snapshot 은 본 메서드
       * 호출 <strong>전</strong> 서비스에서 확보해야 한다(호출 후 NULL).
       *
       * @param reason 무효화 사유 (필수, ≤500자)
       * @throws BusinessException(CONFLICT) signedAt 가 null(미등록) 일 때
       * @throws IllegalArgumentException reason null/blank 또는 500자 초과
       */
      public void invalidateSignature(String reason) {
          if (this.signedAt == null) {
              throw new BusinessException(ErrorCode.CONFLICT,
                      "등록된 서명이 없어 무효화할 수 없습니다");
          }
          if (reason == null || reason.isBlank()) {
              throw new IllegalArgumentException("reason 은 필수입니다");
          }
          if (reason.length() > 500) {
              throw new IllegalArgumentException("reason 은 최대 500자입니다");
          }
          this.signaturePng = null;
          this.signatureHash = null;
          this.signatureChannel = null;
          this.signedAt = null;
      }
  ```

- [ ] **Step 5: 실행 → PASS.**
  ```
  ./gradlew :services:user-service:test --tests "com.samhanair.logis.user.domain.EmployeeSignatureTest"
  ```
  기대: BUILD SUCCESSFUL, 6 tests passed.

- [ ] **Step 6: commit.**
  ```
  git add services/user-service/src/main/java/com/samhanair/logis/user/domain/SignatureChannel.java \
          services/user-service/src/main/java/com/samhanair/logis/user/domain/Employee.java \
          services/user-service/src/test/java/com/samhanair/logis/user/domain/EmployeeSignatureTest.java
  git commit -F <커밋메시지파일>
  ```
  커밋 메시지(Write→`git commit -F`, [feedback_bash_commit_message_file]):
  ```
  feat(user): Employee 서명 4필드 + register/invalidate 도메인 메서드 (C1a)

  - SignatureChannel enum {MOBILE_CANVAS, UPLOAD} 신규 (slip PAPER_SCAN 과 도메인 분리)
  - Employee signaturePng/signatureHash/signedAt/signatureChannel 4필드 (전부 nullable)
  - registerSignature(원자 set, 재등록=교체) / invalidateSignature(미등록 시 409)
  - 직접 set 금지 컨벤션 준수, 단위 테스트 6건

  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  ```

---

### Task C1a.2: EmployeeSignatureAudit 엔티티 + Repository

**Files:**
- create `services/user-service/src/main/java/com/samhanair/logis/user/domain/SignatureAuditAction.java`
- create `services/user-service/src/main/java/com/samhanair/logis/user/domain/EmployeeSignatureAudit.java`
- create `services/user-service/src/main/java/com/samhanair/logis/user/repository/EmployeeSignatureAuditRepository.java`
- create `services/user-service/src/test/java/com/samhanair/logis/user/domain/EmployeeSignatureAuditTest.java`

**Interfaces:**
- Produces: `enum SignatureAuditAction { RECORD, INVALIDATE }` (CHECK(action IN ('RECORD','INVALIDATE')))
- Produces: `EmployeeSignatureAudit.record(UUID employeeId, String signatureHash, SignatureChannel channel, String actorUserId)` → RECORD 행
- Produces: `EmployeeSignatureAudit.invalidate(UUID employeeId, String signatureHash, SignatureChannel channel, String reason, String actorUserId)` → INVALIDATE 행
- Produces: `EmployeeSignatureAuditRepository extends JpaRepository<EmployeeSignatureAudit, UUID>` + `findAllByEmployeeIdOrderByCreatedAtDesc(UUID)`
- Consumes: `BaseEntity` 7 audit, `SignatureChannel`(C1a.1)

- [ ] **Step 1: 실패 테스트 작성 — 정적 factory 동작.**
  `services/user-service/src/test/java/com/samhanair/logis/user/domain/EmployeeSignatureAuditTest.java`:
  ```java
  package com.samhanair.logis.user.domain;

  import static org.assertj.core.api.Assertions.assertThat;

  import java.util.UUID;
  import org.junit.jupiter.api.Test;

  /** EmployeeSignatureAudit 정적 factory 단위 테스트 — C1a. */
  class EmployeeSignatureAuditTest {

      @Test
      void record_factory는_RECORD_action과_핵심필드를_채운다() {
          UUID emp = UUID.randomUUID();
          EmployeeSignatureAudit audit = EmployeeSignatureAudit.record(
                  emp, "a".repeat(64), SignatureChannel.UPLOAD, "actor-1");

          assertThat(audit.getEmployeeId()).isEqualTo(emp);
          assertThat(audit.getAction()).isEqualTo(SignatureAuditAction.RECORD);
          assertThat(audit.getSignatureHash()).isEqualTo("a".repeat(64));
          assertThat(audit.getSignatureChannel()).isEqualTo(SignatureChannel.UPLOAD);
          assertThat(audit.getActorUserId()).isEqualTo("actor-1");
          assertThat(audit.getReason()).isNull();
      }

      @Test
      void invalidate_factory는_INVALIDATE_action과_reason을_채운다() {
          UUID emp = UUID.randomUUID();
          EmployeeSignatureAudit audit = EmployeeSignatureAudit.invalidate(
                  emp, "b".repeat(64), SignatureChannel.MOBILE_CANVAS, "오등록 정정", "master-9");

          assertThat(audit.getAction()).isEqualTo(SignatureAuditAction.INVALIDATE);
          assertThat(audit.getReason()).isEqualTo("오등록 정정");
          assertThat(audit.getActorUserId()).isEqualTo("master-9");
      }
  }
  ```

- [ ] **Step 2: 실행 → FAIL.**
  ```
  ./gradlew :services:user-service:test --tests "com.samhanair.logis.user.domain.EmployeeSignatureAuditTest"
  ```
  기대: COMPILE FAIL (`cannot find symbol: EmployeeSignatureAudit`).

- [ ] **Step 3: SignatureAuditAction enum 생성.**
  ```java
  package com.samhanair.logis.user.domain;

  /**
   * EmployeeSignatureAudit 의 action 분류 — C1a (slip slip_signature_audit 미러).
   *
   * <ul>
   *   <li>{@link #RECORD} — 서명 신규 등록(업로드/모바일). 재등록도 새 RECORD 1건.</li>
   *   <li>{@link #INVALIDATE} — 관리자 무효화(MASTER).</li>
   * </ul>
   *
   * <p>VARCHAR(20) + DB CHECK(action IN ('RECORD','INVALIDATE')) 와 정확히 일치.
   */
  public enum SignatureAuditAction {
      RECORD,
      INVALIDATE
  }
  ```

- [ ] **Step 4: EmployeeSignatureAudit 엔티티 생성.** (`SlipSignatureAudit.java` + `RoleChangeHistory.java` 패턴, `@GeneratedValue @UuidGenerator`)
  ```java
  package com.samhanair.logis.user.domain;

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
   * 사원 서명 등록/무효화 감사 이력 — C1a (slip slip_signature_audit 미러).
   *
   * <p>전자서명 무결성 입증을 위해 별도 테이블로 분리. RECORD/INVALIDATE 2종 action 만 적재.
   * Slip 패턴과 동일하게 entity 는 정적 factory 만 제공하며, 도메인 mutation 직후 서비스 레이어가
   * repository 로 영속한다(직접 INSERT).
   */
  @Entity
  @Getter
  @Table(name = "employee_signature_audit")
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @SQLRestriction("is_deleted = false")
  public class EmployeeSignatureAudit extends BaseEntity {

      @Id
      @GeneratedValue
      @UuidGenerator
      @Column(name = "id", updatable = false, nullable = false)
      private UUID id;

      @Column(name = "employee_id", nullable = false)
      private UUID employeeId;

      @Enumerated(EnumType.STRING)
      @Column(name = "action", nullable = false, length = 20)
      private SignatureAuditAction action;

      @Column(name = "signature_hash", length = 64)
      private String signatureHash;

      @Enumerated(EnumType.STRING)
      @Column(name = "signature_channel", length = 20)
      private SignatureChannel signatureChannel;

      @Column(name = "reason", length = 500)
      private String reason;

      /** 처리자 user-id — 모바일 RECORD 시 NULL 가능(인증 없는 공개 경로), 관리자 작업 시 X-User-Id. */
      @Column(name = "actor_user_id", length = 50)
      private String actorUserId;

      private EmployeeSignatureAudit(UUID employeeId, SignatureAuditAction action, String signatureHash,
                                     SignatureChannel signatureChannel, String reason, String actorUserId) {
          this.employeeId = employeeId;
          this.action = action;
          this.signatureHash = signatureHash;
          this.signatureChannel = signatureChannel;
          this.reason = reason;
          this.actorUserId = actorUserId;
      }

      /**
       * RECORD 이력 생성 — 서명 신규/재등록 시 적재.
       *
       * @param employeeId 대상 사원 UUID (필수)
       * @param signatureHash SHA-256 hex 64자 (필수)
       * @param channel 입력 채널 (필수)
       * @param actorUserId 처리자 user-id (모바일 공개 경로는 NULL 가능)
       */
      public static EmployeeSignatureAudit record(UUID employeeId, String signatureHash,
                                                  SignatureChannel channel, String actorUserId) {
          return new EmployeeSignatureAudit(employeeId, SignatureAuditAction.RECORD,
                  signatureHash, channel, null, actorUserId);
      }

      /**
       * INVALIDATE 이력 생성 — 관리자(MASTER) 무효화 시 적재.
       *
       * @param employeeId 대상 사원 UUID (필수)
       * @param signatureHash 직전 SHA-256 hex 64자 snapshot
       * @param channel 직전 채널 snapshot
       * @param reason 무효화 사유 (필수, ≤500자)
       * @param actorUserId 처리자 user-id (필수)
       */
      public static EmployeeSignatureAudit invalidate(UUID employeeId, String signatureHash,
                                                      SignatureChannel channel, String reason,
                                                      String actorUserId) {
          return new EmployeeSignatureAudit(employeeId, SignatureAuditAction.INVALIDATE,
                  signatureHash, channel, reason, actorUserId);
      }
  }
  ```

- [ ] **Step 5: Repository 생성.** (`SlipSignatureAuditRepository.java` 미러)
  ```java
  package com.samhanair.logis.user.repository;

  import com.samhanair.logis.user.domain.EmployeeSignatureAudit;
  import java.util.List;
  import java.util.UUID;
  import org.springframework.data.jpa.repository.JpaRepository;

  /**
   * 사원 서명 감사 이력 저장소 — C1a.
   * 단일 employeeId 별 이력 조회 + INSERT 만 지원(UPDATE/DELETE 금지 — 감사 무결성).
   */
  public interface EmployeeSignatureAuditRepository
          extends JpaRepository<EmployeeSignatureAudit, UUID> {

      /** 사원별 전체 감사 이력 (created_at DESC). */
      List<EmployeeSignatureAudit> findAllByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
  }
  ```

- [ ] **Step 6: 실행 → PASS.**
  ```
  ./gradlew :services:user-service:test --tests "com.samhanair.logis.user.domain.EmployeeSignatureAuditTest"
  ```
  기대: BUILD SUCCESSFUL, 2 tests passed.

- [ ] **Step 7: commit.**
  ```
  git add services/user-service/src/main/java/com/samhanair/logis/user/domain/SignatureAuditAction.java \
          services/user-service/src/main/java/com/samhanair/logis/user/domain/EmployeeSignatureAudit.java \
          services/user-service/src/main/java/com/samhanair/logis/user/repository/EmployeeSignatureAuditRepository.java \
          services/user-service/src/test/java/com/samhanair/logis/user/domain/EmployeeSignatureAuditTest.java
  git commit -F <커밋메시지파일>
  ```
  메시지: `feat(user): 사원 서명 감사 엔티티 EmployeeSignatureAudit + Repository (C1a)`

---

### Task C1a.3: Flyway V10 — 4컬럼 ADD + CHECK + 감사 테이블 + fresh Postgres probe

**Files:**
- create `services/user-service/src/main/resources/db/migration/V10__add_employee_signature.sql`

**Interfaces:**
- Produces: `employees` 4 신규 컬럼(`signature_png BYTEA`, `signature_hash VARCHAR(64)`, `signed_at TIMESTAMP`, `signature_channel VARCHAR(20)`) + `CHECK(signature_channel IN ('MOBILE_CANVAS','UPLOAD'))`
- Produces: `employee_signature_audit` 테이블 (id PK + employee_id + action CHECK + signature_hash + signature_channel + reason + actor_user_id + BaseEntity 7)
- Consumes: 기존 `employees` 테이블(V1/V8)

> ⚠️ 핸드오프 토큰 테이블(`employee_signature_handoff_token`)은 **C1b 소관** — 본 V10 에 넣지 않는다(슬라이스 경계 = 보안 표면 분리). C1b 가 V11 로 추가.

- [ ] **Step 1: V10 마이그레이션 작성.** (V3/V8 BaseEntity 컬럼 컨벤션 1:1, `@Enumerated(STRING)` 매핑이므로 enum→VARCHAR + CHECK)
  ```sql
  -- V10__add_employee_signature.sql
  -- C1a — 사원 서명(인감) 저장소: employees 4컬럼 + 감사 테이블.
  --
  -- slip-service V5__add_slip_signature.sql 패턴 미러. 단 user 도메인 채널은
  -- {MOBILE_CANVAS, UPLOAD} 2종 (slip 의 PAPER_SCAN 미사용). 핸드오프 토큰 테이블은 C1b(V11).
  --
  -- 컬럼 타입 컨벤션 (V1/V8 계승):
  --   * 모든 신규 컬럼 nullable (미등록 = NULL)
  --   * BYTEA: PNG ≤50KB (서비스 레이어 가드)
  --   * SHA-256 hex 64자 → VARCHAR(64)
  --   * enum → VARCHAR(20) + CHECK (도메인 enum / FE 타입 3곳 정확 일치)

  ----------------------------------------------------------------------
  -- 1) employees — 서명 4컬럼 추가 + 채널 CHECK 제약
  ----------------------------------------------------------------------
  ALTER TABLE employees ADD COLUMN IF NOT EXISTS signature_png       BYTEA;
  ALTER TABLE employees ADD COLUMN IF NOT EXISTS signature_hash      VARCHAR(64);
  ALTER TABLE employees ADD COLUMN IF NOT EXISTS signed_at           TIMESTAMP;
  ALTER TABLE employees ADD COLUMN IF NOT EXISTS signature_channel   VARCHAR(20);

  ALTER TABLE employees
      ADD CONSTRAINT ck_employees_signature_channel
      CHECK (signature_channel IS NULL
             OR signature_channel IN ('MOBILE_CANVAS', 'UPLOAD'));

  ----------------------------------------------------------------------
  -- 2) employee_signature_audit — 등록/무효화 이력 (slip_signature_audit 미러)
  ----------------------------------------------------------------------
  CREATE TABLE employee_signature_audit (
      id                 UUID         PRIMARY KEY,
      employee_id        UUID         NOT NULL,
      action             VARCHAR(20)  NOT NULL,
      signature_hash     VARCHAR(64),
      signature_channel  VARCHAR(20),
      reason             VARCHAR(500),
      actor_user_id      VARCHAR(50),

      -- BaseEntity audit (V1/V3 컨벤션 그대로)
      created_at         TIMESTAMP    NOT NULL,
      created_by         VARCHAR(50)  NOT NULL,
      modified_at        TIMESTAMP,
      modified_by        VARCHAR(50),
      deleted_at         TIMESTAMP,
      deleted_by         VARCHAR(50),
      is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,

      CONSTRAINT ck_employee_signature_audit_action
          CHECK (action IN ('RECORD', 'INVALIDATE')),
      CONSTRAINT ck_employee_signature_audit_channel
          CHECK (signature_channel IS NULL
                 OR signature_channel IN ('MOBILE_CANVAS', 'UPLOAD'))
  );

  CREATE INDEX ix_employee_signature_audit_employee
      ON employee_signature_audit (employee_id, created_at DESC);

  CREATE INDEX ix_employee_signature_audit_action
      ON employee_signature_audit (action, created_at DESC);
  ```

- [ ] **Step 2: fresh Postgres probe — push 전 직접 적용 검증 의무 ([feedback_migration_fresh_postgres_probe]).** Windows 로컬 Testcontainers skip 이 syntax error 를 가린 전례. DROP/CREATE DB + `employees` 시드 row 1건 + `ON_ERROR_STOP` 로 V10 단독 적용.
  ```bash
  # Docker Postgres 기동(없으면)
  docker run -d --name sig-probe -e POSTGRES_PASSWORD=p -p 55432:5432 postgres:16-alpine
  # fresh DB
  PGPASSWORD=p psql -h localhost -p 55432 -U postgres -c "DROP DATABASE IF EXISTS sigprobe;"
  PGPASSWORD=p psql -h localhost -p 55432 -U postgres -c "CREATE DATABASE sigprobe;"
  # employees 최소 골격 + seed 1건 (V10 의 ALTER 대상)
  PGPASSWORD=p psql -h localhost -p 55432 -U postgres -d sigprobe -v ON_ERROR_STOP=1 -c \
    "CREATE TABLE employees (id UUID PRIMARY KEY, full_name VARCHAR(50), is_deleted BOOLEAN NOT NULL DEFAULT FALSE);
     INSERT INTO employees (id, full_name) VALUES (gen_random_uuid(), '프로브사원');"
  # V10 단독 적용 — syntax/CHECK 오류 시 즉시 비-0 exit
  cat services/user-service/src/main/resources/db/migration/V10__add_employee_signature.sql \
    | PGPASSWORD=p psql -h localhost -p 55432 -U postgres -d sigprobe -v ON_ERROR_STOP=1
  # CHECK 동작 검증: 비허용 채널 INSERT 는 거부되어야 함
  PGPASSWORD=p psql -h localhost -p 55432 -U postgres -d sigprobe -v ON_ERROR_STOP=1 -c \
    "UPDATE employees SET signature_channel='PAPER_SCAN';"   # 기대: ERROR (CHECK 위반)
  ```
  기대: V10 적용 BUILD/psql 성공(exit 0), 마지막 `PAPER_SCAN` UPDATE 는 `ERROR: new row ... violates check constraint`.

- [ ] **Step 3: commit.**
  ```
  git add services/user-service/src/main/resources/db/migration/V10__add_employee_signature.sql
  git commit -F <커밋메시지파일>
  ```
  메시지: `feat(user): Flyway V10 사원 서명 4컬럼+CHECK+감사 테이블 (C1a, fresh probe 검증)`
  > ⚠️ 머지 후 V10 은 불변 ([feedback_applied_migration_immutable]) — 주석조차 수정 금지.

---

### Task C1a.4: 서명 DTO 5종 (요청/응답)

**Files:**
- create `services/user-service/src/main/java/com/samhanair/logis/user/web/dto/EmployeeSignatureUploadRequest.java`
- create `services/user-service/src/main/java/com/samhanair/logis/user/web/dto/EmployeeSignatureResponse.java`
- create `services/user-service/src/main/java/com/samhanair/logis/user/web/dto/InternalSignatureBatchRequest.java`
- create `services/user-service/src/main/java/com/samhanair/logis/user/web/dto/EmployeeSignatureDto.java`
- create `services/user-service/src/main/java/com/samhanair/logis/user/web/dto/InvalidateEmployeeSignatureRequest.java`

**Interfaces (계약 글자 그대로):**
- Produces: `EmployeeSignatureUploadRequest{ String signaturePngBase64; String signatureHash; SignatureChannel channel }`
- Produces: `EmployeeSignatureResponse{ boolean registered; String signedAt(ISO); String signatureChannel }`
- Produces: `InternalSignatureBatchRequest{ List<UUID> userIds }`
- Produces: `EmployeeSignatureDto{ String signaturePngBase64; String signedAt(ISO) }`
- Produces: `InvalidateEmployeeSignatureRequest{ String reason }` (?reason= 쿼리도 허용하나 slip 무효화 패턴 정렬 위해 body record 도 제공 — 컨트롤러는 쿼리 param 사용, 본 record 는 미사용 시 생략 가능)

> 본 task 는 순수 record 정의이므로 컴파일만으로 검증. 다음 task(IT)가 실제 동작 검증.

- [ ] **Step 1: 요청 DTO 작성.**
  `EmployeeSignatureUploadRequest.java`:
  ```java
  package com.samhanair.logis.user.web.dto;

  import com.samhanair.logis.user.domain.SignatureChannel;
  import jakarta.validation.constraints.NotBlank;
  import jakarta.validation.constraints.NotNull;

  /**
   * 사원 서명 업로드/등록 요청 — C1a. PATCH /api/v1/admin/users/{id}/signature body.
   *
   * @param signaturePngBase64 PNG base64 (data URI 또는 raw, 서비스가 디코드)
   * @param signatureHash 클라 계산 SHA-256 hex 64자 (서버 재검증, 불일치 400)
   * @param channel 입력 채널 (MOBILE_CANVAS / UPLOAD)
   */
  public record EmployeeSignatureUploadRequest(
          @NotBlank(message = "signaturePngBase64 는 필수입니다") String signaturePngBase64,
          @NotBlank(message = "signatureHash 는 필수입니다") String signatureHash,
          @NotNull(message = "channel 은 필수입니다") SignatureChannel channel
  ) {
  }
  ```
  `InternalSignatureBatchRequest.java` (BulkVerifyRequest 미러):
  ```java
  package com.samhanair.logis.user.web.dto;

  import jakarta.validation.constraints.NotNull;
  import java.util.List;
  import java.util.UUID;

  /**
   * 내부 서명 배치 조회 요청 — C1a. POST /internal/users/signatures body.
   * slip-service 가 dispatcher/inspector/owner userId 다건의 서명을 한 번에 조회.
   *
   * @param userIds 서명을 조회할 user UUID 목록
   */
  public record InternalSignatureBatchRequest(
          @NotNull List<UUID> userIds
  ) {
  }
  ```

- [ ] **Step 2: 응답 DTO 작성.**
  `EmployeeSignatureResponse.java`:
  ```java
  package com.samhanair.logis.user.web.dto;

  import com.samhanair.logis.user.domain.Employee;
  import java.time.format.DateTimeFormatter;

  /**
   * 사원 서명 등록/조회 응답 — C1a. PATCH .../signature 200 body.
   *
   * @param registered 서명 등록 여부
   * @param signedAt 등록 시각 ISO-8601 (미등록 시 null)
   * @param signatureChannel 입력 채널 이름 (미등록 시 null)
   */
  public record EmployeeSignatureResponse(
          boolean registered,
          String signedAt,
          String signatureChannel
  ) {
      /** Employee 의 현재 서명 상태로 응답 매핑. */
      public static EmployeeSignatureResponse from(Employee employee) {
          boolean registered = employee.getSignedAt() != null;
          return new EmployeeSignatureResponse(
                  registered,
                  registered ? employee.getSignedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null,
                  employee.getSignatureChannel() == null ? null : employee.getSignatureChannel().name());
      }
  }
  ```
  `EmployeeSignatureDto.java`:
  ```java
  package com.samhanair.logis.user.web.dto;

  /**
   * 내부 서명 배치 조회 항목 — C1a. slip-service 결재란 인감 enrichment 용.
   *
   * <p>UUID 비공개 — 본 DTO 는 형제 service 한정 응답이며 PNG base64 + 등록 시각만 노출(userId 키는
   * Map 키로만 사용). signaturePngBase64 는 {@code data:image/png;base64,...} data URI 형식.
   *
   * @param signaturePngBase64 서명 PNG data URI (등록된 사원만 맵에 포함)
   * @param signedAt 등록 시각 ISO-8601
   */
  public record EmployeeSignatureDto(
          String signaturePngBase64,
          String signedAt
  ) {
  }
  ```

- [ ] **Step 3: 무효화 요청 DTO 작성 (slip InvalidateSignatureRequest 미러, 컨트롤러는 ?reason= 쿼리 사용 — body record 는 선택 제공이나 일관성 위해 생성).**
  `InvalidateEmployeeSignatureRequest.java`:
  ```java
  package com.samhanair.logis.user.web.dto;

  import jakarta.validation.constraints.NotBlank;
  import jakarta.validation.constraints.Size;

  /**
   * 사원 서명 무효화 요청 — C1a. MASTER 한정. (계약상 DELETE 는 ?reason= 쿼리이나, 향후 body
   * 전환 대비 record 제공.)
   *
   * @param reason 무효화 사유 (필수, ≤500자) — audit INVALIDATE 행에 저장
   */
  public record InvalidateEmployeeSignatureRequest(
          @NotBlank(message = "reason 은 필수입니다")
          @Size(max = 500, message = "reason 은 최대 500자입니다")
          String reason) {
  }
  ```

- [ ] **Step 4: 컴파일 검증.**
  ```
  ./gradlew :services:user-service:compileJava
  ```
  기대: BUILD SUCCESSFUL.

- [ ] **Step 5: commit.**
  ```
  git add services/user-service/src/main/java/com/samhanair/logis/user/web/dto/EmployeeSignatureUploadRequest.java \
          services/user-service/src/main/java/com/samhanair/logis/user/web/dto/EmployeeSignatureResponse.java \
          services/user-service/src/main/java/com/samhanair/logis/user/web/dto/InternalSignatureBatchRequest.java \
          services/user-service/src/main/java/com/samhanair/logis/user/web/dto/EmployeeSignatureDto.java \
          services/user-service/src/main/java/com/samhanair/logis/user/web/dto/InvalidateEmployeeSignatureRequest.java
  git commit -F <커밋메시지파일>
  ```
  메시지: `feat(user): 서명 등록/응답/내부배치 DTO 5종 (C1a)`

---

### Task C1a.5: EmployeeSignatureService — 해시 재검증 + PNG magic-byte + ≤50KB + 등록/무효화/배치

**Files:**
- create `services/user-service/src/main/java/com/samhanair/logis/user/service/EmployeeSignatureService.java`
- create `services/user-service/src/test/java/com/samhanair/logis/user/service/EmployeeSignatureServiceTest.java`

**Interfaces:**
- Produces: `EmployeeSignatureService.register(UUID employeeId, EmployeeSignatureUploadRequest req, String actorUserId) -> EmployeeSignatureResponse` (해시 재검증 400, PNG magic-byte 422, ≤50KB 422, 미존재 404)
- Produces: `EmployeeSignatureService.invalidate(UUID employeeId, String reason, String actorUserId) -> void` (미등록 409, 미존재 404)
- Produces: `EmployeeSignatureService.resolveSignatures(List<UUID> userIds) -> Map<UUID, EmployeeSignatureDto>` (미등록 사원 = 맵 생략)
- Produces (상수): `public static final int PNG_MAX_BYTES = 50 * 1024;`
- Consumes: `EmployeeRepository`(`findById`, `findAllByIdIn`), `EmployeeSignatureAuditRepository`, `Employee.registerSignature/invalidateSignature`

- [ ] **Step 1: 실패 테스트 작성 — 순수 단위(Mockito, Spring 불필요).** 해시 재검증·magic-byte·50KB·배치 누락·404·409 전수.
  `services/user-service/src/test/java/com/samhanair/logis/user/service/EmployeeSignatureServiceTest.java`:
  ```java
  package com.samhanair.logis.user.service;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.lenient;
  import static org.mockito.Mockito.verify;
  import static org.mockito.Mockito.when;

  import com.samhanair.logis.common.exception.BusinessException;
  import com.samhanair.logis.common.exception.ErrorCode;
  import com.samhanair.logis.common.security.Role;
  import com.samhanair.logis.user.domain.Department;
  import com.samhanair.logis.user.domain.Employee;
  import com.samhanair.logis.user.domain.EmployeeSignatureAudit;
  import com.samhanair.logis.user.domain.SignatureChannel;
  import com.samhanair.logis.user.repository.EmployeeRepository;
  import com.samhanair.logis.user.repository.EmployeeSignatureAuditRepository;
  import com.samhanair.logis.user.web.dto.EmployeeSignatureDto;
  import com.samhanair.logis.user.web.dto.EmployeeSignatureResponse;
  import com.samhanair.logis.user.web.dto.EmployeeSignatureUploadRequest;
  import java.security.MessageDigest;
  import java.time.LocalDate;
  import java.util.Base64;
  import java.util.List;
  import java.util.Map;
  import java.util.Optional;
  import java.util.UUID;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  /** EmployeeSignatureService 단위 테스트 — C1a (해시/magic-byte/50KB/배치/404/409). */
  @ExtendWith(MockitoExtension.class)
  class EmployeeSignatureServiceTest {

      @Mock private EmployeeRepository employeeRepository;
      @Mock private EmployeeSignatureAuditRepository auditRepository;
      @InjectMocks private EmployeeSignatureService service;

      private Employee employee;
      private UUID empId;

      @BeforeEach
      void setUp() {
          empId = UUID.randomUUID();
          Department department = Department.create("SIG", "서명팀", 951);
          employee = Employee.create(empId, "sig01", "서명자", "사원",
                  Role.STAFF, department, false, LocalDate.of(2026, 1, 1), null, null);
      }

      /** 최소 유효 PNG = 8바이트 PNG 시그니처. */
      private static byte[] pngBytes() {
          return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
      }

      private static String b64(byte[] data) {
          return Base64.getEncoder().encodeToString(data);
      }

      private static String sha256Hex(byte[] data) throws Exception {
          byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
          StringBuilder sb = new StringBuilder(64);
          for (byte b : d) {
              sb.append(String.format("%02x", b));
          }
          return sb.toString();
      }

      @Test
      void register_정상_업로드는_서명을_저장하고_RECORD_audit를_적재한다() throws Exception {
          byte[] png = pngBytes();
          when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
          EmployeeSignatureUploadRequest req = new EmployeeSignatureUploadRequest(
                  b64(png), sha256Hex(png), SignatureChannel.UPLOAD);

          EmployeeSignatureResponse res = service.register(empId, req, "actor-1");

          assertThat(res.registered()).isTrue();
          assertThat(res.signatureChannel()).isEqualTo("UPLOAD");
          assertThat(res.signedAt()).isNotNull();
          assertThat(employee.getSignatureHash()).isEqualTo(req.signatureHash());
          verify(auditRepository).save(any(EmployeeSignatureAudit.class));
      }

      @Test
      void register_해시_불일치는_400_INVALID_INPUT() {
          byte[] png = pngBytes();
          when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
          EmployeeSignatureUploadRequest req = new EmployeeSignatureUploadRequest(
                  b64(png), "f".repeat(64), SignatureChannel.UPLOAD);

          assertThatThrownBy(() -> service.register(empId, req, "actor-1"))
                  .isInstanceOf(BusinessException.class)
                  .extracting(ex -> ((BusinessException) ex).getErrorCode())
                  .isEqualTo(ErrorCode.INVALID_INPUT);
      }

      @Test
      void register_PNG_magic_byte_아니면_422() throws Exception {
          byte[] notPng = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
          when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
          EmployeeSignatureUploadRequest req = new EmployeeSignatureUploadRequest(
                  b64(notPng), sha256Hex(notPng), SignatureChannel.UPLOAD);

          assertThatThrownBy(() -> service.register(empId, req, "actor-1"))
                  .isInstanceOf(BusinessException.class)
                  .extracting(ex -> ((BusinessException) ex).getErrorCode())
                  .isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
      }

      @Test
      void register_50KB_초과는_422() throws Exception {
          byte[] big = new byte[EmployeeSignatureService.PNG_MAX_BYTES + 1];
          big[0] = (byte) 0x89; big[1] = 0x50; big[2] = 0x4E; big[3] = 0x47;
          big[4] = 0x0D; big[5] = 0x0A; big[6] = 0x1A; big[7] = 0x0A;
          when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
          EmployeeSignatureUploadRequest req = new EmployeeSignatureUploadRequest(
                  b64(big), sha256Hex(big), SignatureChannel.UPLOAD);

          assertThatThrownBy(() -> service.register(empId, req, "actor-1"))
                  .isInstanceOf(BusinessException.class)
                  .extracting(ex -> ((BusinessException) ex).getErrorCode())
                  .isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
      }

      @Test
      void register_미존재_사원은_404() throws Exception {
          when(employeeRepository.findById(empId)).thenReturn(Optional.empty());
          byte[] png = pngBytes();
          EmployeeSignatureUploadRequest req = new EmployeeSignatureUploadRequest(
                  b64(png), sha256Hex(png), SignatureChannel.UPLOAD);

          assertThatThrownBy(() -> service.register(empId, req, "actor-1"))
                  .isInstanceOf(BusinessException.class)
                  .extracting(ex -> ((BusinessException) ex).getErrorCode())
                  .isEqualTo(ErrorCode.NOT_FOUND);
      }

      @Test
      void invalidate_미등록_사원은_409_CONFLICT() {
          when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));

          assertThatThrownBy(() -> service.invalidate(empId, "사유", "master-1"))
                  .isInstanceOf(BusinessException.class)
                  .extracting(ex -> ((BusinessException) ex).getErrorCode())
                  .isEqualTo(ErrorCode.CONFLICT);
      }

      @Test
      void invalidate_등록된_서명을_NULL로_만들고_INVALIDATE_audit를_적재한다() throws Exception {
          byte[] png = pngBytes();
          employee.registerSignature(png, sha256Hex(png), SignatureChannel.UPLOAD);
          when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));

          service.invalidate(empId, "오등록", "master-1");

          assertThat(employee.getSignedAt()).isNull();
          verify(auditRepository).save(any(EmployeeSignatureAudit.class));
      }

      @Test
      void resolveSignatures_등록된_사원만_맵에_담고_미등록은_생략한다() throws Exception {
          byte[] png = pngBytes();
          employee.registerSignature(png, sha256Hex(png), SignatureChannel.UPLOAD);
          UUID unsignedId = UUID.randomUUID();
          Department d = Department.create("SIG2", "서명팀2", 952);
          Employee unsigned = Employee.create(unsignedId, "sig02", "미등록", "사원",
                  Role.STAFF, d, false, LocalDate.of(2026, 1, 1), null, null);
          lenient().when(employeeRepository.findAllByIdIn(any()))
                  .thenReturn(List.of(employee, unsigned));

          Map<UUID, EmployeeSignatureDto> result =
                  service.resolveSignatures(List.of(empId, unsignedId));

          assertThat(result).containsKey(empId);
          assertThat(result).doesNotContainKey(unsignedId);
          assertThat(result.get(empId).signaturePngBase64()).startsWith("data:image/png;base64,");
      }

      @Test
      void resolveSignatures_빈_입력은_빈_맵() {
          assertThat(service.resolveSignatures(List.of())).isEmpty();
          assertThat(service.resolveSignatures(null)).isEmpty();
      }
  }
  ```
  > `BusinessException.getErrorCode()` getter 명은 정찰 시 실제 확인(slip 도 동일 클래스 사용). 다르면 단언 property 명만 정렬.

- [ ] **Step 2: 실행 → FAIL.**
  ```
  ./gradlew :services:user-service:test --tests "com.samhanair.logis.user.service.EmployeeSignatureServiceTest"
  ```
  기대: COMPILE FAIL (`cannot find symbol: EmployeeSignatureService`).

- [ ] **Step 3: EmployeeSignatureService 구현.** (`SlipSignatureService` 의 `decodePng`/`sha256Hex`/50KB 가드 1:1 미러 + PNG magic-byte(8바이트 시그니처) 추가 + magic-byte/50KB → 422 매핑)
  ```java
  package com.samhanair.logis.user.service;

  import com.samhanair.logis.common.exception.BusinessException;
  import com.samhanair.logis.common.exception.ErrorCode;
  import com.samhanair.logis.user.domain.Employee;
  import com.samhanair.logis.user.domain.EmployeeSignatureAudit;
  import com.samhanair.logis.user.domain.SignatureChannel;
  import com.samhanair.logis.user.repository.EmployeeRepository;
  import com.samhanair.logis.user.repository.EmployeeSignatureAuditRepository;
  import com.samhanair.logis.user.web.dto.EmployeeSignatureDto;
  import com.samhanair.logis.user.web.dto.EmployeeSignatureResponse;
  import com.samhanair.logis.user.web.dto.EmployeeSignatureUploadRequest;
  import java.security.MessageDigest;
  import java.security.NoSuchAlgorithmException;
  import java.time.format.DateTimeFormatter;
  import java.util.Base64;
  import java.util.LinkedHashMap;
  import java.util.LinkedHashSet;
  import java.util.List;
  import java.util.Map;
  import java.util.Objects;
  import java.util.Set;
  import java.util.UUID;
  import java.util.stream.Collectors;
  import lombok.RequiredArgsConstructor;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;

  /**
   * 사원 서명(인감) 등록/무효화/배치 조회 워크플로우 — C1a.
   *
   * <p>검증(slip SlipSignatureService 미러 + magic-byte 추가):
   * <ol>
   *   <li>PNG base64 디코드 (실패 400)</li>
   *   <li>PNG magic-byte(8바이트 시그니처) 검증 — 비-PNG 422</li>
   *   <li>크기 ≤ {@value #PNG_MAX_BYTES} bytes 가드 — 초과 422</li>
   *   <li>서버 SHA-256 재계산 → 클라 hash 불일치 400</li>
   * </ol>
   */
  @Service
  @Transactional
  @RequiredArgsConstructor
  public class EmployeeSignatureService {

      /** PNG 크기 가드 — 50KB (slip PNG_MAX_BYTES 미러). */
      public static final int PNG_MAX_BYTES = 50 * 1024;

      /** PNG 파일 시그니처 8바이트 (magic-byte). */
      private static final byte[] PNG_SIGNATURE =
              {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

      private final EmployeeRepository employeeRepository;
      private final EmployeeSignatureAuditRepository auditRepository;

      /**
       * 서명 등록(업로드/모바일) — 해시 재검증 + magic-byte + ≤50KB 후 Employee.registerSignature.
       *
       * @param employeeId 대상 사원 UUID
       * @param req 업로드 요청(base64 + 클라 hash + channel)
       * @param actorUserId 처리자 user-id (모바일 공개 경로는 null 가능)
       * @return 등록 결과
       * @throws BusinessException(NOT_FOUND) 사원 미발견
       * @throws BusinessException(INVALID_INPUT) base64 디코드 실패 / hash 불일치
       * @throws BusinessException(UNPROCESSABLE_ENTITY) 비-PNG / 50KB 초과
       */
      public EmployeeSignatureResponse register(UUID employeeId,
                                                EmployeeSignatureUploadRequest req,
                                                String actorUserId) {
          Employee employee = employeeRepository.findById(employeeId)
                  .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                          "직원을 찾을 수 없습니다: " + employeeId));

          byte[] png = decodePng(req.signaturePngBase64());
          if (!isPng(png)) {
              throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY,
                      "PNG 이미지가 아닙니다 (magic-byte 불일치)");
          }
          if (png.length > PNG_MAX_BYTES) {
              throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY,
                      "서명 PNG 가 너무 큽니다 (" + png.length + " bytes, 최대 " + PNG_MAX_BYTES + ")");
          }
          String serverHash = sha256Hex(png);
          if (!serverHash.equalsIgnoreCase(req.signatureHash())) {
              throw new BusinessException(ErrorCode.INVALID_INPUT,
                      "서명 무결성 검증 실패 — 클라이언트 hash 가 일치하지 않습니다");
          }

          employee.registerSignature(png, serverHash, req.channel());
          auditRepository.save(EmployeeSignatureAudit.record(
                  employee.getId(), serverHash, req.channel(), actorUserId));
          return EmployeeSignatureResponse.from(employee);
      }

      /**
       * 서명 무효화(MASTER) — 직전 hash/channel snapshot 후 Employee.invalidateSignature + audit.
       *
       * @param employeeId 대상 사원 UUID
       * @param reason 무효화 사유 (필수)
       * @param actorUserId 처리자 user-id (필수)
       * @throws BusinessException(NOT_FOUND) 사원 미발견
       * @throws BusinessException(CONFLICT) 미등록 상태 무효화 시도
       */
      public void invalidate(UUID employeeId, String reason, String actorUserId) {
          Employee employee = employeeRepository.findById(employeeId)
                  .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                          "직원을 찾을 수 없습니다: " + employeeId));
          // 직전 snapshot — invalidate 후 NULL
          String prevHash = employee.getSignatureHash();
          SignatureChannel prevChannel = employee.getSignatureChannel();

          employee.invalidateSignature(reason);
          auditRepository.save(EmployeeSignatureAudit.invalidate(
                  employee.getId(), prevHash, prevChannel, reason, actorUserId));
      }

      /**
       * 내부 서명 배치 조회 — slip 결재란 인감 enrichment. 미등록 사원은 맵에서 생략한다.
       *
       * @param userIds 조회 대상 user UUID 목록
       * @return {@code userId -> EmployeeSignatureDto} (등록 사원만)
       */
      @Transactional(readOnly = true)
      public Map<UUID, EmployeeSignatureDto> resolveSignatures(List<UUID> userIds) {
          if (userIds == null || userIds.isEmpty()) {
              return Map.of();
          }
          Set<UUID> distinct = userIds.stream()
                  .filter(Objects::nonNull)
                  .collect(Collectors.toCollection(LinkedHashSet::new));
          if (distinct.isEmpty()) {
              return Map.of();
          }
          Map<UUID, EmployeeSignatureDto> result = new LinkedHashMap<>();
          for (Employee e : employeeRepository.findAllByIdIn(distinct)) {
              if (e.getSignedAt() == null || e.getSignaturePng() == null) {
                  continue; // 미등록 = 생략
              }
              String dataUri = "data:image/png;base64,"
                      + Base64.getEncoder().encodeToString(e.getSignaturePng());
              String signedAt = e.getSignedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
              result.put(e.getId(), new EmployeeSignatureDto(dataUri, signedAt));
          }
          return result;
      }

      // ---------- helpers (slip SlipSignatureService 미러) ----------

      private boolean isPng(byte[] data) {
          if (data == null || data.length < PNG_SIGNATURE.length) {
              return false;
          }
          for (int i = 0; i < PNG_SIGNATURE.length; i++) {
              if (data[i] != PNG_SIGNATURE[i]) {
                  return false;
              }
          }
          return true;
      }

      private byte[] decodePng(String input) {
          if (input == null) {
              throw new BusinessException(ErrorCode.INVALID_INPUT, "signaturePngBase64 가 비어있습니다");
          }
          String base64 = input.contains(",") ? input.substring(input.indexOf(',') + 1) : input;
          try {
              return Base64.getDecoder().decode(base64);
          } catch (IllegalArgumentException ex) {
              throw new BusinessException(ErrorCode.INVALID_INPUT, "PNG base64 디코드 실패");
          }
      }

      private String sha256Hex(byte[] data) {
          try {
              byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
              StringBuilder sb = new StringBuilder(64);
              for (byte b : digest) {
                  sb.append(String.format("%02x", b));
              }
              return sb.toString();
          } catch (NoSuchAlgorithmException e) {
              throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 알고리즘 미지원");
          }
      }
  }
  ```

- [ ] **Step 4: 실행 → PASS.**
  ```
  ./gradlew :services:user-service:test --tests "com.samhanair.logis.user.service.EmployeeSignatureServiceTest"
  ```
  기대: BUILD SUCCESSFUL, 9 tests passed.

- [ ] **Step 5: commit.**
  ```
  git add services/user-service/src/main/java/com/samhanair/logis/user/service/EmployeeSignatureService.java \
          services/user-service/src/test/java/com/samhanair/logis/user/service/EmployeeSignatureServiceTest.java
  git commit -F <커밋메시지파일>
  ```
  메시지: `feat(user): EmployeeSignatureService — 해시재검증+magic-byte+50KB+배치조회 (C1a)`

---

### Task C1a.6: AdminUserController PATCH/DELETE 서명 엔드포인트 + IT (저장/해시/50KB/재등록/409)

**Files:**
- modify `services/user-service/src/main/java/com/samhanair/logis/user/web/AdminUserController.java` (검증: 존재)
- create `services/user-service/src/test/java/com/samhanair/logis/user/it/AdminUserSignatureControllerIT.java`

**Interfaces (계약 글자 그대로):**
- Produces: `PATCH /api/v1/admin/users/{id}/signature` body `EmployeeSignatureUploadRequest` → 200 `ApiResponse<EmployeeSignatureResponse>`, `@RequireDepartment(EXECUTIVE_OFFICE)` + `@RequirePermission(page="admin.users", action=UPDATE)`
- Produces: `DELETE /api/v1/admin/users/{id}/signature?reason=...` → 204, `@RequireDepartment(EXECUTIVE_OFFICE)` + `@RequirePermission(page="admin.users", action=DELETE)` (MASTER seed 만 통과)
- Consumes: `EmployeeSignatureService`(C1a.5), `EmployeeSignatureUploadRequest`/`EmployeeSignatureResponse`(C1a.4)

- [ ] **Step 1: 실패 IT 작성 — 실 Postgres(Testcontainers) + MockMvc.** `InternalUserSearchControllerIT` 구조 + `UserPermissionControllerIT` 권한 mock 패턴 결합. `@MockBean DynamicPermissionClient` 가 `check(...)=true` 면 게이트 통과(권한 게이트는 UserPermissionControllerIT 가 별도 커버 — 본 IT 는 비즈니스 동작 검증).

  `services/user-service/src/test/java/com/samhanair/logis/user/it/AdminUserSignatureControllerIT.java`:
  ```java
  package com.samhanair.logis.user.it;

  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.ArgumentMatchers.anyString;
  import static org.mockito.Mockito.lenient;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

  import com.samhanair.logis.common.security.Role;
  import com.samhanair.logis.security.permission.DynamicPermissionClient;
  import com.samhanair.logis.security.permission.PermissionAction;
  import com.samhanair.logis.user.UserServiceApplication;
  import com.samhanair.logis.user.client.AuthClient;
  import com.samhanair.logis.user.domain.Department;
  import com.samhanair.logis.user.domain.Employee;
  import com.samhanair.logis.user.domain.SignatureChannel;
  import com.samhanair.logis.user.repository.DepartmentRepository;
  import com.samhanair.logis.user.repository.EmployeeRepository;
  import com.samhanair.logis.user.repository.EmployeeSignatureAuditRepository;
  import com.samhanair.logis.security.HrAuthorizationHelper;
  import java.security.MessageDigest;
  import java.time.LocalDate;
  import java.util.Base64;
  import java.util.UUID;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.boot.test.mock.mockito.MockBean;
  import org.springframework.http.MediaType;
  import org.springframework.test.web.servlet.MockMvc;

  /** AdminUserController 서명 PATCH/DELETE IT — C1a (저장/해시/50KB/재등록/409). */
  @SpringBootTest(classes = UserServiceApplication.class,
          properties = "samhan.security.department.enabled=true")
  @AutoConfigureMockMvc
  class AdminUserSignatureControllerIT extends AbstractPostgresIT {

      private static final String USER_ID_HEADER = "X-User-Id";
      private static final String ROLE_HEADER = "X-User-Role";
      private static final String DEPARTMENT_HEADER = "X-User-Department";

      @Autowired private MockMvc mockMvc;
      @Autowired private DepartmentRepository departmentRepository;
      @Autowired private EmployeeRepository employeeRepository;
      @Autowired private EmployeeSignatureAuditRepository auditRepository;

      @MockBean private DynamicPermissionClient dynamicPermissionClient;
      @MockBean private AuthClient authClient;

      private Department department;

      @BeforeEach
      void setUp() {
          lenient().when(dynamicPermissionClient.check(any(UUID.class), anyString(), any(PermissionAction.class)))
                  .thenReturn(true);
          department = departmentRepository.findByCode("SIG_CTRL_IT")
                  .orElseGet(() -> departmentRepository.save(
                          Department.create("SIG_CTRL_IT", "서명컨트롤IT", 953)));
      }

      private UUID newEmployee() {
          Employee e = Employee.create(UUID.randomUUID(), "sigit-" + shortId(), "서명사원", "사원",
                  Role.STAFF, department, false, LocalDate.of(2026, 1, 1), null, null);
          return employeeRepository.saveAndFlush(e).getId();
      }

      private static byte[] pngBytes() {
          return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
      }

      private static String b64(byte[] data) {
          return Base64.getEncoder().encodeToString(data);
      }

      private static String sha256Hex(byte[] data) throws Exception {
          byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
          StringBuilder sb = new StringBuilder(64);
          for (byte b : d) {
              sb.append(String.format("%02x", b));
          }
          return sb.toString();
      }

      private String uploadBody(byte[] png, String hash, SignatureChannel channel) {
          return """
                  {"signaturePngBase64":"%s","signatureHash":"%s","channel":"%s"}
                  """.formatted(b64(png), hash, channel.name());
      }

      private static String shortId() {
          return UUID.randomUUID().toString().substring(0, 8);
      }

      private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withMaster(
              org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) {
          return req.header(USER_ID_HEADER, UUID.randomUUID().toString())
                  .header(ROLE_HEADER, "MASTER")
                  .header(DEPARTMENT_HEADER, HrAuthorizationHelper.EXECUTIVE_OFFICE_NAME);
      }

      @Test
      void PATCH_업로드는_서명을_저장하고_200에_registered_true를_반환한다() throws Exception {
          UUID id = newEmployee();
          byte[] png = pngBytes();

          mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(uploadBody(png, sha256Hex(png), SignatureChannel.UPLOAD)))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.data.registered").value(true))
                  .andExpect(jsonPath("$.data.signatureChannel").value("UPLOAD"))
                  .andExpect(jsonPath("$.data.signedAt").isNotEmpty());

          Employee saved = employeeRepository.findById(id).orElseThrow();
          org.assertj.core.api.Assertions.assertThat(saved.getSignatureHash())
                  .isEqualTo(sha256Hex(png));
          org.assertj.core.api.Assertions.assertThat(
                  auditRepository.findAllByEmployeeIdOrderByCreatedAtDesc(id)).hasSize(1);
      }

      @Test
      void PATCH_해시_불일치는_400() throws Exception {
          UUID id = newEmployee();
          mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(uploadBody(pngBytes(), "f".repeat(64), SignatureChannel.UPLOAD)))
                  .andExpect(status().isBadRequest());
      }

      @Test
      void PATCH_비_PNG_magic_byte는_422() throws Exception {
          UUID id = newEmployee();
          byte[] notPng = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
          mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(uploadBody(notPng, sha256Hex(notPng), SignatureChannel.UPLOAD)))
                  .andExpect(status().isUnprocessableEntity());
      }

      @Test
      void PATCH_50KB_초과는_422() throws Exception {
          UUID id = newEmployee();
          byte[] big = new byte[EmployeeSignatureServiceMaxRef.MAX + 1];
          big[0] = (byte) 0x89; big[1] = 0x50; big[2] = 0x4E; big[3] = 0x47;
          big[4] = 0x0D; big[5] = 0x0A; big[6] = 0x1A; big[7] = 0x0A;
          mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(uploadBody(big, sha256Hex(big), SignatureChannel.UPLOAD)))
                  .andExpect(status().isUnprocessableEntity());
      }

      @Test
      void PATCH_재등록은_기존_서명을_교체한다() throws Exception {
          UUID id = newEmployee();
          byte[] first = pngBytes();
          mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(uploadBody(first, sha256Hex(first), SignatureChannel.UPLOAD)))
                  .andExpect(status().isOk());
          byte[] second = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x11};
          mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(uploadBody(second, sha256Hex(second), SignatureChannel.MOBILE_CANVAS)))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.data.signatureChannel").value("MOBILE_CANVAS"));

          Employee saved = employeeRepository.findById(id).orElseThrow();
          org.assertj.core.api.Assertions.assertThat(saved.getSignatureHash())
                  .isEqualTo(sha256Hex(second));
      }

      @Test
      void DELETE_등록된_서명은_204로_무효화된다() throws Exception {
          UUID id = newEmployee();
          byte[] png = pngBytes();
          mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(uploadBody(png, sha256Hex(png), SignatureChannel.UPLOAD)))
                  .andExpect(status().isOk());

          mockMvc.perform(withMaster(delete("/api/v1/admin/users/{id}/signature", id))
                          .param("reason", "오등록 정정"))
                  .andExpect(status().isNoContent());

          Employee saved = employeeRepository.findById(id).orElseThrow();
          org.assertj.core.api.Assertions.assertThat(saved.getSignedAt()).isNull();
      }

      @Test
      void DELETE_미등록_서명_무효화는_409() throws Exception {
          UUID id = newEmployee();
          mockMvc.perform(withMaster(delete("/api/v1/admin/users/{id}/signature", id))
                          .param("reason", "사유"))
                  .andExpect(status().isConflict());
      }

      /** PNG_MAX_BYTES 참조 (서비스 상수 직접 인용). */
      static final class EmployeeSignatureServiceMaxRef {
          static final int MAX =
                  com.samhanair.logis.user.service.EmployeeSignatureService.PNG_MAX_BYTES;
      }
  }
  ```
  > `DepartmentRepository.findByCode(...)` 시그니처는 `InternalUserSearchControllerIT:48` 에서 검증됨. `samhan.security.department.enabled=true` + MASTER role 헤더로 `@RequireDepartment` 통과(UserPermissionControllerIT 의 MASTER bypass 와 동일 매커니즘 — MASTER 는 부서 게이트 우회).

- [ ] **Step 2: 실행 → FAIL.**
  ```
  ./gradlew :services:user-service:test --tests "com.samhanair.logis.user.it.AdminUserSignatureControllerIT"
  ```
  기대: COMPILE FAIL (`patch .../signature` 핸들러 미존재 → 404, 또는 컴파일 단계에서 import 부재). Docker 미가용 시 skip(이 경우 fresh probe + Linux CI 가 커버 — [feedback_qa_docker_real_test]).

- [ ] **Step 3: AdminUserController 에 2 엔드포인트 추가.** import 에 추가:
  ```java
  import com.samhanair.logis.user.service.EmployeeSignatureService;
  import com.samhanair.logis.user.web.dto.EmployeeSignatureResponse;
  import com.samhanair.logis.user.web.dto.EmployeeSignatureUploadRequest;
  import org.springframework.web.bind.annotation.DeleteMapping;
  ```
  생성자 주입 필드 추가(`@RequiredArgsConstructor` 이므로 final 필드만 추가):
  ```java
      private final EmployeeSignatureService signatureService;
  ```
  `roleHistory(...)` 메서드(`:244-248`) 다음, helper 구역(`:250`) 앞에 추가:
  ```java
      // -------------------------------------------------------------------------
      // 서명(인감) — C1a
      // -------------------------------------------------------------------------

      /**
       * 사원 서명 등록/교체 (대표실 + admin.users UPDATE) — 업로드 또는 모바일 핸드오프 공통 저장.
       *
       * <p>서버 가드: PNG magic-byte + ≤50KB(초과 422) + 클라 hash 재검증(불일치 400). 재등록 시
       * 기존 서명 교체. audit RECORD 1행 적재.
       *
       * @param id 대상 직원 UUID
       * @param request 서명 업로드 요청 (base64 + hash + channel)
       * @param callerHeader X-User-Id 헤더 (audit actor)
       * @return 등록 결과 (registered / signedAt / signatureChannel)
       */
      @PatchMapping("/{id}/signature")
      @RequireDepartment(Department.EXECUTIVE_OFFICE)
      @RequirePermission(page = "admin.users", action = PermissionAction.UPDATE)
      public ApiResponse<EmployeeSignatureResponse> registerSignature(
              @PathVariable UUID id,
              @Valid @RequestBody EmployeeSignatureUploadRequest request,
              @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
          UUID caller = parseCaller(callerHeader);
          return ApiResponse.ok(signatureService.register(
                  id, request, caller == null ? null : caller.toString()));
      }

      /**
       * 사원 서명 무효화 (MASTER 한정 — admin.users DELETE seed 가 MASTER 만 허용).
       *
       * <p>등록된 서명 4필드 NULL + audit INVALIDATE 1행. 미등록 상태 무효화는 409.
       *
       * @param id 대상 직원 UUID
       * @param reason 무효화 사유 (필수)
       * @param callerHeader X-User-Id 헤더 (audit actor)
       */
      @DeleteMapping("/{id}/signature")
      @ResponseStatus(HttpStatus.NO_CONTENT)
      @RequireDepartment(Department.EXECUTIVE_OFFICE)
      @RequirePermission(page = "admin.users", action = PermissionAction.DELETE)
      public void invalidateSignature(
              @PathVariable UUID id,
              @RequestParam(value = "reason") String reason,
              @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
          UUID caller = parseCaller(callerHeader);
          signatureService.invalidate(id, reason, caller == null ? "system" : caller.toString());
      }
  ```

- [ ] **Step 4: 실행 → PASS.**
  ```
  ./gradlew :services:user-service:test --tests "com.samhanair.logis.user.it.AdminUserSignatureControllerIT"
  ```
  기대: BUILD SUCCESSFUL, 7 tests passed (Docker 가용 시). Docker 미가용 시 skip → Step 6 Linux CI 에서 green 확인.

- [ ] **Step 5: 기존 권한 IT 회귀 가드 — 신규 엔드포인트도 `@RequireDepartment` 보유 확인.** `UserPermissionControllerIT.adminUserEndpointsUseRequireDepartmentAndNoPreAuthorize`(`:221-231`) 에 2줄 추가(같은 PR, 회귀 박제 [feedback_defect_family_sweep_fix]):
  ```java
          assertDepartmentGate("registerSignature", UUID.class,
                  com.samhanair.logis.user.web.dto.EmployeeSignatureUploadRequest.class, String.class);
          assertDepartmentGate("invalidateSignature", UUID.class, String.class, String.class);
  ```
  실행:
  ```
  ./gradlew :services:user-service:test --tests "com.samhanair.logis.user.it.UserPermissionControllerIT"
  ```
  기대: BUILD SUCCESSFUL (신규 2 메서드의 `@RequireDepartment(EXECUTIVE_OFFICE)` + `@PreAuthorize` 부재 박제 통과).

- [ ] **Step 6: 변경 모듈 전체 test 완주 ([feedback_changed_module_full_test_before_push]).**
  ```
  ./gradlew :services:user-service:test
  ```
  기대: BUILD SUCCESSFUL (신규 IT 타깃만이 아니라 기존 mock 단위테스트 회귀 동반 확인).

- [ ] **Step 7: commit.**
  ```
  git add services/user-service/src/main/java/com/samhanair/logis/user/web/AdminUserController.java \
          services/user-service/src/test/java/com/samhanair/logis/user/it/AdminUserSignatureControllerIT.java \
          services/user-service/src/test/java/com/samhanair/logis/user/it/UserPermissionControllerIT.java
  git commit -F <커밋메시지파일>
  ```
  메시지: `feat(user): AdminUserController 서명 PATCH(업로드)/DELETE(무효화 MASTER) + IT (C1a)`

---

### Task C1a.7: InternalUserController POST /internal/users/signatures 배치 + IT (배치/join-key 회귀)

**Files:**
- modify `services/user-service/src/main/java/com/samhanair/logis/user/web/InternalUserController.java` (검증: 존재)
- create `services/user-service/src/test/java/com/samhanair/logis/user/it/InternalUserSignatureBatchControllerIT.java`

**Interfaces (계약 글자 그대로):**
- Produces: `POST /internal/users/signatures` body `InternalSignatureBatchRequest{ List<UUID> userIds }` → 200 `ApiResponse<Map<UUID, EmployeeSignatureDto>>` (미등록 사원 = 맵 생략), `@PreAuthorize("hasRole('MASTER')")` + X-Internal-Token
- Consumes: `EmployeeSignatureService.resolveSignatures(...)`(C1a.5), `InternalSignatureBatchRequest`/`EmployeeSignatureDto`(C1a.4)

> ⚠️ join-key 회귀 = slip 의 userId(`createdBy`/`dispatcherUserId`/`inspectorUserId`) = `Employee.id` 로 조회 시 서명 반환. 본 IT 가 P4 join key 를 직접 박제.

- [ ] **Step 1: 실패 IT 작성 — 실 Postgres + X-Internal-Token + 배치 + join-key 회귀.**
  `services/user-service/src/test/java/com/samhanair/logis/user/it/InternalUserSignatureBatchControllerIT.java`:
  ```java
  package com.samhanair.logis.user.it;

  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

  import com.samhanair.logis.common.security.Role;
  import com.samhanair.logis.security.permission.DynamicPermissionClient;
  import com.samhanair.logis.user.UserServiceApplication;
  import com.samhanair.logis.user.client.AuthClient;
  import com.samhanair.logis.user.domain.Department;
  import com.samhanair.logis.user.domain.Employee;
  import com.samhanair.logis.user.domain.SignatureChannel;
  import com.samhanair.logis.user.repository.DepartmentRepository;
  import com.samhanair.logis.user.repository.EmployeeRepository;
  import java.security.MessageDigest;
  import java.time.LocalDate;
  import java.util.UUID;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.boot.test.mock.mockito.MockBean;
  import org.springframework.http.MediaType;
  import org.springframework.test.web.servlet.MockMvc;

  /** POST /internal/users/signatures 배치 IT — C1a (배치/미등록생략/join-key/토큰). */
  @SpringBootTest(classes = UserServiceApplication.class)
  @AutoConfigureMockMvc
  class InternalUserSignatureBatchControllerIT extends AbstractPostgresIT {

      private static final String TOKEN = "test-internal-token";

      @Autowired private MockMvc mockMvc;
      @Autowired private DepartmentRepository departmentRepository;
      @Autowired private EmployeeRepository employeeRepository;

      @MockBean private DynamicPermissionClient dynamicPermissionClient;
      @MockBean private AuthClient authClient;

      private Department department;

      @BeforeEach
      void setUp() {
          department = departmentRepository.findByCode("SIG_BATCH_IT")
                  .orElseGet(() -> departmentRepository.save(
                          Department.create("SIG_BATCH_IT", "서명배치IT", 954)));
      }

      private UUID signedEmployee() throws Exception {
          Employee e = Employee.create(UUID.randomUUID(), "sigb-" + shortId(), "서명사원", "사원",
                  Role.STAFF, department, false, LocalDate.of(2026, 1, 1), null, null);
          byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
          e.registerSignature(png, sha256Hex(png), SignatureChannel.UPLOAD);
          return employeeRepository.saveAndFlush(e).getId();
      }

      private UUID unsignedEmployee() {
          Employee e = Employee.create(UUID.randomUUID(), "sigu-" + shortId(), "미등록사원", "사원",
                  Role.STAFF, department, false, LocalDate.of(2026, 1, 1), null, null);
          return employeeRepository.saveAndFlush(e).getId();
      }

      private static String sha256Hex(byte[] data) throws Exception {
          byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
          StringBuilder sb = new StringBuilder(64);
          for (byte b : d) {
              sb.append(String.format("%02x", b));
          }
          return sb.toString();
      }

      private static String shortId() {
          return UUID.randomUUID().toString().substring(0, 8);
      }

      @Test
      void 배치조회는_등록사원만_맵에_담고_미등록은_생략한다() throws Exception {
          UUID signed = signedEmployee();
          UUID unsigned = unsignedEmployee();
          UUID missing = UUID.randomUUID();

          mockMvc.perform(post("/internal/users/signatures")
                          .header("X-Internal-Token", TOKEN)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("""
                                  {"userIds":["%s","%s","%s"]}
                                  """.formatted(signed, unsigned, missing)))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.data['%s'].signaturePngBase64".formatted(signed))
                          .value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                  .andExpect(jsonPath("$.data['%s'].signedAt".formatted(signed)).isNotEmpty())
                  .andExpect(jsonPath("$.data['%s']".formatted(unsigned)).doesNotExist())
                  .andExpect(jsonPath("$.data['%s']".formatted(missing)).doesNotExist());
      }

      @Test
      void join_key_회귀_slip_userId로_조회시_해당_사원_서명을_반환한다() throws Exception {
          // slip 의 createdBy/dispatcherUserId/inspectorUserId = Employee.id (P4 join key).
          UUID slipUserId = signedEmployee(); // = Employee.id

          mockMvc.perform(post("/internal/users/signatures")
                          .header("X-Internal-Token", TOKEN)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("""
                                  {"userIds":["%s"]}
                                  """.formatted(slipUserId)))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.data['%s'].signaturePngBase64".formatted(slipUserId)).isNotEmpty());
      }

      @Test
      void 빈_userIds는_빈_맵을_반환한다() throws Exception {
          mockMvc.perform(post("/internal/users/signatures")
                          .header("X-Internal-Token", TOKEN)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("""
                                  {"userIds":[]}
                                  """))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.data").isMap())
                  .andExpect(jsonPath("$.data").isEmpty());
      }

      @Test
      void X_Internal_Token_누락은_403() throws Exception {
          mockMvc.perform(post("/internal/users/signatures")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("""
                                  {"userIds":["%s"]}
                                  """.formatted(UUID.randomUUID())))
                  .andExpect(status().isForbidden());
      }

      @Test
      void X_Internal_Token_불일치는_401() throws Exception {
          mockMvc.perform(post("/internal/users/signatures")
                          .header("X-Internal-Token", "wrong-token")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("""
                                  {"userIds":["%s"]}
                                  """.formatted(UUID.randomUUID())))
                  .andExpect(status().isUnauthorized());
      }
  }
  ```

- [ ] **Step 2: 실행 → FAIL.**
  ```
  ./gradlew :services:user-service:test --tests "com.samhanair.logis.user.it.InternalUserSignatureBatchControllerIT"
  ```
  기대: 핸들러 미존재 → `/internal/users/signatures` POST 404 (또는 import 부재 컴파일 실패). Docker 미가용 시 skip.

- [ ] **Step 3: InternalUserController 에 배치 엔드포인트 추가.** import 추가:
  ```java
  import com.samhanair.logis.user.service.EmployeeSignatureService;
  import com.samhanair.logis.user.web.dto.EmployeeSignatureDto;
  import com.samhanair.logis.user.web.dto.InternalSignatureBatchRequest;
  ```
  `@RequiredArgsConstructor` final 필드 추가(기존 `employeeRepository` 옆):
  ```java
      private final EmployeeSignatureService signatureService;
  ```
  `displayNames(...)` 메서드(`:199-216`) 다음, 클래스 닫기 전에 추가:
  ```java
      /**
       * 사원 서명 다건 조회 — C1a. slip-service 가 출고전표 결재란(작성자/출고인/검수인) 인감을
       * enrich 할 때 dispatcher/inspector/owner userId 의 서명을 한 번의 RPC 로 해석한다.
       *
       * <p>join key = {@code Employee.id} = slip 의 createdBy/dispatcherUserId/inspectorUserId (P4).
       * display-names/verify-bulk 배치 패턴 미러 — {@code findAllByIdIn}. 미등록 사원은 맵에서 생략한다.
       *
       * @param req 조회 대상 user UUID 목록
       * @return 존재·등록 사원의 {@code userId -> EmployeeSignatureDto} 매핑
       */
      @PostMapping("/signatures")
      @PreAuthorize("hasRole('MASTER')")
      public ApiResponse<Map<UUID, EmployeeSignatureDto>> signatures(
              @Valid @RequestBody InternalSignatureBatchRequest req) {
          return ApiResponse.ok(signatureService.resolveSignatures(req.userIds()));
      }
  ```
  > `Map`/`UUID` import 는 기존 파일에 이미 존재(`InternalUserController.java:20,22`).

- [ ] **Step 4: 실행 → PASS.**
  ```
  ./gradlew :services:user-service:test --tests "com.samhanair.logis.user.it.InternalUserSignatureBatchControllerIT"
  ```
  기대: BUILD SUCCESSFUL, 5 tests passed (Docker 가용 시).

- [ ] **Step 5: 변경 모듈 전체 test 완주 + 최종 회귀.**
  ```
  ./gradlew :services:user-service:test
  ```
  기대: BUILD SUCCESSFUL (전 패키지). gradlew 실행 비트 ([feedback_gradlew_exec_bit]):
  ```
  git update-index --chmod=+x gradlew
  ```

- [ ] **Step 6: commit + push + 조기 PR.** ([feedback_open_pr_early])
  ```
  git add services/user-service/src/main/java/com/samhanair/logis/user/web/InternalUserController.java \
          services/user-service/src/test/java/com/samhanair/logis/user/it/InternalUserSignatureBatchControllerIT.java
  git commit -F <커밋메시지파일>
  git push -u origin feat/employee-signature-c1a
  ```
  메시지: `feat(user): POST /internal/users/signatures 배치 조회 + join-key 회귀 IT (C1a)`

  PR 본문에 명시: 연관 에픽 = 사원 서명 등록 spec(`docs/superpowers/specs/2026-06-21-employee-signature-stamp-design.md`), 다음 슬라이스 = C1b(핸드오프 토큰 V11)·C3(slip enrichment, 본 PR `/internal/users/signatures` 응답 DTO `EmployeeSignatureDto{signaturePngBase64, signedAt}` 고정 전제). PR 발행 즉시 `gh pr checks --watch` 자동 모니터링 ([feedback_pr_ci_monitoring]). Docker 실 QA = AdminUserSignatureControllerIT/InternalUserSignatureBatchControllerIT 실 Testcontainers 결과 + Linux CI green 스크린샷 인라인 첨부 ([feedback_qa_docker_real_test], [feedback_early_pr_docker_qa_screenshots]).
", "slice":"C1a"}