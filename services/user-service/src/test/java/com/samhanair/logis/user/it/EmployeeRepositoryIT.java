package com.samhanair.logis.user.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.UserServiceApplication;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.repository.EmployeeRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test against a real PostgreSQL container, validating the partial unique
 * indexes from V1__init_user_service.sql. Requires Docker — skipped automatically by
 * Testcontainers if the host has no daemon.
 */
@SpringBootTest(classes = UserServiceApplication.class)
@Transactional
class EmployeeRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @MockBean
    private com.samhanair.logis.user.client.AuthClient authClient;

    private Department salesTeam;

    @BeforeEach
    void setUp() {
        salesTeam = departmentRepository.findByCode("SALES_1")
                .orElseGet(() -> departmentRepository.save(Department.create("SALES_1", "영업1팀", 2)));
    }

    @Test
    void partialUniqueIndex_loginId_allowsReuseAfterSoftDelete() {
        UUID idA = UUID.randomUUID();
        Employee a = employeeRepository.save(Employee.create(
                idA, "dupcheck", "직원A", "사원", Role.SALES, salesTeam, false,
                LocalDate.of(2026, 1, 1), null, null));
        a.markDeleted("test");
        employeeRepository.flush();

        UUID idB = UUID.randomUUID();
        Employee b = employeeRepository.save(Employee.create(
                idB, "dupcheck", "직원B", "사원", Role.SALES, salesTeam, false,
                LocalDate.of(2026, 1, 1), null, null));

        employeeRepository.flush();
        assertThat(b.getId()).isEqualTo(idB);
    }

    @Test
    void uniqueOneLeadPerDept_secondLeadInsertFails() {
        employeeRepository.save(Employee.create(
                UUID.randomUUID(), "leadA", "리더A", "이사", Role.SALES, salesTeam, true,
                LocalDate.of(2026, 1, 1), null, null));
        employeeRepository.flush();

        Employee secondLead = Employee.create(
                UUID.randomUUID(), "leadB", "리더B", "이사", Role.SALES, salesTeam, true,
                LocalDate.of(2026, 1, 1), null, null);

        assertThatThrownBy(() -> {
            employeeRepository.save(secondLead);
            employeeRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sqlRestrictionFilter_hidesDeletedRowsFromFindAll() {
        employeeRepository.save(Employee.create(
                UUID.randomUUID(), "alive", "활성", "사원", Role.SALES, salesTeam, false,
                LocalDate.of(2026, 1, 1), null, null));
        Employee terminated = employeeRepository.save(Employee.create(
                UUID.randomUUID(), "gone", "퇴사", "사원", Role.SALES, salesTeam, false,
                LocalDate.of(2026, 1, 1), null, null));
        terminated.markDeleted("test");
        employeeRepository.flush();

        var results = employeeRepository.findAll();
        assertThat(results).extracting(Employee::getLoginId).contains("alive").doesNotContain("gone");
    }
}
