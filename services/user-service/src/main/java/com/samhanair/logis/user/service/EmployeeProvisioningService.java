package com.samhanair.logis.user.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.client.AuthClient;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.web.dto.CreateEmployeeRequest;
import com.samhanair.logis.user.web.dto.EmployeeResponse;
import com.samhanair.logis.user.web.dto.UpdateEmployeeRequest;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisioning saga for {@link Employee} + corresponding {@code Account} in auth-service.
 *
 * <p>Order: Auth first (so we discover loginId conflicts before touching local state),
 * then local persist. If local persist fails we compensate by calling
 * {@link AuthClient#delete(UUID)} and re-throw; secondary errors during compensation
 * are logged but swallowed so the original cause surfaces.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeProvisioningService.class);

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AuthClient authClient;

    public EmployeeResponse create(CreateEmployeeRequest req, UUID callerId) {
        Department department = departmentRepository.findById(req.departmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "부서를 찾을 수 없습니다"));

        UUID newId = UUID.randomUUID();

        // Step 1 — Auth first. Conflicts surface here so we never persist a half-baked employee.
        authClient.createAccount(newId, req.loginId(), req.password(), req.fullName(), req.role());

        // Step 2 — local persist; compensate on failure.
        try {
            Employee saved = employeeRepository.save(Employee.create(
                    newId,
                    req.loginId(),
                    req.fullName(),
                    req.position(),
                    req.role(),
                    department,
                    req.teamLead(),
                    req.hireDate(),
                    req.email(),
                    req.phone()));
            return EmployeeResponse.from(saved);
        } catch (RuntimeException persistFailure) {
            try {
                authClient.delete(newId);
            } catch (RuntimeException compensationFailure) {
                log.error("Compensation delete failed for account {}: {}", newId, compensationFailure.getMessage());
            }
            throw persistFailure;
        }
    }

    public EmployeeResponse update(UUID id, UpdateEmployeeRequest req, UUID callerId) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원을 찾을 수 없습니다"));

        if (req.fullName() != null && !Objects.equals(req.fullName(), employee.getFullName())) {
            employee.changeFullName(req.fullName());
            // Q2 — propagate displayName drift into auth-service.
            authClient.updateDisplayName(id, req.fullName());
        }
        if (req.position() != null) {
            employee.changePosition(req.position());
        }
        if (req.departmentId() != null
                && !Objects.equals(req.departmentId(), employee.getDepartment().getId())) {
            Department dept = departmentRepository.findById(req.departmentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "부서를 찾을 수 없습니다"));
            employee.changeDepartment(dept);
        }
        if (req.teamLead() != null) {
            employee.setTeamLead(req.teamLead());
        }
        if (req.email() != null) {
            employee.changeEmail(req.email());
        }
        if (req.phone() != null) {
            employee.changePhone(req.phone());
        }
        return EmployeeResponse.from(employee);
    }

    public EmployeeResponse updateRole(UUID id, Role role, UUID callerId) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원을 찾을 수 없습니다"));

        employee.updateRoleSnapshot(role);
        authClient.updateRole(id, role);
        return EmployeeResponse.from(employee);
    }

    public void terminate(UUID id, LocalDate date, UUID callerId) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원을 찾을 수 없습니다"));

        employee.terminate(date);
        employee.markDeleted(callerId == null ? "system" : callerId.toString());

        authClient.disable(id);
    }
}
