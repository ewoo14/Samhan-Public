package com.samhanair.logis.partner.dto;

import com.samhanair.logis.partner.domain.Partner;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 거래처 admin 목록 응답 — Phase 10 P0-5.
 *
 * <p>frontend {@code /admin/partners} 페이지 backing. UUID 비공개 — items 는
 * {@link PartnerSummaryResponse} (partnerCode / bizNo / phone / status / 잔액 등 비즈니스 식별자만).
 *
 * @param items 페이지 내 거래처 요약 리스트
 * @param total 전체 매칭 건수
 * @param page 0-based 페이지 번호
 * @param size 페이지 크기
 */
public record AdminPartnerListResponse(
        List<PartnerSummaryResponse> items,
        long total,
        int page,
        int size
) {

    public static AdminPartnerListResponse from(Page<Partner> page) {
        List<PartnerSummaryResponse> items = page.getContent().stream()
                .map(PartnerSummaryResponse::from)
                .toList();
        return new AdminPartnerListResponse(
                items,
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }
}
