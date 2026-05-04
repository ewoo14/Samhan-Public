package com.samhanair.logis.user.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.web.dto.DepartmentResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only department directory. Available to any authenticated caller. */
@RestController
@RequestMapping("/users/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    @GetMapping
    public ApiResponse<List<DepartmentResponse>> list() {
        List<DepartmentResponse> result = departmentRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(DepartmentResponse::from)
                .toList();
        return ApiResponse.ok(result);
    }
}
