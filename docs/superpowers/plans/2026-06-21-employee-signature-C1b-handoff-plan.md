> **슬라이스 플랜** — 에픽 인덱스: [2026-06-21-employee-signature-stamp-plan.md](2026-06-21-employee-signature-stamp-plan.md) (Global Constraints · 공유 계약 · 실행 방식 · 구현시점 확인항목). 본 파일 = 단일 슬라이스 = 1 PR. Step 은 `- [ ]` 체크박스로 추적.

## Slice C1b: 핸드오프 토큰 · 공개 인증우회 표면 (user-service BE + gateway)

**PR boundary:** 이 슬라이스 = PR 1개. `feat/signature-c1b-handoff-token` 브랜치. 조기 PR([[feedback_open_pr_early]]) — Task C1b.1 첫 push 직후 PR open, 이후 Task를 누적 커밋. **선행 의존**: 본 슬라이스는 C1a가 머지한 `Employee.registerSignature(byte[], String, SignatureChannel)` 도메인 메서드 + `SignatureChannel{MOBILE_CANVAS,UPLOAD}` enum + `EmployeeSignatureAudit`(action RECORD/INVALIDATE) + `EmployeeSignatureService.PNG_MAX_BYTES`/PNG·hash 검증 헬퍼를 **재사용**한다. C1a 머지 전이면 C1b는 시작하지 않는다(본 plan 9절 의존도). **보안 표면 슬라이스** — 모든 Task의 테스트는 토큰 위협모델(만료·재사용·위조·충돌·rate)에 집중한다.

검증된 코드 앵커(읽음):
- `services/api-gateway/src/main/resources/application.yml:77-86` — `slip-service-public`(`/api/public/**` → slip-service, `StripInboundIdentityHeaders` + `StripPrefix=1`, JWT 필터 없음). **새 user-service 공개 라우트는 이 라우트보다 먼저 선언**(더 구체 경로 `/api/public/employee-signatures/**` 우선 매칭).
- `services/slip-service/.../delivery/web/PublicSlipController.java` — 공개 컨트롤러 패턴(`@RequestMapping("/public")`, no-auth, `ApiResponse` 래퍼, CONFLICT→410 GONE 매핑).
- `services/user-service/.../config/SecurityConfig.java` — `/actuator/**` permitAll + `/internal/**` X-Internal-Token 게이트 + `anyRequest().authenticated()`. **`/public/**` permitAll 라인을 신규 추가**.
- `services/user-service/.../web/InternalUserController.java` — `@PreAuthorize("hasRole('MASTER')")` + `findAllByIdIn` 배치 패턴.
- `services/user-service/.../web/AdminUserController.java` — `@RequestMapping("/api/v1/admin/users")`, `@RequireDepartment(EXECUTIVE_OFFICE)` + `@RequirePermission(page="admin.users", action=...)`, `CALLER_HEADER="X-User-Id"`, `parseCaller(...)`.
- `services/slip-service/.../delivery/domain/DeliveryBatch.java:53,203-207` — `SecureRandom RNG` + `Base64.getUrlEncoder().withoutPadding()` base64url 48바이트(=64자) 토큰 생성, `isExpired()` = `LocalDateTime.now().isAfter(tokenExpiresAt)`.
- `services/user-service/.../it/AbstractPostgresIT.java` — 싱글턴 Testcontainers Postgres + `app.security.internal.token=test-internal-token` + `DockerAvailableCondition` skip. `InternalUserByNameControllerIT.java` — MockMvc IT + `@MockBean DynamicPermissionClient`/`AuthClient` 패턴.
- `shared/common/.../dto/ApiResponse.java` — `ok(T)` / `fail(ErrorCode, String)`, 필드 `success/code/message/data/timestamp`.
- `shared/common/.../exception/{ErrorCode,BusinessException}.java` — `NOT_FOUND(404)`, `CONFLICT(409)`, `INVALID_INPUT(400)`, `GONE` enum 없음(410은 컨트롤러가 `HttpStatus.GONE`로 직접 매핑).
- `shared/security/.../InternalTokenFilter.java:36-37` — `X-Internal-Token` 헤더, `INTERNAL_PRINCIPAL="system-internal"`.

**Flyway 버전 규칙**: user-service 최신 = `V9`. C1a가 `V10`(employees 4컬럼 + `employee_signature_audit`) 사용 → **C1b = `V11__add_employee_signature_handoff_token.sql`**. C1a 머지 PR의 실제 버전을 push 전 `ls db/migration` 으로 재확인하고, 충돌 시 그 다음 번호로 rebase(적용 후 불변 [[feedback_applied_migration_immutable]]).

---

### Task C1b.1: 핸드오프 토큰 엔티티 + Flyway 테이블 + 리포지토리

**Files:**
- create `services/user-service/src/main/java/com/samhanair/logis/user/domain/EmployeeSignatureHandoffToken.java`
- create `services/user-service/src/main/java/com/samhanair/logis/user/repository/EmployeeSignatureHandoffTokenRepository.java`
- create `services/user-service/src/main/resources/db/migration/V11__add_employee_signature_handoff_token.sql`
- create `services/user-service/src/test/java/com/samhanair/logis/user/it/EmployeeSignatureHandoffTokenIT.java`
- modify `services/user-service/src/test/java/com/samhanair/logis/user/it/AbstractPostgresIT.java` (변경 없음 — 재사용만; 신규 import 불요)

**Interfaces:**
- Produces 도메인: `EmployeeSignatureHandoffToken` — 정적 팩토리 `static EmployeeSignatureHandoffToken issue(UUID employeeId, String actorUserId)` (token=SecureRandom base64url 64자, expiresAt=now+10분), 도메인 메서드 `void markUsed()` (이미 사용됨이면 409 CONFLICT), `boolean isExpired()`, `boolean isUsed()`, `@Getter` 필드 `UUID id / UUID employeeId / String token / LocalDateTime expiresAt / LocalDateTime usedAt / String actorUserId`. TTL 상수 `TOKEN_TTL_MINUTES=10`.
- Produces 리포지토리: `Optional<EmployeeSignatureHandoffToken> findByToken(String token)`, `List<EmployeeSignatureHandoffToken> findAllByEmployeeIdAndUsedAtIsNull(UUID employeeId)` (재발급 시 동일 사원 미사용 토큰 무효화용).
- Consumes: `com.samhanair.logis.common.entity.BaseEntity`(7 audit), `com.samhanair.logis.common.exception.{BusinessException,ErrorCode}`.

- [ ] **Step 1: 실패 테스트 작성 — Flyway 적용 + 엔티티 매핑 + 토큰 발급/만료/사용 라이프사이클.** `EmployeeSignatureHandoffTokenIT.java` 작성. C1a가 만든 `Department`/`Employee` 픽스처 패턴을 `InternalUserByNameControllerIT`에서 그대로 차용.

