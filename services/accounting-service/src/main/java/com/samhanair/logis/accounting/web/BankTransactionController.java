package com.samhanair.logis.accounting.web;

import com.samhanair.logis.accounting.domain.MatchStatus;
import com.samhanair.logis.accounting.service.BankTransactionService;
import com.samhanair.logis.accounting.web.dto.BankTransactionImportMapping;
import com.samhanair.logis.accounting.web.dto.BankTransactionImportResult;
import com.samhanair.logis.accounting.web.dto.BankTransactionResponse;
import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 입출금 매칭용 통장 거래 endpoint. */
@RestController
@RequestMapping("/accounting/bank-transactions")
@RequiredArgsConstructor
@Tag(name = "통장 거래", description = "회계 H-1 BankTransaction CSV import/조회")
public class BankTransactionController {

    private static final String PAGE_CODE = "accounting.bank-matching";

    private final BankTransactionService service;

    /** 범용 컬럼 매핑 CSV import. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(page = PAGE_CODE, action = PermissionAction.CREATE)
    @Operation(summary = "통장 CSV import", description = "은행별 CSV 컬럼을 사용자가 매핑해 BankTransaction 으로 적재")
    public ApiResponse<BankTransactionImportResult> importCsv(
            @RequestPart("file") MultipartFile file,
            @RequestParam String bankAccountLabel,
            @RequestParam String dateColumn,
            @RequestParam(required = false) String depositColumn,
            @RequestParam(required = false) String withdrawalColumn,
            @RequestParam(required = false) String balanceColumn,
            @RequestParam String descriptionColumn,
            @RequestParam(required = false) String counterpartyColumn,
            @RequestParam(required = false) String counterpartyAccountColumn,
            @RequestParam(required = false) String externalRefColumn,
            @RequestParam(defaultValue = "true") boolean headerRow) {
        BankTransactionImportMapping mapping = new BankTransactionImportMapping(
                dateColumn,
                depositColumn,
                withdrawalColumn,
                balanceColumn,
                descriptionColumn,
                counterpartyColumn,
                counterpartyAccountColumn,
                externalRefColumn,
                headerRow);
        return ApiResponse.ok(service.importCsv(file, bankAccountLabel, mapping),
                "통장 CSV import 가 완료되었습니다.");
    }

    /** 통장 거래 목록. */
    @GetMapping
    @RequirePermission(page = PAGE_CODE, action = PermissionAction.VIEW)
    @Operation(summary = "통장 거래 목록", description = "matchStatus 탭, 기간, 은행계좌 표시명 필터")
    public ApiResponse<List<BankTransactionResponse>> list(
            @RequestParam(required = false) MatchStatus matchStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String bankAccountLabel) {
        return ApiResponse.ok(service.list(matchStatus, from, to, bankAccountLabel));
    }
}
