package com.samhanair.logis.groupware.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.samhanair.logis.groupware.GroupwareServiceApplication;
import com.samhanair.logis.groupware.domain.ApprovalTemplate;
import com.samhanair.logis.groupware.repository.ApprovalTemplateFieldRepository;
import com.samhanair.logis.groupware.repository.ApprovalTemplateRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** 활성 그룹웨어 결재유형 템플릿 목록 — 인증-only public path 계약 IT. */
@SpringBootTest(classes = GroupwareServiceApplication.class)
@AutoConfigureMockMvc
@Transactional
class ApprovalTemplateActiveEndpointIT extends AbstractPostgresIT {

    private static final String ACTOR_ID = "40000000-0000-0000-0000-000000000501";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApprovalTemplateRepository templateRepository;

    @Autowired
    private ApprovalTemplateFieldRepository fieldRepository;

    @BeforeEach
    void setUp() {
        fieldRepository.deleteAll();
        templateRepository.deleteAll();
        templateRepository.save(ApprovalTemplate.create("INACTIVE_IT", "비활성 양식", "비활성", false, 1));
        templateRepository.save(ApprovalTemplate.create("EXPENSE_REPORT_IT", "지출결의서", "지출", true, 20));
        templateRepository.save(ApprovalTemplate.create("LEAVE_REQUEST_IT", "휴가신청서", "휴가", true, 10));
        templateRepository.flush();
    }

    @Test
    @DisplayName("GET /groupware/approval-templates/active — 인증 사용자에게 active=true 템플릿만 displayOrder 순 반환")
    void activeTemplates_authenticatedUser_returnsActiveOnlyOrdered() throws Exception {
        MvcResult result = mvc.perform(get("/groupware/approval-templates/active")
                        .header("X-User-Id", ACTOR_ID)
                        .header("X-User-Role", "SALES")
                        .header("X-Is-System-Master", "false"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("LEAVE_REQUEST_IT")
                .contains("EXPENSE_REPORT_IT")
                .contains("휴가신청서")
                .contains("지출결의서")
                .doesNotContain("INACTIVE_IT")
                .doesNotContain("비활성 양식");
        assertThat(body.indexOf("LEAVE_REQUEST_IT")).isLessThan(body.indexOf("EXPENSE_REPORT_IT"));
    }

    @Test
    @DisplayName("GET /groupware/approval-templates/active — 비인증 직접 호출은 403")
    void activeTemplates_anonymous_returns403() throws Exception {
        MvcResult result = mvc.perform(get("/groupware/approval-templates/active"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}
