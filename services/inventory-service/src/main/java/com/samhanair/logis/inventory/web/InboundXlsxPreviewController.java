package com.samhanair.logis.inventory.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.inventory.service.InboundXlsxParser;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 가입고 XLSX를 전표로 쓰지 않고 레거시 규칙으로 미리보기만 만드는 endpoint. */
@RestController
@RequestMapping("/warehouse/inbound-xlsx")
@RequiredArgsConstructor
public class InboundXlsxPreviewController {

    private final InboundXlsxParser parser;

    @Operation(summary = "가입고 XLSX 미리보기", description = "레거시 가입고 XLSX를 파싱해 행과 누락 사유만 반환합니다. 전표를 생성하지 않습니다.")
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(page = "inventory.dps", action = PermissionAction.VIEW)
    public ApiResponse<InboundXlsxParser.ParseResult> preview(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("가입고 XLSX 파일이 비어있습니다");
        try {
            return ApiResponse.ok(parser.parse(file.getInputStream()), "가입고 XLSX 미리보기 완료");
        } catch (IOException ex) {
            throw new IllegalArgumentException("가입고 XLSX 파일을 읽을 수 없습니다", ex);
        }
    }
}
