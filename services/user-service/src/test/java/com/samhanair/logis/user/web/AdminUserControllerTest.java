package com.samhanair.logis.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.RoleChangeHistoryRepository;
import com.samhanair.logis.user.service.EmployeeProvisioningService;
import com.samhanair.logis.user.web.dto.AdminUserListResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AdminUserController 단위 테스트 — Phase 10 P0-5.
 *
 * <p>핵심: 페이지네이션 응답 형태 (items / total / page / size) 와 필터 파라미터 정상 전달.
 */
class AdminUserControllerTest {

    private final EmployeeProvisioningService provisioningService = mock(EmployeeProvisioningService.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final RoleChangeHistoryRepository roleHistoryRepository = mock(RoleChangeHistoryRepository.class);

    private final AdminUserController controller = new AdminUserController(
            provisioningService, employeeRepository, roleHistoryRepository);

    @Test
    @DisplayName("list — 페이지네이션 응답에 items / total / page / size 포함")
    void list_returns_paginated_response() {
        Department dept = Department.create("SALES_1", "영업1팀", 2);
        UUID deptId = UUID.randomUUID();
        ReflectionTestUtils.setField(dept, "id", deptId);
        Employee emp = Employee.create(UUID.randomUUID(), "kim01", "김영업", "사원",
                Role.SALES, dept, false, LocalDate.of(2026, 1, 1), null, null);
        Page<Employee> page = new PageImpl<>(List.of(emp), PageRequest.of(0, 20), 1L);
        when(employeeRepository.searchAdmin(any(), any(), any(), any())).thenReturn(page);

        ApiResponse<AdminUserListResponse> response =
                controller.list(0, 20, "김", Role.SALES, deptId);

        assertThat(response.isSuccess()).isTrue();
        AdminUserListResponse body = response.getData();
        assertThat(body.items()).hasSize(1);
        assertThat(body.items().get(0).fullName()).isEqualTo("김영업");
        assertThat(body.total()).isEqualTo(1);
        assertThat(body.page()).isEqualTo(0);
        assertThat(body.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("listRoles — 7 ROLE 모두 반환 (MASTER 포함)")
    void listRoles_returns_all_seven_roles() {
        ApiResponse<List<Role>> response = controller.listRoles();
        assertThat(response.getData()).hasSize(7).contains(Role.MASTER, Role.MANAGER);
    }
}
