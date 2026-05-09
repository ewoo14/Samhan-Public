package com.samhanair.logis.slip.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * PR-E1 BE-A5 응답 — 다음날자 전표 이미지 데이터.
 *
 * <p>FE 가 받아서 이미지 렌더링 (window.print 또는 html2canvas). slip + chat-room + block + region
 * 5 way 정보가 슬립별로 동봉 — 이미지 1장당 거래처별 그룹 1개.
 *
 * <p>응답 구조:
 * <ul>
 *   <li>{@code targetDate} = 호출 시 입력한 date+1 (다음날자)</li>
 *   <li>{@code totalSlips} = 다음날자 전체 슬립 수 (legacy 카운트)</li>
 *   <li>{@code regionGroups} = 지역 그룹별 묶음 N건 (지역 미분류 = "미분류" key)</li>
 * </ul>
 *
 * <p>UUID 비공개 가드 — entry 레벨에 partner_id 미노출, partner_code / partner_name 만 표시.
 */
public record NextDaySlipImageResponse(
        LocalDate targetDate,
        int totalSlips,
        List<RegionGroup> regionGroups) {

    /**
     * 지역 그룹 1개 — region_group 별 슬립 묶음. legacy GAS 의 "내일자 전표 이미지" 의 지역별 페이지에 대응.
     */
    public record RegionGroup(
            String regionGroup,
            int slipCount,
            List<SlipImageEntry> slips) {
    }

    /**
     * 슬립 1건의 이미지 데이터 — partner_code 매핑 정보 + 단톡방 + 발송금지 flag 포함.
     *
     * <p>{@code chatRoomNames} = notification-service 의 partner_code → chat_room_name 매핑.
     * 미매핑 시 빈 리스트. {@code blocked} = partner-service BLOCK 발송금지 여부 (true 면 FE 가 경고 표시).
     */
    public record SlipImageEntry(
            String slipNo,
            LocalDate slipDate,
            String partnerCode,
            String partnerName,
            String driverName,
            String driverPhone,
            String classifiedRegionGroup,
            String memo,
            List<String> chatRoomNames,
            boolean blocked) {
    }
}
