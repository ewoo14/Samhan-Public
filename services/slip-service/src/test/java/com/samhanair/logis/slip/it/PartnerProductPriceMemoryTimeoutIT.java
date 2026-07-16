package com.samhanair.logis.slip.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.slip.SlipServiceApplication;
import com.samhanair.logis.slip.config.SlipDataSourceConfig.PriceMemoryJdbcAccess;
import com.samhanair.logis.slip.client.InventoryClient;
import com.samhanair.logis.slip.client.PartnerInternalClient;
import com.samhanair.logis.slip.client.ProductClient;
import com.samhanair.logis.slip.client.UserInternalClient;
import com.samhanair.logis.slip.client.WarehouseInternalClient;
import com.samhanair.logis.slip.price.config.PartnerProductPriceMemoryProperties;
import com.samhanair.logis.slip.price.domain.PartnerProductPriceMemory;
import com.samhanair.logis.slip.price.repository.PartnerProductPriceMemoryBatchRepository;
import com.samhanair.logis.slip.price.service.PartnerProductPriceMemoryCommand;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * [R8-BE-4 / D-R8-2] 가격기억 전용 DataSource 격리 + 트랜잭션 timeout 실효성 — 실 PostgreSQL 검증.
 *
 * <p><b>왜 실 DB 여야 하는가</b>: 종전 검증은 {@code verify(batchRepository)
 * .applyTransactionTimeouts(1_000, 3_000)} — <b>mock 호출 여부</b>만 확인하는 false-green 이었다.
 * {@code set_config('lock_timeout', ?, true)} 의 {@code is_local=true} 는 <b>트랜잭션 로컬</b>이므로,
 * JdbcTemplate 이 TM 이 연 트랜잭션에 참여하지 못하고 별도 autocommit 커넥션을 잡으면 timeout 은
 * 그 statement 의 암묵 트랜잭션과 함께 <b>즉시 사라지고 upsert 에 적용되지 않는다</b>. mock 은 그
 * 무력화를 전혀 보지 못한다 — 호출은 여전히 일어나기 때문이다.
 *
 * <p>따라서 upsert 와 <b>같은 커넥션</b>에서 {@code pg_settings} 를 읽어 timeout 이 실제로 걸려
 * 있는지 단언한다. {@code pg_settings.setting} 은 lock_timeout/statement_timeout 을 ms 단위 문자열로
 * 반환하므로("1000"/"3000") {@code SHOW} 의 단위 정규화("1s"/"3s") 에 의존하지 않아 결정적이다.
 */
@SpringBootTest(classes = SlipServiceApplication.class)
class PartnerProductPriceMemoryTimeoutIT extends AbstractPostgresIT {

    @Autowired
    private PartnerProductPriceMemoryBatchRepository batchRepository;

    @Autowired
    private PartnerProductPriceMemoryProperties properties;

    @Autowired
    private PriceMemoryJdbcAccess priceMemoryJdbcAccess;

    @Autowired
    @Qualifier("priceMemoryDataSource")
    private DataSource priceMemoryDataSource;

    /** 무자격 주입 = @Primary 메인 pool 이어야 한다 (전용 pool 이 잡히면 함정 ② 재발). */
    @Autowired
    private DataSource primaryDataSource;

    /** 무자격 주입 = 자동구성 JdbcTemplate(메인 pool) 이어야 한다 (함정 ② 가드). */
    @Autowired
    private JdbcTemplate autoConfiguredJdbcTemplate;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private PartnerInternalClient partnerInternalClient;

    @MockBean
    private UserInternalClient userInternalClient;

    @MockBean
    private WarehouseInternalClient warehouseInternalClient;

    /**
     * 핵심 단언 — upsert 를 실행한 <b>바로 그 커넥션</b>이 lock/statement timeout 을 보유한다.
     *
     * <p>전용 JdbcTemplate 이 전용 TM 의 트랜잭션에 참여하지 못하면 이 단언은 "0"(=비활성, PG 기본)
     * 을 읽어 RED 가 된다. 즉 이 테스트가 {@code priceMemoryDataSource} +
     * {@code priceMemoryTransactionManager} + {@code priceMemoryJdbcTemplate} 3종 결속의 가드다.
     */
    @Test
    void transactionTimeouts_areActuallyAppliedOnTheSameConnectionAsTheUpsert() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        TransactionTemplate transactionTemplate = priceMemoryTransactionTemplate();

        TimeoutSettings settings = transactionTemplate.execute(status -> {
            batchRepository.applyTransactionTimeouts(
                    properties.getLockTimeoutMs(), properties.getStatementTimeoutMs());
            int affected = batchRepository.upsertAll(
                    List.of(new PartnerProductPriceMemoryCommand(
                            partnerId, productId, new BigDecimal("123000.00"),
                            PartnerProductPriceMemory.SOURCE_LINE_SAVE, "actor-timeout",
                            LocalDateTime.now())),
                    LocalDateTime.now());
            assertThat(affected).isEqualTo(1);
            // upsert 와 같은 트랜잭션 = 같은 커넥션에서 읽어야 의미가 있다
            return readTimeouts();
        });

