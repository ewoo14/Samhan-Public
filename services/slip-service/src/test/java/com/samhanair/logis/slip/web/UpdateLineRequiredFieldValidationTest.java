package com.samhanair.logis.slip.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.slip.estimate.web.dto.UpdateEstimateRequest;
import com.samhanair.logis.slip.web.dto.SlipUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 수정 PUT 라인의 필수 snapshot 필드가 DB까지 내려가기 전에 400 계약으로 거부되는지 검증한다. */
class UpdateLineRequiredFieldValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    /** 매입·매출은 같은 DTO를 사용하므로 이 단언 하나가 두 PUT의 wire 계약을 함께 고정한다. */
    @Test
    void purchaseAndSalesUpdate_missingOrBlankProductName_isRejectedAsRequiredField() {
        assertRequiredProductNameViolation(slipRequest(null));
        assertRequiredProductNameViolation(slipRequest("   "));
    }

    /** 견적 수정도 매입·매출과 같은 한국어 필수필드 계약을 사용한다. */
    @Test
    void estimateUpdate_missingOrBlankProductName_isRejectedAsRequiredField() {
        assertRequiredProductNameViolation(estimateRequest(null));
        assertRequiredProductNameViolation(estimateRequest("   "));
    }

    private void assertRequiredProductNameViolation(Object request) {
        assertThat(validator.validate(request))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("lines[0].productName");
                    assertThat(violation.getMessage()).isEqualTo("품목명은 필수입니다.");
                });
    }

    private SlipUpdateRequest slipRequest(String productName) {
        return new SlipUpdateRequest(
                LocalDateTime.of(2026, 7, 17, 9, 0), UUID.randomUUID(), "거래처", null,
                null, null, null, null, null, null, null,
                List.of(new SlipUpdateRequest.LineRequest(
                        UUID.randomUUID(), productName, "MODEL-1", null,
                        1, BigDecimal.ONE, null, null)), true);
    }

    private UpdateEstimateRequest estimateRequest(String productName) {
        return new UpdateEstimateRequest(
                UUID.randomUUID(), "거래처", null, null, null, null,
                List.of(new UpdateEstimateRequest.EstimateLineUpdate(
                        UUID.randomUUID(), productName, "MODEL-1", null,
                        1, BigDecimal.ONE, null, null, true, null)), true);
    }
}
