package com.samhanair.logis.user.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.service.EmployeeProvisioningService;
import com.samhanair.logis.user.service.OrgChartService;
import com.samhanair.logis.user.service.dto.EmployeeProjection;
import com.samhanair.logis.user.web.dto.CreateEmployeeRequest;
import com.samhanair.logis.user.web.dto.EmployeeResponse;
import com.samhanair.logis.user.web.dto.LookupRequest;
import com.samhanair.logis.user.web.dto.TerminateRequest;
import com.samhanair.logis.user.web.dto.UpdateEmployeeRequest;
import com.samhanair.logis.user.web.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Employee CRUD + lookup. Authorization is via gateway-injected X-User-Role headers. */
@RestController
@RequestMapping("/users/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private static final String CALLER_HEADER = "X-User-Id";

    private final EmployeeProvisioningService provisioningService;
    private final OrgChartService orgChartService;
    private final EmployeeRepository employeeRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<EmployeeResponse> create(
            @Valid @RequestBody CreateEmployeeRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(provisioningService.create(request, parseCaller(callerHeader)));
    }

    @GetMapping
    public ApiResponse<List<EmployeeResponse>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) Role role) {
        List<Employee> result;
        if (departmentId != null && role != null) {
            result = employeeRepository.findAllByDepartment_IdAndRoleSnapshot(departmentId, role);
        } else if (departmentId != null) {
            result = employeeRepository.findAllByDepartment_Id(departmentId);
        } else if (role != null) {
            result = employeeRepository.findAllByRoleSnapshot(role);
        } else {
            result = employeeRepository.findAll();
        }
        return ApiResponse.ok(result.stream().map(EmployeeResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<EmployeeResponse> getOne(@PathVariable UUID id) {
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원을 찾을 수 없습니다"));
        return ApiResponse.ok(EmployeeResponse.from(e));
    }

    @PostMapping("/lookup")
    public ApiResponse<List<EmployeeProjection>> lookup(@Valid @RequestBody LookupRequest request) {
        return ApiResponse.ok(orgChartService.lookup(request.ids()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<EmployeeResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(provisioningService.update(id, request, parseCaller(callerHeader)));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<EmployeeResponse> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(provisioningService.updateRole(id, request.role(), parseCaller(callerHeader)));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasRole('MASTER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void terminate(
            @PathVariable UUID id,
            @Valid @RequestBody TerminateRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        provisioningService.terminate(id, request.terminationDate(), parseCaller(callerHeader));
    }

    private UUID parseCaller(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