        assertThat(settings).isNotNull();
        assertThat(settings.lockTimeoutMs())
                .as("upsert 커넥션의 lock_timeout — '0' 이면 set_config 가 무력화된 것")
                .isEqualTo(String.valueOf(properties.getLockTimeoutMs()));
        assertThat(settings.statementTimeoutMs())
                .as("upsert 커넥션의 statement_timeout — '0' 이면 set_config 가 무력화된 것")
                .isEqualTo(String.valueOf(properties.getStatementTimeoutMs()));

        cleanup(partnerId, productId);
    }

    /** {@code is_local=true} 계약 — 트랜잭션이 끝나면 timeout 이 커넥션에 남지 않는다(누수 방지). */
    @Test
    void transactionTimeouts_doNotLeakBeyondTheTransaction() {
        TransactionTemplate transactionTemplate = priceMemoryTransactionTemplate();
        transactionTemplate.executeWithoutResult(status ->
                batchRepository.applyTransactionTimeouts(
                        properties.getLockTimeoutMs(), properties.getStatementTimeoutMs()));

        // 트랜잭션 종료 후 새 트랜잭션에서 읽으면 세션 기본값(0 = 무제한)이어야 한다
        TimeoutSettings afterCommit = transactionTemplate.execute(status -> readTimeouts());

        assertThat(afterCommit).isNotNull();
        assertThat(afterCommit.lockTimeoutMs()).isEqualTo("0");
        assertThat(afterCommit.statementTimeoutMs()).isEqualTo("0");
    }

    /**
     * [R8-BE-4 함정 ②] 두 번째 DataSource 등록에도 메인 DataSource 가 살아있고, 자동구성
     * {@code JdbcTemplate} 은 <b>메인</b>({@code @Primary}) 을, 가격기억은 <b>전용</b> pool 을 쓴다.
     *
     * <p>메인이 back-off 로 사라지면 JPA/Flyway 기동이 실패하므로 사실 이 IT 의 컨텍스트 로딩
     * 자체가 1차 가드지만, 두 pool 이 <b>서로 다른 인스턴스</b>인지(=격리가 실재하는지) 를 명시한다.
     */
    @Test
    void dedicatedPool_isIsolatedFromPrimaryPoolUsedByJpaAndAutoConfiguredJdbcTemplate() {
        assertThat(priceMemoryDataSource).isNotSameAs(primaryDataSource);
        assertThat(autoConfiguredJdbcTemplate.getDataSource()).isSameAs(primaryDataSource);
        assertThat(priceMemoryJdbcAccess.jdbcTemplate().getDataSource()).isSameAs(priceMemoryDataSource);
        assertThat(priceMemoryJdbcAccess.dataSource()).isSameAs(priceMemoryDataSource);
    }

    /** 두 pool 이 같은 데이터베이스를 향한다 — 격리는 pool 단위이지 DB 단위가 아니다(XA 불필요). */
    @Test
    void dedicatedPool_targetsTheSameDatabaseAsPrimaryPool() {
        String primaryDb = autoConfiguredJdbcTemplate.queryForObject(
                "SELECT current_database()", String.class);
        String priceMemoryDb = priceMemoryJdbcAccess.jdbcTemplate().queryForObject(
                "SELECT current_database()", String.class);

        assertThat(priceMemoryDb).isEqualTo(primaryDb);
    }

    private TransactionTemplate priceMemoryTransactionTemplate() {
        TransactionTemplate template = new TransactionTemplate(priceMemoryJdbcAccess.transactionManager());
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /**
     * 현재 커넥션의 timeout 설정을 ms 문자열로 읽는다.
     *
     * <p>{@code pg_settings.setting} 은 unit=ms 인 파라미터를 정수 문자열로 반환하므로
     * {@code SHOW lock_timeout} 의 단위 정규화("1000ms" → "1s")에 의존하지 않는다.
     */
    private TimeoutSettings readTimeouts() {
        return priceMemoryJdbcAccess.jdbcTemplate().queryForObject("""
                SELECT (SELECT setting FROM pg_settings WHERE name = 'lock_timeout') AS lock_timeout,
                       (SELECT setting FROM pg_settings WHERE name = 'statement_timeout') AS statement_timeout
                """, (rs, rowNum) -> new TimeoutSettings(
                rs.getString("lock_timeout"), rs.getString("statement_timeout")));
    }

    private void cleanup(UUID partnerId, UUID productId) {
        priceMemoryJdbcAccess.jdbcTemplate().update(
                "DELETE FROM partner_product_price_memory WHERE partner_id = ? AND product_id = ?",
                partnerId, productId);
    }

    private record TimeoutSettings(String lockTimeoutMs, String statementTimeoutMs) {
    }
}
