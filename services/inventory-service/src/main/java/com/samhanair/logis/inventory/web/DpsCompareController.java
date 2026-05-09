package com.samhanair.logis.inventory.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.inventory.service.DpsCompareGroupBy;
import com.samhanair.logis.inventory.service.DpsCompareService;
import com.samhanair.logis.inventory.web.dto.DpsCompareResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * DPS 입고 비교 endpoint — PR-E1 BE-2.
 *
 * <p>Samhan Public 자동화: legacy GAS 1번 (DPS 입고기록 비교) + 16번 (품목별 DPS 입고내역 비교)
 * 의 자체 운영 endpoint. 출고전표 = 자체 자동 조회 (slip-service Feign), DPS = 사용자 엑셀 업로드
 * 유지 (사용자 명시: "DPS 엑셀을 그대로 업로드할 수 있게 해야함 — 자동으로 가져올 수 없음").
 *
 * <p>권한 매트릭스 (memory ROLE 풀네임 의무):
 * <ul>
 *   <li>POST /warehouse/audit/dps-compare — MASTER / MANAGER / WAREHOUSE / INVENTORY</li>
 *   <li>GET /warehouse/audit/dps-compare/template — MASTER / MANAGER / WAREHOUSE / INVENTORY</li>
 * </ul>
 *
 * <p>UUID 비공개 — 응답에는 slipNo / productCode / partnerCode / partnerName 비즈니스 식별자만
 * 노출. slip-service 의 productId / partnerId UUID 는 의도적으로 wire-format 에서 제거.
 */
@RestController
@RequestMapping("/warehouse/audit/dps-compare")
@RequiredArgsConstructor
public class DpsCompareController {

    private final DpsCompareService dpsCompareService;

    /**
     * DPS 입고 비교 — multipart 업로드 + 출고전표 자동 조회 + 매칭 결과 응답.
     *
     * @param file    DPS 엑셀 (.xlsx) multipart 파일 (필수)
     * @param from    출고전표 자동 조회 기간 시작 (yyyy-MM-dd)
     * @param to      출고전표 자동 조회 기간 종료 (yyyy-MM-dd)
     * @param groupBy 매칭 단위 (SLIP / ITEM, 기본 SLIP)
     * @return 매칭 + mismatch 결과 ({@link DpsCompareResponse})
     */
    @Operation(summary = "DPS 입고 비교",
            description = "출고전표 자동 조회 + DPS 엑셀 업로드 → SLIP/ITEM 단위 매칭 + mismatch 분류")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "비교 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "엑셀 형식 오류 / 파일 비어있음 / 인자 누락"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 누락"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 부족"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "slip-service 호출 실패")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','WAREHOUSE','INVENTORY')")
    public ApiResponse<DpsCompareResponse> compare(
            @RequestParam("file") MultipartFile file,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "groupBy", defaultValue = "SLIP") DpsCompareGroupBy groupBy) {
        return ApiResponse.ok(dpsCompareService.compare(file, from, to, groupBy),
                "DPS 입고 비교 완료");
    }

    /**
     * DPS 엑셀 업로드 양식 다운로드 — 헤더 row 만 있는 빈 .xlsx.
     *
     * <p>사용자 가이드 — 5개 헤더 (품번 / 입고일자 / 수량 / 거래처코드 / 거래처명) 만 채워서
     * 다시 업로드.
     *
     * @return .xlsx 바이너리 (Content-Disposition attachment)
     */
    @Operation(summary = "DPS 엑셀 양식 다운로드",
            description = "헤더 row 만 있는 빈 .xlsx — 사용자가 채워서 다시 업로드")
    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','WAREHOUSE','INVENTORY')")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] body = dpsCompareService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"dps-compare-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
