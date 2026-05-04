package com.samhanair.logis.user.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.service.dto.EmployeeProjection;
import com.samhanair.logis.user.service.dto.OrgChartNode;
import com.samhanair.logis.user.web.dto.OrgChartResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only org-chart + batch lookup. Soft-deleted (terminated) employees are excluded. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrgChartService {

    private static final int LOOKUP_MAX = 100;

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public OrgChartResponse getOrgChart() {
        List<Department> departments = departmentRepository.findAllByOrderByDisplayOrderAsc();
        List<Employee> employees = employeeRepository.findAll();

        Map<UUID, List<Employee>> byDept = employees.stream()
                .collect(Collectors.groupingBy(e -> e.getDepartment().getId()));

        List<OrgChartNode> nodes = new ArrayList<>(departments.size());
        for (Department d : departments) {
            List<Employee> deptMembers = byDept.getOrDefault(d.getId(), List.of()).stream()
                    .sorted(Comparator
                            .comparing(Employee::isTeamLead).reversed()
                            .thenComparing(Employee::getFullName))
                    .toList();

            EmployeeProjection lead = deptMembers.stream()
                    .filter(Employee::isTeamLead)
                    .findFirst()
                    .map(this::toProjection)
                    .orElse(null);

            List<EmployeeProjection> members = deptMembers.stream()
                    .map(this::toProjection)
                    .toList();

            nodes.add(new OrgChartNode(d.getId(), d.getCode(), d.getName(), lead, members));
        }
        return new OrgChartResponse(nodes);
    }

    public List<EmployeeProjection> lookup(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회할 직원 ID가 비어있습니다");
        }
        if (ids.size() > LOOKUP_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "한 번에 조회할 수 있는 최대 직원 수는 " + LOOKUP_MAX + "명입니다");
        }
        return employeeRepository.findAllByIdIn(ids).stream()
                .map(this::toProjection)
                .toList();
    }

    private EmployeeProjection toProjection(Employee e) {
        return new EmployeeProjection(
                e.getId(),
                e.getFullName(),
                e.getRoleSnapshot(),
                e.getDepartment().getName(),
                e.getPosition());
    }
}
