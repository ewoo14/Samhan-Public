package com.samhanair.logis.inventory.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.inventory.InventoryServiceApplication;
import com.samhanair.logis.inventory.client.AccountingClient;
import com.samhanair.logis.inventory.client.ProductClient;
import com.samhanair.logis.inventory.client.SlipServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

/**
 * ApplicationContext load 만 검증하는 경량 IT — bean 등록 충돌 / dependency injection 누락 즉시 탐지.
 *
 * <p><b>장기 가드 (PR #119 commit 4c98ed2 패턴 일관, PR-G1 backlog #3).</b> inventory-service 의
 * Configuration class 들 (PR-E1 BE-2 신규 SlipServiceClient + DPS parser) 의 {@code @Bean} 메서드
 * 이름 충돌 패턴을 사전에 차단. 본 IT 가 즉시 fail (BeanDefinitionOverrideException) 회귀 가드.
 *
 * <p>외부 client {@code @MockBean} 격리 ({@code feedback_it_mockbean_external_clients}) — Eureka
 * 비활성 환경 5xx 회피.
 *
 * <p>Docker 미가용 환경에서는 {@link AbstractPostgresIT} 의 {@code DockerAvailableCondition} 으로
 * 자동 skip — CI Linux runner 에서만 실 검증.
 */
@SpringBootTest(classes = InventoryServiceApplication.class)
class ApplicationContextLoadIT extends AbstractPostgresIT {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private ProductClient productClient;
    @MockBean
    private AccountingClient accountingClient;
    @MockBean
    private SlipServiceClient slipServiceClient;

    /**
     * Spring ApplicationContext 가 BeanDefinitionOverrideException / NoSuchBeanDefinitionException
     * 없이 정상 부팅하는지만 검증.
     */
    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