```java
package com.samhanair.logis.user.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.UserServiceApplication;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.domain.EmployeeSignatureHandoffToken;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.EmployeeSignatureHandoffTokenRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

/** 핸드오프 토큰 엔티티 + Flyway(V11) + 라이프사이클(발급/만료/사용/무효화) IT. */
@SpringBootTest(classes = UserServiceApplication.class)
class EmployeeSignatureHandoffTokenIT extends AbstractPostgresIT {

    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeSignatureHandoffTokenRepository tokenRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department dept = departmentRepository.findByCode("C1B_IT")
                .orElseGet(() -> departmentRepository.save(Department.create("C1B_IT", "C1b 테스트팀", 901)));
        employee = employeeRepository.save(Employee.create(
                UUID.randomUUID(), "c1b-" + UUID.randomUUID(), "핸드오프대상", "사원",
                Role.SALES, dept, false, LocalDate.of(2026, 1, 1), null, null));
    }

    @Test
    void 발급_토큰은_64자_base64url_미사용_10분후만료() {
        EmployeeSignatureHandoffToken token =
                tokenRepository.save(EmployeeSignatureHandoffToken.issue(employee.getId(), "actor-1"));

        assertThat(token.getToken()).hasSize(64);
        assertThat(token.getToken()).matches("[A-Za-z0-9_-]{64}");
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.isUsed()).isFalse();
        assertThat(token.isExpired()).isFalse();
        assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(9));
        assertThat(token.getExpiresAt()).isBefore(LocalDateTime.now().plusMinutes(11));
    }

    @Test
    void markUsed_는_usedAt_세팅_isUsed_true() {
        EmployeeSignatureHandoffToken token =
                tokenRepository.save(EmployeeSignatureHandoffToken.issue(employee.getId(), "actor-1"));
        token.markUsed();
        assertThat(token.getUsedAt()).isNotNull();
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void 이미_사용된_토큰_재사용_409_CONFLICT() {
        EmployeeSignatureHandoffToken token =
                tokenRepository.save(EmployeeSignatureHandoffToken.issue(employee.getId(), "actor-1"));
        token.markUsed();
        assertThatThrownBy(token::markUsed)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 사용");
    }

    @Test
    void 만료시각_과거이면_isExpired_true() {
        EmployeeSignatureHandoffToken token =
                EmployeeSignatureHandoffToken.issue(employee.getId(), "actor-1");
        ReflectionTestUtils.setField(token, "expiresAt", LocalDateTime.now().minusMinutes(1));
        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void findAllByEmployeeIdAndUsedAtIsNull_은_미사용토큰만() {
        EmployeeSignatureHandoffToken active =
                tokenRepository.save(EmployeeSignatureHandoffToken.issue(employee.getId(), "actor-1"));
        EmployeeSignatureHandoffToken used =
                tokenRepository.save(EmployeeSignatureHandoffToken.issue(employee.getId(), "actor-1"));
        used.markUsed();
        tokenRepository.save(used);

        var open = tokenRepository.findAllByEmployeeIdAndUsedAtIsNull(employee.getId());
        assertThat(open).extracting(EmployeeSignatureHandoffToken::getId).contains(active.getId());
        assertThat(open).extracting(EmployeeSignatureHandoffToken::getId).doesNotContain(used.getId());
    }
}
```

- [ ] **Step 2: 실행 → FAIL.** `cmd /c "gradlew.bat :services:user-service:test --tests *EmployeeSignatureHandoffTokenIT"` (Windows; 한글 path JDK17 `test` 트랩 시 [[feedback_korean_path_jdk]] 회피로 CI 위임). **기대 FAIL**: `EmployeeSignatureHandoffToken` / 리포지토리 미존재 → 컴파일 에러. (Docker 미가용이면 IT는 skip — Step 4의 fresh Postgres probe가 Flyway를 별도 검증.)

- [ ] **Step 3: 엔티티 + 리포지토리 + Flyway 구현.**

`EmployeeSignatureHandoffToken.java` — `DeliveryBatch` 토큰 생성 패턴(`SecureRandom` 48바이트 base64url) + `Slip`/`DeliveryBatch`의 `isExpired()` 패턴 미러:
```java
package com.samhanair.logis.user.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 사원 서명 모바일 핸드오프 토큰 (slice C1b · spec §4.4).
 *
 * <p>관리자 desktop 이 "모바일로 그리기" 발급 시 1개 생성된다. 사원 폰이 이 토큰으로
 * 공개 제출 endpoint({@code POST /api/public/employee-signatures/{token}}) 에 1회 접근한다.
 *
 * <p>보안 속성:
 * <ul>
 *   <li>token = {@link SecureRandom} 48바이트 → base64url 64자 (UUID 와 다른 형식 —
 *       UUID 비공개 가드 무관, slip {@code DeliveryBatch} 패턴 미러).</li>
 *   <li>TTL = {@value #TOKEN_TTL_MINUTES} 분 ({@link #expiresAt}).</li>
 *   <li>1회용 — {@link #usedAt} 소진 후 재사용 불가 (재제출 409).</li>
 *   <li>재발급 시 서비스가 동일 사원 미사용 토큰을 soft-delete 무효화 (1슬롯 경합 회피).</li>
 * </ul>
 */
@Entity
@Getter
@Table(name = "employee_signature_handoff_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class EmployeeSignatureHandoffToken extends BaseEntity {

    /** 토큰 TTL — 10분 (spec §4.4). */
    public static final int TOKEN_TTL_MINUTES = 10;

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 48; // base64url(48 bytes) = 64자

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "token", nullable = false, length = 64, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "actor_user_id", length = 50)
    private String actorUserId;

    private EmployeeSignatureHandoffToken(UUID employeeId, String token,
                                          LocalDateTime expiresAt, String actorUserId) {
        this.employeeId = employeeId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.actorUserId = actorUserId;
    }

    /**
     * 신규 토큰 발급 — base64url 64자 + now+10분 만료.
     *
     * @param employeeId 서명 대상 사원 (Employee.id = canonical user UUID)
     * @param actorUserId 발급한 관리자 user-id (X-User-Id, 감사용, nullable)
     */
    public static EmployeeSignatureHandoffToken issue(UUID employeeId, String actorUserId) {
        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId 는 필수입니다");
        }
        return new EmployeeSignatureHandoffToken(
                employeeId, generateToken(),
                LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES), actorUserId);
    }

    /**
     * 토큰 소진 — 공개 제출 성공 직후 호출. 이미 사용된 토큰이면 409 CONFLICT.
     *
     * @throws BusinessException(CONFLICT) 이미 사용된 토큰 재소진 시도
     */
    public void markUsed() {
        if (this.usedAt != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용된 토큰입니다");
        }
        this.usedAt = LocalDateTime.now();
    }

    /** 만료 여부 — 공개 제출 진입 시 검증. */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    /** 사용 여부 — 공개 제출 진입 시 검증. */
    public boolean isUsed() {
        return this.usedAt != null;
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

`EmployeeSignatureHandoffTokenRepository.java`:
```java
package com.samhanair.logis.user.repository;

import com.samhanair.logis.user.domain.EmployeeSignatureHandoffToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 핸드오프 토큰 lookup — soft-delete 는 @SQLRestriction 으로 엔티티 레벨 강제. */
public interface EmployeeSignatureHandoffTokenRepository
        extends JpaRepository<EmployeeSignatureHandoffToken, UUID> {

    /** 공개 제출 토큰 검증 — base64url 토큰 단건 lookup. */
    Optional<EmployeeSignatureHandoffToken> findByToken(String token);

    /** 재발급 시 동일 사원 미사용 토큰 무효화 대상 조회. */
    List<EmployeeSignatureHandoffToken> findAllByEmployeeIdAndUsedAtIsNull(UUID employeeId);
}
```

`V11__add_employee_signature_handoff_token.sql` — slip `V5` 테이블 컨벤션(VARCHAR(64) UNIQUE token, BaseEntity 7 audit) 미러:
```sql
-- V11__add_employee_signature_handoff_token.sql
-- user-service — 사원 서명 모바일 핸드오프 토큰 (slice C1b · spec §4.4).
--
-- 관리자 desktop 이 "모바일로 그리기" 발급 → 1회용 토큰 → 사원 폰 공개 제출.
-- TTL=10분 (서비스 레이어 now+10분 발급, 진입 시 expires_at 비교 검증).
-- token = SecureRandom 48바이트 → base64url 64자 (slip delivery_batches.batch_token 패턴).
--
-- 컬럼 컨벤션 (slip V5 계승):
--   * token VARCHAR(64) — partial 아닌 전체 UNIQUE (NULL 미발생, NOT NULL).
--   * used_at TIMESTAMP NULL — 1회용 소진 마커.
--   * BaseEntity 7 audit 컬럼 (created/modified/deleted + is_deleted).

