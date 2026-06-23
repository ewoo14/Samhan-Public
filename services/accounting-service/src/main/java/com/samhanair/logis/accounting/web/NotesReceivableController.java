package com.samhanair.logis.accounting.web;

import com.samhanair.logis.accounting.domain.NoteStatus;
import com.samhanair.logis.accounting.service.NotesReceivableService;
import com.samhanair.logis.accounting.web.dto.CreateNotesReceivableRequest;
import com.samhanair.logis.accounting.web.dto.NotesReceivableResponse;
import com.samhanair.logis.accounting.web.dto.UpdateNotesReceivableStatusRequest;
import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 받을어음 CRUD endpoint.
 *
 * <p>PageCode 는 회계 보고 스위트 공통 코드인 {@code accounting.reports} 를 사용한다.
 * 신규 page-code를 만들지 않아 auth-service enum/seed 변경 없이 G-1을 accounting-service 범위에
 * 유지한다.
 */
@RestController
@RequestMapping("/accounting/notes-receivable")
@RequiredArgsConstructor
@Tag(name = "받을어음", description = "회계 보고 스위트 G-1 받을어음 등록/목록/상태전이")
public class NotesReceivableController {

    private static final String PAGE_CODE = "accounting.reports";

    private final NotesReceivableService service;

    /** 받을어음 등록. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(page = PAGE_CODE, action = PermissionAction.CREATE)
    @Operation(summary = "받을어음 등록", description = "partnerCode/bizNo/partnerName 중 하나로 거래처를 resolve 하여 등록")
    public ApiResponse<NotesReceivableResponse> register(
            @RequestBody @Valid CreateNotesReceivableRequest request) {
        return ApiResponse.ok(service.register(request), "받을어음이 등록되었습니다.");
    }

    /** 받을어음 목록. 기본 정렬은 만기 임박순. */
    @GetMapping
    @RequirePermission(page = PAGE_CODE, action = PermissionAction.VIEW)
    @Operation(summary = "받을어음 목록", description = "만기 임박순 목록. status / partnerCode / bizNo / partnerName 필터")
    public ApiResponse<List<NotesReceivableResponse>> list(
            @RequestParam(required = false) NoteStatus status,
            @RequestParam(required = false) String partnerCode,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) String partnerName) {
        return ApiResponse.ok(service.list(status, partnerCode, bizNo, partnerName));
    }

    /** 받을어음 단건 조회. */
    @GetMapping("/{noteNo}")
    @RequirePermission(page = PAGE_CODE, action = PermissionAction.VIEW)
    @Operation(summary = "받을어음 단건 조회", description = "어음번호 기준 조회. UUID 미노출")
    public ApiResponse<NotesReceivableResponse> getOne(@PathVariable String noteNo) {
        return ApiResponse.ok(service.getOne(noteNo));
    }

    /** 받을어음 상태 전이. */
    @PatchMapping("/{noteNo}/status")
    @RequirePermission(page = PAGE_CODE, action = PermissionAction.UPDATE)
    @Operation(summary = "받을어음 상태 전이", description = "COLLECTING/SETTLED/DISHONORED 로 전이")
    public ApiResponse<NotesReceivableResponse> updateStatus(
            @PathVariable String noteNo,
            @RequestBody @Valid UpdateNotesReceivableStatusRequest request) {
        return ApiResponse.ok(service.transition(noteNo, request.status()), "받을어음 상태가 변경되었습니다.");
    }
}
