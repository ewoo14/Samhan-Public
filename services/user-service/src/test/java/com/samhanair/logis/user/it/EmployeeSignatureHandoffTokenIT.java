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

    @Test
    void 대량_발급_토큰은_중복되지_않는다() {
        var tokens = java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> EmployeeSignatureHandoffToken.issue(employee.getId(), "actor-1").getToken())
                .toList();

        assertThat(tokens).doesNotHaveDuplicates();
    }
}
