package com.samhanair.logis.dcconfig.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhanair.logis.dcconfig.DcConfigServiceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 수식 빌더 Phase 1 — 종합견적서 전역 가격 파라미터 endpoint IT. */
@SpringBootTest(classes = DcConfigServiceApplication.class)
@AutoConfigureMockMvc
@Transactional
class EstimateConfigControllerIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("admin GET은 estimate_configs 기본 싱글톤을 seed 후 반환한다")
    void adminGet_seedsDefaultSingleton() throws Exception {
        mockMvc.perform(get("/api/v1/estimate-config")
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000100")
                        .header("X-Is-System-Master", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.commonHomeDiscountRate").value(0.45))
                .andExpect(jsonPath("$.data.commonCommercialDiscountRate").value(0.45))
                .andExpect(jsonPath("$.data.oldProductDiscountRate").value(0.5))
                .andExpect(jsonPath("$.data.vatRate").value(0.1))
                .andExpect(jsonPath("$.data.cardFeeRate").value(0))
                .andExpect(jsonPath("$.data.advanceDiscountRate").value(0));
    }

    @Test
    @DisplayName("admin PUT은 전역 가격 파라미터를 수정한다")
    void adminPut_updatesSingleton() throws Exception {
        mockMvc.perform(put("/api/v1/estimate-config")
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000101")
                        .header("X-Is-System-Master", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commonHomeDiscountRate": 0.4200,
                                  "commonCommercialDiscountRate": 0.4300,
                                  "oldProductDiscountRate": 0.5500,
                                  "vatRate": 0.1000,
                                  "cardFeeRate": 0.0300,
                                  "advanceDiscountRate": 0.0200,
                                  "comboWarnRate": 0.8000,
                                  "footerNotice": "테스트 안내"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commonHomeDiscountRate").value(0.42))
                .andExpect(jsonPath("$.data.commonCommercialDiscountRate").value(0.43))
                .andExpect(jsonPath("$.data.oldProductDiscountRate").value(0.55))
                .andExpect(jsonPath("$.data.cardFeeRate").value(0.03))
                .andExpect(jsonPath("$.data.advanceDiscountRate").value(0.02))
                .andExpect(jsonPath("$.data.footerNotice").value("테스트 안내"));
    }

    @Test
    @DisplayName("internal GET은 X-Internal-Token 없으면 401, 있으면 기본값을 반환한다")
    void internalGet_requiresInternalToken() throws Exception {
        mockMvc.perform(get("/internal/estimate-config"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/internal/estimate-config")
                        .header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commonHomeDiscountRate").value(0.45))
                .andExpect(jsonPath("$.data.footerNotice").isString());
    }
}
