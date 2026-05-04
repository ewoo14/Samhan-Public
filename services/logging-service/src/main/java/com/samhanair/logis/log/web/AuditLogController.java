package com.samhanair.logis.log.web;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.log.domain.AuditLog;
import com.samhanair.logis.log.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Audit log search REST API.
 *
 * Authorization: gateway is expected to enforce MASTER / MANAGER role
 * before the request reaches this service. We intentionally do not
 * re-check the role here — trust the upstream.
 */
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository repository;

    @GetMapping("/by-service/{serviceName}")
    public ApiResponse<Page<AuditLog>> byService(
            @PathVariable String serviceName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(
                repository.findByServiceName(serviceName, paged(page, size)));
    }

    @GetMapping("/by-user/{userId}")
    public ApiResponse<Page<AuditLog>> byUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(
                repository.findByUserId(userId, paged(page, size)));
    }

    @GetMapping("/search")
    public ApiResponse<Page<AuditLog>> search(
            @RequestParam String action,
            @RequestParam Instant fromInstant,
            @RequestParam Instant toInstant,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(
                repository.findByActionAndOccurredAtBetween(
                        action, fromInstant, toInstant, paged(page, size)));
    }

    private static Pageable paged(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
    }
}
