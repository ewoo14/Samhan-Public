package com.samhanair.logis.partner.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.partner.PartnerServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * ApplicationContext load 만 검증하는 경량 IT — bean 등록 충돌 / dependency injection 누락 즉시 탐지.
 *
 * <p><b>장기 가드 (PR #119 commit 4c98ed2 패턴 일관, PR-G1 backlog #3).</b> partner-service 의
 * Configuration class 들 (BlockedPartner / PartnerAligoExportService 신규) 의 {@code @Bean}
 * 메서드 이름 충돌 패턴을 사전에 차단. 본 IT 가 즉시 fail (BeanDefinitionOverrideException) 회귀
 * 가드.
 *
 * <p>partner-service 는 외부 client 가 없으므로 {@code @MockBean} 0개 (자체 도메인만 책임).
 *
 * <p>Docker 미가용 환경에서는 {@link AbstractPostgresIT} 의 {@code DockerAvailableCondition}
 * 으로 자동 skip — CI Linux runner 에서만 실 검증.
 */
@SpringBootTest(classes = PartnerServiceApplication.class)
class ApplicationContextLoadIT extends AbstractPostgresIT {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Spring ApplicationContext 가 BeanDefinitionOverrideException / NoSuchBeanDefinitionException
     * 없이 정상 부팅하는지만 검증.
     */
    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
