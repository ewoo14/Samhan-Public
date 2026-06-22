package com.samhanair.logis.auth.web;

import com.samhanair.logis.auth.service.ApprovalLineConfigService;
import com.samhanair.logis.auth.web.dto.ApprovalLineStructureView;
import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 결재란 렌더용 구조 read 엔드포인트. 인증 사용자는 admin 권한 없이 조회할 수 있다. */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class ApprovalLineStructureController {

    private final ApprovalLineConfigService service;

    @GetMapping("/approval-line-configs/{documentType}/structure")
    public ApiResponse<List<ApprovalLineStructureView>> getStructure(@PathVariable String documentType) {
        return ApiResponse.ok(service.listStructure(requireDocumentType(documentType)));
    }

    private static String requireDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "전표 종류(documentType)를 입력해야 합니다");
        }
        return documentType.trim();
    }
}
