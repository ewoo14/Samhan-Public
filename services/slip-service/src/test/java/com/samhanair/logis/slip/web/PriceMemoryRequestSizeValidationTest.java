package com.samhanair.logis.slip.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.slip.domain.SlipType;
import com.samhanair.logis.slip.estimate.web.dto.CreateEstimateRequest;
import com.samhanair.logis.slip.web.dto.CreateSlipRequest;
import com.samhanair.logis.slip.web.dto.PartnerProductPriceMemoryBulkRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** CH-8/CH-9 최대 100개 wire/라인 제한 Bean Validation 계약 테스트. */
class PriceMemoryRequestSizeValidationTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
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

    @Test
    void bulkRequest_over100ProductIds_isRejected() {
        PartnerProductPriceMemoryBulkRequest request = new PartnerProductPriceMemoryBulkRequest(
                UUID.randomUUID(), Collections.nCopies(101, UUID.randomUUID()));

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("productIds"));
    }

    @Test
    void createSlip_over100Lines_isRejected() {
        CreateSlipRequest.SlipLineRequest line = new CreateSlipRequest.SlipLineRequest(
                UUID.randomUUID(), "품목", "모델", null, 1, BigDecimal.ONE, null);
        CreateSlipRequest request = new CreateSlipRequest(
                SlipType.OUTBOUND,
                null,
                null,
                null,
                UUID.randomUUID(),
                "거래처",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Collections.nCopies(101, line));

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("lines"));
    }

    @Test
    void createEstimate_over100Lines_isRejected() {
        CreateEstimateRequest.EstimateLineRequest line = new CreateEstimateRequest.EstimateLineRequest(
                UUID.randomUUID(), "품목", "모델", null, 1, BigDecimal.ONE, null);
        CreateEstimateRequest request = new CreateEstimateRequest(
                null, UUID.randomUUID(), "거래처", null, null, null, null,
                Collections.nCopies(101, line));

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                        .isEqualTo("lines"));
    }
}