CREATE TABLE employee_signature_handoff_token (
    id            UUID         PRIMARY KEY,
    employee_id   UUID         NOT NULL,
    token         VARCHAR(64)  NOT NULL,
    expires_at    TIMESTAMP    NOT NULL,
    used_at       TIMESTAMP,
    actor_user_id VARCHAR(50),

    -- BaseEntity 7 audit (V1 컨벤션 그대로)
    created_at    TIMESTAMP    NOT NULL,
    created_by    VARCHAR(50)  NOT NULL,
    modified_at   TIMESTAMP,
    modified_by   VARCHAR(50),
    deleted_at    TIMESTAMP,
    deleted_by    VARCHAR(50),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 공개 제출 토큰 lookup 유일성 — soft-delete 무효화 후 같은 token 재발급은 없으므로 전체 UNIQUE.
CREATE UNIQUE INDEX uk_emp_sig_handoff_token
    ON employee_signature_handoff_token (token);

-- 재발급 시 동일 사원 미사용 토큰 무효화 lookup 가속.
CREATE INDEX ix_emp_sig_handoff_employee_open
    ON employee_signature_handoff_token (employee_id)
    WHERE used_at IS NULL AND is_deleted = FALSE;
```

- [ ] **Step 4: fresh Postgres probe — Flyway 직접 적용 검증** ([[feedback_migration_fresh_postgres_probe]], Windows Testcontainers skip 이 syntax error 가리는 전례). Docker 기동 후:
```bash
docker run -d --name v11probe -e POSTGRES_PASSWORD=pw -e POSTGRES_DB=user_db -p 55432:5432 postgres:16-alpine
# 컨테이너 ready 까지 pg_isready 폴링
until docker exec v11probe pg_isready -U postgres; do :; done
# C1a 까지의 마이그레이션을 순서대로 적용한 위에서 V11 만 검증 (BaseEntity audit 컬럼 의존 없음 — 독립 CREATE TABLE)
docker cp services/user-service/src/main/resources/db/migration/V11__add_employee_signature_handoff_token.sql v11probe:/tmp/V11.sql
docker exec v11probe sh -c 'psql -v ON_ERROR_STOP=1 -U postgres -d user_db -f /tmp/V11.sql'
docker exec v11probe psql -U postgres -d user_db -c '\d employee_signature_handoff_token'
docker rm -f v11probe
```
**기대 PASS**: `CREATE TABLE` + 2 INDEX 무에러, `\d` 출력에 `token | character varying(64)` + `uk_emp_sig_handoff_token UNIQUE` 표시. (한글 path Git Bash UTF-8 깨짐 회피로 `docker cp` + 파일 적용 — inline `-c` 금지.)

- [ ] **Step 5: IT 실행 → PASS.** `cmd /c "gradlew.bat :services:user-service:test --tests *EmployeeSignatureHandoffTokenIT"`. **기대 PASS**(Docker 가용 시 5/5; 미가용 시 skip + Step 4 probe 가 Flyway 보증). 변경 모듈 전체 test 완주([[feedback_changed_module_full_test_before_push]]): `cmd /c "gradlew.bat :services:user-service:test"`.

- [ ] **Step 6: 커밋.**
```bash
git add services/user-service/src/main/java/com/samhanair/logis/user/domain/EmployeeSignatureHandoffToken.java \
        services/user-service/src/main/java/com/samhanair/logis/user/repository/EmployeeSignatureHandoffTokenRepository.java \
        services/user-service/src/main/resources/db/migration/V11__add_employee_signature_handoff_token.sql \
        services/user-service/src/test/java/com/samhanair/logis/user/it/EmployeeSignatureHandoffTokenIT.java
git commit -F - <<'EOF'
feat(user): 사원 서명 핸드오프 토큰 엔티티 + V11 테이블 (slice C1b)

- EmployeeSignatureHandoffToken: SecureRandom base64url 64자, TTL 10분, 1회용(usedAt 소진)
- markUsed() 재소진 시 409 CONFLICT, isExpired/isUsed 진입 가드
- 리포지토리 findByToken / findAllByEmployeeIdAndUsedAtIsNull (재발급 무효화용)
- V11 Flyway: token UNIQUE + 미사용 partial index, BaseEntity 7 audit
- fresh Postgres probe 로 V11 직접 적용 검증
EOF
```

---

### Task C1b.2: 토큰 발급/상태 admin 엔드포인트 (AdminUserController + EmployeeSignatureHandoffService)

**Files:**
- create `services/user-service/src/main/java/com/samhanair/logis/user/service/EmployeeSignatureHandoffService.java`
- create `services/user-service/src/main/java/com/samhanair/logis/user/web/dto/HandoffTokenResponse.java`
- create `services/user-service/src/main/java/com/samhanair/logis/user/web/dto/HandoffStatusResponse.java`
- modify `services/user-service/src/main/java/com/samhanair/logis/user/web/AdminUserController.java`
- create `services/user-service/src/test/java/com/samhanair/logis/user/it/HandoffTokenAdminControllerIT.java`

**Interfaces:**
- Produces 엔드포인트 (contract 글자 그대로):
  - `POST /api/v1/admin/users/{id}/signature/handoff-token` → 200 `ApiResponse<HandoffTokenResponse>` where `HandoffTokenResponse{ String token; String qrUrl; String expiresAt(ISO) }`. `@RequirePermission(page="admin.users", action=UPDATE)` + `@RequireDepartment(EXECUTIVE_OFFICE)`.
  - `GET /api/v1/admin/users/{id}/signature/handoff/{token}/status` → 200 `ApiResponse<HandoffStatusResponse>` where `HandoffStatusResponse{ boolean used; boolean expired }`. `@RequirePermission(page="admin.users", action=VIEW)` + `@RequireDepartment(EXECUTIVE_OFFICE)`.
- Produces 서비스: `HandoffTokenResponse EmployeeSignatureHandoffService.issueToken(UUID employeeId, String actorUserId)` (사원 존재 검증→미사용 토큰 soft-delete 무효화→신규 발급), `HandoffStatusResponse EmployeeSignatureHandoffService.status(UUID employeeId, String token)`.
- Consumes: `EmployeeRepository.findById`(사원 존재), `EmployeeSignatureHandoffTokenRepository`(C1b.1), `AdminUserController.CALLER_HEADER`/`parseCaller`(기존).
- qrUrl base: `app.signature.public-base-url` (application.yml, 기본 `http://localhost:8080`). 모바일 공개 웹앱 origin(C2 DevOps 확정) 주입점.

- [ ] **Step 1: 실패 테스트 작성** — `HandoffTokenAdminControllerIT.java`. `@MockBean DynamicPermissionClient` 를 `anyRequest` 통과(권한 게이트는 별도 슬라이스에서 검증 — 본 IT 는 도메인 동작 집중)하도록 stub. `X-User-Id` 헤더 + `@RequireDepartment` 통과를 위한 EXECUTIVE_OFFICE 부서/마스터 픽스처는 `HrAuthorizationIT` 패턴 차용.

```java
package com.samhanair.logis.user.it;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import com.samhanair.logis.user.UserServiceApplication;
import com.samhanair.logis.user.client.AuthClient;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.EmployeeSignatureHandoffTokenRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/** 핸드오프 토큰 발급/상태 admin 엔드포인트 IT (slice C1b). */
@SpringBootTest(classes = UserServiceApplication.class)
@AutoConfigureMockMvc
class HandoffTokenAdminControllerIT extends AbstractPostgresIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeSignatureHandoffTokenRepository tokenRepository;

    @MockBean private DynamicPermissionClient dynamicPermissionClient;
    @MockBean private AuthClient authClient;

    private Employee employee;
    private String callerId;

    @BeforeEach
    void setUp() {
        when(dynamicPermissionClient.hasPermission(any(), any(), any())).thenReturn(true);
        Department dept = departmentRepository.findByCode("대표실")
                .orElseGet(() -> departmentRepository.save(Department.create("대표실", "대표실", 1)));
        callerId = UUID.randomUUID().toString();
        employee = employeeRepository.save(Employee.create(
                UUID.randomUUID(), "handoff-" + UUID.randomUUID(), "핸드오프대상", "사원",
                Role.SALES, dept, false, LocalDate.of(2026, 1, 1), null, null));
    }

    @Test
    void 토큰_발급_200_token64자_qrUrl_expiresAt() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{id}/signature/handoff-token", employee.getId())
                        .header("X-User-Id", callerId)
                        .header("X-User-Role", "MASTER")
                        .header("X-Department", "EXECUTIVE_OFFICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.qrUrl").value(org.hamcrest.Matchers.containsString(
                        "/api/public/employee-signatures/")))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }

    @Test
    void 미존재_사원_토큰_발급_404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{id}/signature/handoff-token", UUID.randomUUID())
                        .header("X-User-Id", callerId)
                        .header("X-User-Role", "MASTER")
                        .header("X-Department", "EXECUTIVE_OFFICE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 재발급시_직전_미사용토큰_무효화() throws Exception {
        // 1차 발급
        String first = postTokenAndExtract();
        // 2차 발급 — 직전 미사용 토큰은 soft-delete 무효화
        String second = postTokenAndExtract();

        // 직전 토큰 status 조회 → soft-delete 되어 미발견 404
        mockMvc.perform(get("/api/v1/admin/users/{id}/signature/handoff/{token}/status",
                        employee.getId(), first)
                        .header("X-User-Id", callerId)
                        .header("X-User-Role", "MASTER")
                        .header("X-Department", "EXECUTIVE_OFFICE"))
                .andExpect(status().isNotFound());
        // 신규 토큰은 미사용/미만료
        mockMvc.perform(get("/api/v1/admin/users/{id}/signature/handoff/{token}/status",
                        employee.getId(), second)
                        .header("X-User-Id", callerId)
                        .header("X-User-Role", "MASTER")
                        .header("X-Department", "EXECUTIVE_OFFICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used").value(false))
                .andExpect(jsonPath("$.data.expired").value(false));
    }

    @Test
    void 상태조회_미발견_토큰_404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/{id}/signature/handoff/{token}/status",
                        employee.getId(), "nonexistent-token-xyz")
                        .header("X-User-Id", callerId)
                        .header("X-User-Role", "MASTER")
                        .header("X-Department", "EXECUTIVE_OFFICE"))
                .andExpect(status().isNotFound());
    }

    private String postTokenAndExtract() throws Exception {
        var result = mockMvc.perform(
                        post("/api/v1/admin/users/{id}/signature/handoff-token", employee.getId())
                                .header("X-User-Id", callerId)
                                .header("X-User-Role", "MASTER")
                                .header("X-Department", "EXECUTIVE_OFFICE"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.token");
    }
}
```
(주의: `@MockBean DynamicPermissionClient`의 실제 메서드 시그니처는 C1a IT 가 사용한 것과 일치시킨다 — `InternalUserByNameControllerIT`처럼 단순 `@MockBean`만으로 충분하면 `when(...)` 라인 제거. `@RequireDepartment` 헤더 키(`X-Department`)는 `HrAuthorizationIT` 실제 값으로 정렬.)

- [ ] **Step 2: 실행 → FAIL.** `cmd /c "gradlew.bat :services:user-service:test --tests *HandoffTokenAdminControllerIT"`. **기대 FAIL**: 엔드포인트 미존재 → 404(매핑 없음) 또는 컴파일 에러(DTO/서비스 미존재).

- [ ] **Step 3: DTO + 서비스 + 컨트롤러 구현.**

`HandoffTokenResponse.java`:
```java
package com.samhanair.logis.user.web.dto;

/**
 * 핸드오프 토큰 발급 응답 (slice C1b · contract).
 *
 * @param token base64url 64자 1회용 토큰
 * @param qrUrl 모바일 공개 제출 URL (QR/복사링크용, 실 origin + /api/public/employee-signatures/{token})
 * @param expiresAt ISO-8601 만료 시각
 */
public record HandoffTokenResponse(String token, String qrUrl, String expiresAt) {}
```

`HandoffStatusResponse.java`:
```java
package com.samhanair.logis.user.web.dto;

/**
 * 핸드오프 토큰 상태 응답 — desktop 폴링용 (slice C1b · contract).
 *
 * @param used 토큰 소진(제출 완료) 여부
 * @param expired 만료 여부
 */
public record HandoffStatusResponse(boolean used, boolean expired) {}
```

`EmployeeSignatureHandoffService.java`:
```java
package com.samhanair.logis.user.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.user.domain.EmployeeSignatureHandoffToken;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.EmployeeSignatureHandoffTokenRepository;
import com.samhanair.logis.user.web.dto.HandoffStatusResponse;
import com.samhanair.logis.user.web.dto.HandoffTokenResponse;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사원 서명 모바일 핸드오프 토큰 서비스 (slice C1b · spec §5.2).
 *
 * <p>발급: 사원 존재 검증 → 동일 사원 미사용 토큰 soft-delete 무효화 → 신규 토큰 발급.
 * 상태: 토큰 단건 lookup → {used, expired} 폴링 응답.
 *
 * <p>qrUrl base 는 {@code app.signature.public-base-url} — 모바일 공개 웹앱 origin
 * (C2 DevOps 확정). 게이트웨이 공개 라우트 {@code /api/public/employee-signatures/{token}} 결합.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeSignatureHandoffService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSignatureHandoffTokenRepository tokenRepository;

    @Value("${app.signature.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    /**
     * 토큰 발급 — 동일 사원 미사용 토큰 무효화 후 신규 1개 발급.
     *
     * @param employeeId 서명 대상 사원
     * @param actorUserId 발급 관리자 user-id (감사용, nullable)
     * @return 발급 응답 (token / qrUrl / expiresAt ISO)
     * @throws BusinessException(NOT_FOUND) 사원 미존재
     */
    public HandoffTokenResponse issueToken(UUID employeeId, String actorUserId) {
        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "직원을 찾을 수 없습니다: " + employeeId));
        // 동일 사원 미사용 토큰 무효화 (soft-delete — @SQLRestriction 으로 이후 조회 제외)
        tokenRepository.findAllByEmployeeIdAndUsedAtIsNull(employeeId)
                .forEach(EmployeeSignatureHandoffToken::softDelete);
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employeeId, actorUserId));
        String qrUrl = publicBaseUrl + "/api/public/employee-signatures/" + token.getToken();
        return new HandoffTokenResponse(
                token.getToken(),
                qrUrl,
                token.getExpiresAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    /**
     * 토큰 상태 — desktop 폴링용. 토큰 미발견(또는 무효화) 시 404.
     *
     * @throws BusinessException(NOT_FOUND) 토큰 미발견/무효화
     */
    @Transactional(readOnly = true)
    public HandoffStatusResponse status(UUID employeeId, String token) {
        EmployeeSignatureHandoffToken found = tokenRepository.findByToken(token)
                .filter(t -> t.getEmployeeId().equals(employeeId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "토큰을 찾을 수 없습니다"));
        return new HandoffStatusResponse(found.isUsed(), found.isExpired());
    }
}
```
(주의: `EmployeeSignatureHandoffToken::softDelete` 는 `BaseEntity` 의 기존 soft-delete 메서드를 사용. `BaseEntity` 에 `softDelete()`가 없으면 `tokenRepository.delete(...)` 로 대체 — push 전 `BaseEntity.java` 메서드명 확인 후 정렬.)

`AdminUserController.java` 수정 — 기존 import/필드에 추가:
```java
// 필드 추가 (생성자 @RequiredArgsConstructor 자동 주입)
private final EmployeeSignatureHandoffService handoffService;

// 엔드포인트 2개 추가 (기존 패턴 정렬: @RequireDepartment + @RequirePermission + parseCaller)

/**
 * 모바일 핸드오프 토큰 발급 — "모바일로 그리기" (slice C1b · spec §5.2).
 *
 * <p>동일 사원 미사용 토큰 무효화 후 신규 1회용 토큰 발급. desktop 이 QR + 복사링크 표시.
 */
@PostMapping("/{id}/signature/handoff-token")
@RequireDepartment(Department.EXECUTIVE_OFFICE)
@RequirePermission(page = "admin.users", action = PermissionAction.UPDATE)
public ApiResponse<com.samhanair.logis.user.web.dto.HandoffTokenResponse> issueHandoffToken(
        @PathVariable UUID id,
        @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
    UUID caller = parseCaller(callerHeader);
    return ApiResponse.ok(handoffService.issueToken(id, caller == null ? null : caller.toString()));
}

/**
 * 핸드오프 토큰 상태 조회 — desktop 폴링 (2s, slice C1b · spec §5.2).
 */
@GetMapping("/{id}/signature/handoff/{token}/status")
@RequireDepartment(Department.EXECUTIVE_OFFICE)
@RequirePermission(page = "admin.users", action = PermissionAction.VIEW)
public ApiResponse<com.samhanair.logis.user.web.dto.HandoffStatusResponse> handoffStatus(
        @PathVariable UUID id,
        @PathVariable String token) {
    return ApiResponse.ok(handoffService.status(id, token));
}
```
(주의: 게이트웨이 `user-admin-v1`(`/api/v1/admin/users/**`, no-strip, JwtAuthentication) 라우트가 이미 본 컨트롤러 풀패스를 커버 — 신규 게이트웨이 라우트 불요. 발급/상태는 JWT 보호 경로.)

- [ ] **Step 4: 실행 → PASS.** `cmd /c "gradlew.bat :services:user-service:test --tests *HandoffTokenAdminControllerIT"`. **기대 PASS** 4/4. 발급 토큰 64자·qrUrl prefix·재발급 무효화·status 404 단언 통과.

- [ ] **Step 5: 커밋.**
```bash
git add services/user-service/src/main/java/com/samhanair/logis/user/service/EmployeeSignatureHandoffService.java \
        services/user-service/src/main/java/com/samhanair/logis/user/web/dto/HandoffTokenResponse.java \
        services/user-service/src/main/java/com/samhanair/logis/user/web/dto/HandoffStatusResponse.java \
        services/user-service/src/main/java/com/samhanair/logis/user/web/AdminUserController.java \
        services/user-service/src/test/java/com/samhanair/logis/user/it/HandoffTokenAdminControllerIT.java
git commit -F - <<'EOF'
feat(user): 핸드오프 토큰 발급/상태 admin 엔드포인트 (slice C1b)

- POST .../signature/handoff-token: 동일 사원 미사용 토큰 무효화 후 신규 발급, qrUrl+expiresAt
- GET .../signature/handoff/{token}/status: desktop 폴링용 {used, expired}
- admin.users UPDATE/VIEW 게이트 + EXECUTIVE_OFFICE 부서 가드 (기존 AdminUserController 정렬)
- qrUrl base = app.signature.public-base-url (모바일 공개 웹앱 origin 주입점)
EOF
```

---

### Task C1b.3: 공개 제출 엔드포인트 + 게이트웨이 라우트 + SecurityConfig permitAll (인증우회 표면)

**Files:**
- create `services/user-service/src/main/java/com/samhanair/logis/user/web/PublicEmployeeSignatureController.java`
- create `services/user-service/src/main/java/com/samhanair/logis/user/web/dto/PublicEmployeeSignatureRequest.java`
- modify `services/user-service/src/main/java/com/samhanair/logis/user/service/EmployeeSignatureHandoffService.java` (공개 제출 메서드 `submitPublic` 추가)
- modify `services/user-service/src/main/java/com/samhanair/logis/user/config/SecurityConfig.java` (`/public/**` permitAll + identity 헤더 fail-CLOSED)
- modify `services/api-gateway/src/main/resources/application.yml` (신규 `user-service-employee-signatures-public` 라우트)
- modify `services/user-service/src/main/resources/application.yml` (`app.signature.public-base-url` 기본값 명시)
- create `services/user-service/src/test/java/com/samhanair/logis/user/it/PublicEmployeeSignatureControllerIT.java`

**Interfaces:**
- Produces 엔드포인트 (contract 글자 그대로): `POST /api/public/employee-signatures/{token}` body `PublicEmployeeSignatureRequest{ String signaturePngBase64; String signatureHash }` → 200 `ApiResponse<Void>`. **NO-AUTH 토큰 게이트.** user-service 컨트롤러 매핑 = `@RequestMapping("/public/employee-signatures")` (게이트웨이 `StripPrefix=1` → `/public/employee-signatures/{token}`).
- Produces 게이트웨이 라우트: `id: user-service-employee-signatures-public`, `Path=/api/public/employee-signatures/**` → `lb://user-service`, filters `StripInboundIdentityHeaders` + `StripPrefix=1`, **JWT 필터 없음**, `slip-service-public`(`/api/public/**`)보다 **먼저 선언**.
- Produces 서비스: `void EmployeeSignatureHandoffService.submitPublic(String token, String signaturePngBase64, String signatureHash)` — 토큰 검증(미만료·미사용)→PNG 디코드+≤50KB+SHA-256 재검증→`employee.registerSignature(png, hash, MOBILE_CANVAS)`→audit RECORD→`token.markUsed()`.
- Consumes (C1a): `Employee.registerSignature(byte[], String, SignatureChannel)`, `SignatureChannel.MOBILE_CANVAS`, `EmployeeSignatureAudit.record(...)` + `EmployeeSignatureAuditRepository`, `EmployeeSignatureService.PNG_MAX_BYTES`(50*1024) 또는 동일 상수 재선언. Consumes(C1b.1): `EmployeeSignatureHandoffTokenRepository.findByToken`, `markUsed`.

- [ ] **Step 1: 실패 테스트 작성 — 위협모델 전수** (만료 거부·재사용 거부·위조 토큰 404·hash mismatch 400·≤50KB 초과 422·정상 등록+사원 서명 반영). `PublicEmployeeSignatureControllerIT.java`. **권한 헤더 없이** 호출(공개 경로) — SecurityConfig `/public/**` permitAll 검증.

```java
package com.samhanair.logis.user.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import com.samhanair.logis.user.UserServiceApplication;
import com.samhanair.logis.user.client.AuthClient;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.domain.EmployeeSignatureHandoffToken;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.EmployeeSignatureHandoffTokenRepository;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** 공개 제출 엔드포인트 위협모델 IT — 만료/재사용/위조/hash/크기 (slice C1b · spec §10). */
@SpringBootTest(classes = UserServiceApplication.class)
@AutoConfigureMockMvc
class PublicEmployeeSignatureControllerIT extends AbstractPostgresIT {

    // 1x1 투명 PNG (89 504E 470D ... magic-byte 포함 유효 PNG)
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeSignatureHandoffTokenRepository tokenRepository;

    @MockBean private DynamicPermissionClient dynamicPermissionClient;
    @MockBean private AuthClient authClient;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department dept = departmentRepository.findByCode("C1BPUB_IT")
                .orElseGet(() -> departmentRepository.save(Department.create("C1BPUB_IT", "공개제출 테스트팀", 902)));
        employee = employeeRepository.save(Employee.create(
                UUID.randomUUID(), "pub-" + UUID.randomUUID(), "공개서명대상", "사원",
                Role.SALES, dept, false, LocalDate.of(2026, 1, 1), null, null));
    }

    @Test
    void 정상_제출_200_사원_서명_반영_토큰_소진() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));

        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isOk());

        // 사원 서명 4필드 반영 (MOBILE_CANVAS) — join key Employee.id 로 조회 시 서명 존재
        Employee reloaded = employeeRepository.findById(employee.getId()).orElseThrow();
        assertThat((byte[]) ReflectionTestUtils.getField(reloaded, "signaturePng")).isNotNull();
        assertThat((String) ReflectionTestUtils.getField(reloaded, "signatureHash"))
                .isEqualToIgnoringCase(sha256Hex(PNG));
        // 토큰 소진
        EmployeeSignatureHandoffToken used = tokenRepository.findByToken(token.getToken()).orElseThrow();
        assertThat(used.isUsed()).isTrue();
    }

    @Test
    void 만료_토큰_제출_거부_410() throws Exception {
        EmployeeSignatureHandoffToken token =
                EmployeeSignatureHandoffToken.issue(employee.getId(), null);
        ReflectionTestUtils.setField(token, "expiresAt", LocalDateTime.now().minusMinutes(1));
        tokenRepository.save(token);

        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isGone());
    }

    @Test
    void 재사용_토큰_제출_거부_409() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        // 1차 제출 성공
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isOk());
        // 2차 제출 = 재사용 → 409
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isConflict());
    }

    @Test
    void 위조_미발견_토큰_404() throws Exception {
        mockMvc.perform(post("/public/employee-signatures/{token}", "forged-token-deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isNotFound());
    }

    @Test
    void hash_mismatch_400() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, "0".repeat(64))))
                .andExpect(status().isBadRequest());
        // 실패 시 토큰 미소진 (재시도 가능)
        assertThat(tokenRepository.findByToken(token.getToken()).orElseThrow().isUsed()).isFalse();
    }

    @Test
    void PNG_50KB_초과_422() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        byte[] big = new byte[50 * 1024 + 1];
        // 유효 PNG magic-byte 선두 8바이트 보존 후 크기만 초과
        System.arraycopy(PNG, 0, big, 0, Math.min(PNG.length, 8));
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(big, sha256Hex(big))))
                .andExpect(status().isUnprocessableEntity());
    }

    private String body(byte[] png, String hash) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "signaturePngBase64", Base64.getEncoder().encodeToString(png),
                "signatureHash", hash));
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
```
(주의: ≤50KB 초과의 기대 코드 = **422 UNPROCESSABLE_ENTITY** (spec §5.1 "초과 422"). hash mismatch = **400 INVALID_INPUT**. 만료 = **410 GONE**(컨트롤러 CONFLICT→GONE 매핑). 재사용 = **409 CONFLICT**(`markUsed` 또는 진입 가드). 위조 = **404 NOT_FOUND**.)

- [ ] **Step 2: 실행 → FAIL.** `cmd /c "gradlew.bat :services:user-service:test --tests *PublicEmployeeSignatureControllerIT"`. **기대 FAIL**: 컨트롤러 미존재(404 매핑 없음) + `/public/**` 비-permitAll → 403/401, 또는 컴파일 에러.

- [ ] **Step 3: DTO + 서비스 메서드 + 컨트롤러 + SecurityConfig + 게이트웨이 구현.**

`PublicEmployeeSignatureRequest.java`:
```java
package com.samhanair.logis.user.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 공개 모바일 서명 제출 요청 (slice C1b · contract · NO-AUTH 토큰 게이트).
 *
 * @param signaturePngBase64 canvas PNG (data URI 또는 raw base64)
 * @param signatureHash 클라 계산 SHA-256 hex (BE 재검증, 불일치 400)
 */
public record PublicEmployeeSignatureRequest(
        @NotBlank String signaturePngBase64,
        @NotBlank String signatureHash) {}
```

`EmployeeSignatureHandoffService.submitPublic(...)` 추가 (slip `SlipSignatureService.recordSignature` 디코드/hash/크기 가드 패턴 미러; PNG 헬퍼는 C1a `EmployeeSignatureService` 재사용 또는 동일 로직 복제):
```java
// 필드 추가
private final EmployeeSignatureAuditRepository auditRepository;
public static final int PNG_MAX_BYTES = 50 * 1024;

/**
 * 공개 모바일 서명 제출 — 토큰 게이트 (slice C1b · spec §5.2).
 *
 * <p>처리: 토큰 lookup(없으면 404) → 만료(410 컨트롤러 매핑) → 사용됨(409) →
 * PNG 디코드 + ≤50KB(초과 422) + SHA-256 재계산 + clientHash 비교(불일치 400) →
 * Employee.registerSignature(MOBILE_CANVAS) → audit RECORD → 토큰 markUsed 소진.
 *
 * @throws BusinessException(NOT_FOUND) 토큰 미발견/사원 미발견
 * @throws BusinessException(CONFLICT) 토큰 만료(컨트롤러 410 매핑) / 이미 사용
 * @throws BusinessException(INVALID_INPUT) hash mismatch / base64 디코드 실패
 * @throws BusinessException(UNPROCESSABLE_ENTITY) PNG 50KB 초과
 */
public void submitPublic(String token, String signaturePngBase64, String signatureHash) {
    EmployeeSignatureHandoffToken handoff = tokenRepository.findByToken(token)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "유효하지 않은 토큰입니다"));
    if (handoff.isExpired()) {
        throw new BusinessException(ErrorCode.CONFLICT, "토큰이 만료되었습니다");
    }
    if (handoff.isUsed()) {
        throw new BusinessException(ErrorCode.CONFLICT, "이미 사용된 토큰입니다");
    }
    Employee employee = employeeRepository.findById(handoff.getEmployeeId())
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원을 찾을 수 없습니다"));

    byte[] png = decodePng(signaturePngBase64);
    if (png.length > PNG_MAX_BYTES) {
        throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY,
                "서명 PNG 가 너무 큽니다 (" + png.length + " bytes, 최대 " + PNG_MAX_BYTES + ")");
    }
    String serverHash = sha256Hex(png);
    if (!serverHash.equalsIgnoreCase(signatureHash)) {
        throw new BusinessException(ErrorCode.INVALID_INPUT,
                "서명 무결성 검증 실패 — 클라이언트 hash 가 일치하지 않습니다");
    }

    employee.registerSignature(png, serverHash, SignatureChannel.MOBILE_CANVAS);
    auditRepository.save(EmployeeSignatureAudit.record(
            employee.getId(), serverHash, SignatureChannel.MOBILE_CANVAS, null));
    handoff.markUsed();
}

// private byte[] decodePng(String) / private String sha256Hex(byte[]) — SlipSignatureService 동일 로직 복제
//   (data URI 또는 raw base64 처리, 디코드 실패 시 INVALID_INPUT; SHA-256 hex 64자).
//   C1a EmployeeSignatureService 에 동일 헬퍼가 있으면 그 쪽으로 추출 후 재사용.
```
(주의: `EmployeeSignatureAudit.record(employeeId, hash, channel, actorUserId)` 의 정확 시그니처는 C1a 산출물에 맞춤 — actorUserId=null(공개 제출). `decodePng`/`sha256Hex`는 C1a `EmployeeSignatureService`로 추출되어 있으면 거기서 재사용([[feedback_defect_family_sweep_fix]] — 중복 구현 금지).)

`PublicEmployeeSignatureController.java` (slip `PublicSlipController` CONFLICT→410 매핑 패턴 미러):
```java
package com.samhanair.logis.user.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.user.service.EmployeeSignatureHandoffService;
import com.samhanair.logis.user.web.dto.PublicEmployeeSignatureRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사원 서명 공개 제출 endpoint — NO-AUTH 토큰 게이트 (slice C1b · spec §5.2 · §7).
 *
 * <p>게이트웨이 {@code /api/public/employee-signatures/**} → {@code StripPrefix=1} →
 * 본 컨트롤러 {@code /public/employee-signatures/{token}}. JwtAuthentication 필터 미적용 +
 * {@code StripInboundIdentityHeaders} 로 위조 identity 헤더 strip. user-service SecurityConfig
 * 의 {@code /public/**} permitAll + identity fail-CLOSED ([[feedback_identity_header_authz_antipattern]]).
 *
 * <p>토큰 만료 시 slip {@code PublicSlipController} 패턴대로 CONFLICT → 410 GONE 매핑.
 */
@RestController
@RequestMapping("/public/employee-signatures")
@RequiredArgsConstructor
public class PublicEmployeeSignatureController {

    private final EmployeeSignatureHandoffService handoffService;

    @PostMapping("/{token}")
    public ResponseEntity<ApiResponse<Void>> submit(
            @PathVariable String token,
            @Valid @RequestBody PublicEmployeeSignatureRequest request) {
        try {
            handoffService.submitPublic(token, request.signaturePngBase64(), request.signatureHash());
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (BusinessException ex) {
            // 토큰 만료 = CONFLICT → 410 GONE (slip PublicSlipController 정렬).
            // 이미 사용된 토큰은 그대로 409 (GlobalExceptionHandler CONFLICT 매핑) 보존.
            if (ex.getErrorCode() == ErrorCode.CONFLICT
                    && ex.getMessage() != null && ex.getMessage().contains("만료")) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(ApiResponse.fail(ErrorCode.CONFLICT, ex.getMessage()));
            }
            throw ex;
        }
    }
}
```

`SecurityConfig.java` (user-service) 수정 — `/actuator/**` 다음에 `/public/**` permitAll 추가 (slip-service SecurityConfig 정렬, identity fail-CLOSED 는 게이트웨이 `StripInboundIdentityHeaders` + permitAll 이 보장):
```java
.requestMatchers("/actuator/**").permitAll()
// slice C1b: 사원 서명 공개 제출 — NO-AUTH 토큰 게이트 (게이트웨이 StripInboundIdentityHeaders 로
// identity 헤더 strip, downstream 은 토큰만 신뢰 — X-User-* 미주입 fail-CLOSED)
.requestMatchers("/public/**").permitAll()
.requestMatchers("/internal/**").access(...)  // 기존 유지
```

`api-gateway/application.yml` 수정 — `slip-service-public`(line 79) **앞에** 신규 라우트 삽입 (더 구체 경로 우선):
```yaml
        # slice C1b — 사원 서명 공개 제출 (no auth, 토큰만 검증).
        # /api/public/** slip 라우트보다 먼저 선언 — 더 구체 경로 우선 매칭.
        # JwtAuthentication 미적용 + identity 헤더 strip (위조 차단).
        - id: user-service-employee-signatures-public
          uri: lb://user-service
          predicates:
            - Path=/api/public/employee-signatures/**
          filters:
            - StripInboundIdentityHeaders
            - StripPrefix=1

        # Slice B — 공개 모바일 endpoint (no auth, 토큰만 검증)  ← 기존
        - id: slip-service-public
          ...
```

`user-service/application.yml` 수정 — `app:` 블록에 base-url 추가:
```yaml
app:
  signature:
    # 모바일 공개 서명 웹앱 origin (C2 DevOps 확정 — 기본 게이트웨이 localhost). qrUrl 결합 base.
    public-base-url: ${SAMHAN_SIGNATURE_PUBLIC_BASE_URL:http://localhost:8080}
  security:
    internal:
      ...
```

- [ ] **Step 4: 실행 → PASS.** `cmd /c "gradlew.bat :services:user-service:test --tests *PublicEmployeeSignatureControllerIT"`. **기대 PASS** 6/6: 정상 200+서명반영+토큰소진 / 만료 410 / 재사용 409 / 위조 404 / hash 400+토큰미소진 / 50KB 422. + 변경 모듈 전체 `cmd /c "gradlew.bat :services:user-service:test"` 완주([[feedback_changed_module_full_test_before_push]]).

- [ ] **Step 5: 게이트웨이 라우트 순서 + SecurityConfig 정적 검증.** `cmd /c "gradlew.bat :services:api-gateway:compileJava"` (YAML 매핑 깨짐 없는지) + grep 으로 `user-service-employee-signatures-public` 가 `slip-service-public` 보다 앞 줄 번호인지 확인:
```bash
grep -n "id: user-service-employee-signatures-public\|id: slip-service-public" \
  services/api-gateway/src/main/resources/application.yml
```
**기대**: employee-signatures 라인 번호 < slip-service-public 라인 번호.

- [ ] **Step 6: 커밋.**
```bash
git add services/user-service/src/main/java/com/samhanair/logis/user/web/PublicEmployeeSignatureController.java \
        services/user-service/src/main/java/com/samhanair/logis/user/web/dto/PublicEmployeeSignatureRequest.java \
        services/user-service/src/main/java/com/samhanair/logis/user/service/EmployeeSignatureHandoffService.java \
        services/user-service/src/main/java/com/samhanair/logis/user/config/SecurityConfig.java \
        services/user-service/src/main/resources/application.yml \
        services/api-gateway/src/main/resources/application.yml \
        services/user-service/src/test/java/com/samhanair/logis/user/it/PublicEmployeeSignatureControllerIT.java
git commit -F - <<'EOF'
feat(user): 공개 서명 제출 + 게이트웨이 공개 라우트 (slice C1b · 인증우회 표면)

- POST /api/public/employee-signatures/{token}: NO-AUTH 토큰 게이트
  토큰 검증(미만료·미사용) → PNG ≤50KB + SHA-256 재검증 → registerSignature(MOBILE_CANVAS) → 토큰 소진
- 위협모델: 만료 410 / 재사용 409 / 위조 404 / hash mismatch 400 / 50KB 초과 422
- 게이트웨이 user-service-employee-signatures-public: /api/public/** slip 라우트보다 먼저 선언
  StripInboundIdentityHeaders + StripPrefix=1, JWT 필터 없음
- user-service SecurityConfig /public/** permitAll + identity fail-CLOSED
- app.signature.public-base-url (모바일 공개 웹앱 origin)
EOF
```

---

### Task C1b.4: 동시발급 경합 + 게이트웨이 인증우회 표면 회귀 가드 (보안 표면 마무리)

**Files:**
- modify `services/user-service/src/test/java/com/samhanair/logis/user/it/PublicEmployeeSignatureControllerIT.java` (동시발급/경합 케이스 추가)
- create `services/user-service/src/test/java/com/samhanair/logis/user/it/PublicSignatureSecurityGateIT.java`

**Interfaces:**
- Consumes: C1b.1~C1b.3 전 산출물. 신규 production 코드 없음 — **보안 표면 회귀 박제만**.
- Produces: 위협모델 회귀 테스트 (충돌·permitAll 경로 격리·identity 헤더 무시).

- [ ] **Step 1: 충돌(동시 발급→직전 토큰 무효화) + permitAll 격리 테스트 작성.** spec §10 "토큰 IT(만료·재사용 거부·재발급 무효화)" + 인증우회 표면 격리.

`PublicSignatureSecurityGateIT.java`:
```java
package com.samhanair.logis.user.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import com.samhanair.logis.user.UserServiceApplication;
import com.samhanair.logis.user.client.AuthClient;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.domain.EmployeeSignatureHandoffToken;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.EmployeeSignatureHandoffTokenRepository;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 공개 제출 인증우회 표면 회귀 가드 — permitAll 경로 격리 + 위조 identity 헤더 무시 (slice C1b). */
@SpringBootTest(classes = UserServiceApplication.class)
@AutoConfigureMockMvc
class PublicSignatureSecurityGateIT extends AbstractPostgresIT {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeSignatureHandoffTokenRepository tokenRepository;

    @MockBean private DynamicPermissionClient dynamicPermissionClient;
    @MockBean private AuthClient authClient;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department dept = departmentRepository.findByCode("C1BSEC_IT")
                .orElseGet(() -> departmentRepository.save(Department.create("C1BSEC_IT", "보안게이트 테스트팀", 903)));
        employee = employeeRepository.save(Employee.create(
                UUID.randomUUID(), "sec-" + UUID.randomUUID(), "보안게이트대상", "사원",
                Role.SALES, dept, false, LocalDate.of(2026, 1, 1), null, null));
    }

    @Test
    void 공개경로는_X_User_헤더_없이도_권한게이트_미적용_200() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        // X-User-Id / X-User-Role / X-Internal-Token 일절 없이 — /public/** permitAll 통과
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isOk());
    }

    @Test
    void 위조_identity_헤더_주입해도_공개경로_동작_무변동_200() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        // 게이트웨이 StripInboundIdentityHeaders 가 실 ingress 에서 제거하지만,
        // downstream 이 X-User-* 를 신뢰하지 않음(공개 토큰 게이트)을 박제 — 위조 헤더는 무의미.
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MASTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isOk());
    }

    private String body(byte[] png, String hash) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "signaturePngBase64", Base64.getEncoder().encodeToString(png),
                "signatureHash", hash));
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
```

`PublicEmployeeSignatureControllerIT.java` 에 충돌(재발급 무효화 후 구 토큰 제출 거부) 케이스 추가:
```java
    @Test
    void 재발급_무효화된_구토큰_제출_거부_404() throws Exception {
        EmployeeSignatureHandoffToken first = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        // 재발급 = 직전 미사용 토큰 soft-delete 무효화 (서비스 issueToken 의 무효화 로직 직접 검증)
        first.softDelete();
        tokenRepository.save(first);
        EmployeeSignatureHandoffToken second = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));

        // 구 토큰(soft-delete) = @SQLRestriction 으로 findByToken 미발견 → 404
        mockMvc.perform(post("/public/employee-signatures/{token}", first.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isNotFound());
        // 신규 토큰은 정상 제출
        mockMvc.perform(post("/public/employee-signatures/{token}", second.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isOk());
    }
```

- [ ] **Step 2: 실행 → 신규 케이스 FAIL/검증.** `cmd /c "gradlew.bat :services:user-service:test --tests *PublicSignatureSecurityGateIT --tests *PublicEmployeeSignatureControllerIT"`. C1b.3 production 코드가 이미 정확하면 신규 케이스도 PASS (회귀 박제). soft-delete 후 `findByToken` 가 여전히 구 토큰을 반환하면(=`@SQLRestriction` 누락) FAIL → 엔티티 `@SQLRestriction("is_deleted = false")` 확인.

- [ ] **Step 3: 미통과 시 최소 수정.** `findByToken` 이 soft-delete 토큰을 반환하면 리포지토리 쿼리가 `@SQLRestriction` 을 우회하는지 점검(JPQL 명시 쿼리면 `AND t.isDeleted = false` 추가). 그 외 케이스가 다른 상태코드면 C1b.3 서비스/컨트롤러 매핑 정렬.

- [ ] **Step 4: 실행 → PASS.** 위 명령 전체 PASS. + user-service 전체 `cmd /c "gradlew.bat :services:user-service:test"` 그린.

- [ ] **Step 5: 커밋.**
```bash
git add services/user-service/src/test/java/com/samhanair/logis/user/it/PublicSignatureSecurityGateIT.java \
        services/user-service/src/test/java/com/samhanair/logis/user/it/PublicEmployeeSignatureControllerIT.java
git commit -F - <<'EOF'
test(user): 공개 서명 인증우회 표면 회귀 가드 (slice C1b)

- permitAll 경로는 X-User-* 없이도 200, 위조 identity 헤더 주입해도 동작 무변동
- 재발급 무효화된 구 토큰 제출 404 (soft-delete + @SQLRestriction 박제)
- 토큰 위협모델 전수: 만료/재사용/위조/충돌/hash/크기
EOF
```

---

**슬라이스 종료 후 (PR 머지 전, [[feedback_temp_multimodel_workflow]]·[[feedback_overnight_live_capture]]):**
- CI green 확인(`gh pr checks --watch`, [[feedback_pr_ci_monitoring]]) — ci.yml 의 user-service 잡이 신규 IT 패키지(`*.it.*`)를 자동 커버하는지 확인(allowlist 필터면 등재 [[feedback_ci_test_filter_false_green]]).
- Docker 라이브 실QA: 게이트웨이+user-service 기동 → `POST /api/v1/admin/users/{id}/signature/handoff-token`(dev_master JWT) qrUrl 발급 캡처 → `POST /api/public/employee-signatures/{token}`(JWT 없이, 토큰만) 실 제출 200 캡처 → 만료/재사용/위조 4xx 캡처 → DB `employees.signature_png NOT NULL` + `employee_signature_handoff_token.used_at NOT NULL` 실 query 캡처. PR 본문 인라인 첨부([[feedback_no_fake_data_ever]] — 합성 금지).
- dual 5-agent 리뷰(Opus→Codex) — QA agent 의 Docker 위협모델 실QA 스크린샷을 해당 라운드 리뷰 코멘트에 인라인 게시.