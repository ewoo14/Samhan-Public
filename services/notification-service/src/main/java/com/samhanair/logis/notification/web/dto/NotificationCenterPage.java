package com.samhanair.logis.notification.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지네이션 응답 — number 0-base, totalElements/totalPages 표준.
 */
public record NotificationCenterPage(
        List<NotificationCenterResponse> content,
        int number,
        int size,
        long totalElements,
        int totalPages
) {
    public static NotificationCenterPage from(Page<NotificationCenterResponse> page) {
        return new NotificationCenterPage(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
