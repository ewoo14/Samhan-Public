package com.samhanair.logis.user.web.dto;

import com.samhanair.logis.user.domain.Employee;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 사용자 관리 목록 응답 — Phase 10 P0-5 admin endpoint.
 *
 * <p>매뉴얼 §1 사용자 목록 화면 (filter / 페이지네이션) backing DTO. items 만 사용자 표시 필드,
 * total / page / size 는 페이지네이션 컨트롤.
 *
 * <p>Spring Data {@link Page} 의 zero-based page 번호 그대로 전달 (frontend 가 +1 처리).
 *
 * @param items 페이지 내 사용자 요약 리스트
 * @param total 전체 매칭 건수
 * @param page 0-based 페이지 번호
 * @param size 페이지 크기
 */
public record AdminUserListResponse(
        List<EmployeeResponse> items,
        long total,
        int page,
        int size
) {

    public static AdminUserListResponse from(Page<Employee> page) {
        List<EmployeeResponse> items = page.getContent().stream()
                .map(EmployeeResponse::from)
                .toList();
        return new AdminUserListResponse(
                items,
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }
}
