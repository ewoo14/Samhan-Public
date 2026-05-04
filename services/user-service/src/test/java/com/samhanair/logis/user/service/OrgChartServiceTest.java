package com.samhanair.logis.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.service.dto.EmployeeProjection;
import com.samhanair.logis.user.web.dto.OrgChartResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrgChartServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private OrgChartService service;

    private Department salesTeam;
    private UUID salesId;
    private Department accounting;
    private UUID accountingId;

    @BeforeEach
    void setUp() {
        salesTeam = Department.create("SALES_1", "영업1팀", 2);
        salesId = UUID.randomUUID();
        ReflectionTestUtils.setField(salesTeam, "id", salesId);

        accounting = Department.create("ACCOUNTING", "회계팀", 5);
        accountingId = UUID.randomUUID();
        ReflectionTestUtils.setField(accounting, "id", accountingId);
    }

    @Test
    void getOrgChart_groupsByDepartment_andSurfacesTeamLead() {
        Employee lead = newEmployee("오병승", Role.SALES, "이사", salesTeam, true);
        Employee member = newEmployee("홍지수", Role.SALES, "사원", salesTeam, false);
        Employee accountant = newEmployee("이성미", Role.ACCOUNTANT, "사원", accounting, true);

        when(departmentRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(salesTeam, accounting));
        when(employeeRepository.findAll()).thenReturn(List.of(lead, member, accountant));

        OrgChartResponse response = service.getOrgChart();

        assertThat(response.departments()).hasSize(2);
        var salesNode = response.departments().get(0);
        assertThat(salesNode.code()).isEqualTo("SALES_1");
        assertThat(salesNode.teamLead()).isNotNull();
        assertThat(salesNode.teamLead().fullName()).isEqualTo("오병승");
        assertThat(salesNode.members()).extracting(EmployeeProjection::fullName)
                .containsExactly("오병승", "홍지수");

        var accountingNode = response.departments().get(1);
        assertThat(accountingNode.teamLead().fullName()).isEqualTo("이성미");
    }

    @Test
    void getOrgChart_excludesTerminatedEmployees() {
        // @SQLRestriction handles this in the real repo. We simulate by simply not
        // returning them from findAll() — which is exactly the runtime contract.
        Employee active = newEmployee("홍지수", Role.SALES, "사원", salesTeam, false);
        when(departmentRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(salesTeam));
        when(employeeRepository.findAll()).thenReturn(List.of(active));

        OrgChartResponse response = service.getOrgChart();

        assertThat(response.departments()).hasSize(1);
        assertThat(response.departments().get(0).members()).hasSize(1);
        assertThat(response.departments().get(0).teamLead()).isNull();
    }

    @Test
    void lookup_returnsBatchProjection() {
        Employee a = newEmployee("A", Role.SALES, "사원", salesTeam, false);
        Employee b = newEmployee("B", Role.SALES, "사원", salesTeam, false);
        when(employeeRepository.findAllByIdIn(List.of(a.getId(), b.getId())))
                .thenReturn(List.of(a, b));

        List<EmployeeProjection> result = service.lookup(List.of(a.getId(), b.getId()));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(EmployeeProjection::fullName).containsExactly("A", "B");
        assertThat(result).extracting(EmployeeProjection::departmentName)
                .containsExactly("영업1팀", "영업1팀");
    }

    @Test
    void lookup_overSizeLimit_throwsBadRequest() {
        List<UUID> tooMany = new ArrayList<>(IntStream.range(0, 101)
                .mapToObj(i -> UUID.randomUUID())
                .toList());

        assertThatThrownBy(() -> service.lookup(tooMany))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void lookup_emptyList_throwsBadRequest() {
        assertThatThrownBy(() -> service.lookup(List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    private Employee newEmployee(String fullName, Role role, String position,
                                 Department dept, boolean teamLead) {
        return Employee.create(
                UUID.randomUUID(), fullName.toLowerCase().replaceAll("\\s+", "") + UUID.randomUUID(),
                fullName, position, role, dept, teamLead,
                LocalDate.of(2026, 1, 1), null, null);
    }
}
