package com.samhanair.logis.slip.price.repository;

import com.samhanair.logis.slip.price.service.PartnerProductPriceMemoryCommand;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.samhanair.logis.slip.config.SlipDataSourceConfig.PriceMemoryJdbcAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 거래처+품목 최근단가의 PostgreSQL set-based upsert 저장소.
 *
 * <p><b>전용 pool 결속 (R8-BE-4 / D-R8-2)</b>: JdbcTemplate 을 무자격으로 주입받지 않고
 * {@link PriceMemoryJdbcAccess} 에서 꺼낸다. 종전 {@code @RequiredArgsConstructor} 무자격 주입으로
 * 되돌리면 자동구성 {@code JdbcTemplate}(메인 {@code @Primary} DataSource)이 주입되어,
 * 가격기억 TM 이 연 트랜잭션에 <b>참여하지 못하고</b> tx 밖 autocommit 커넥션을 잡는다. 그러면
 * {@link #applyTransactionTimeouts} 의 {@code set_config(..., is_local=true)} 는 그 statement 의
 * 암묵 트랜잭션과 함께 즉시 사라져 {@link #upsertAll} 에 적용되지 않는다 (조용한 무력화 — mock
 * 기반 테스트로는 검출 불가. {@code PartnerProductPriceMemoryTimeoutIT} 가 실 PostgreSQL 에서
 * 같은 커넥션의 {@code pg_settings} 를 읽어 가드한다).
 */
@Repository
public class PartnerProductPriceMemoryBatchRepository {

    private static final String VALUE_PLACEHOLDERS = "(?, ?, ?, ?, ?, ?, ?, ?, FALSE)";

    private final JdbcTemplate jdbcTemplate;

    public PartnerProductPriceMemoryBatchRepository(PriceMemoryJdbcAccess priceMemoryJdbcAccess) {
        this.jdbcTemplate = priceMemoryJdbcAccess.jdbcTemplate();
    }

    /** 현재 가격기억 트랜잭션에만 PostgreSQL lock/statement timeout 을 적용한다. */
    public void applyTransactionTimeouts(int lockTimeoutMs, int statementTimeoutMs) {
        jdbcTemplate.queryForObject(
                "SELECT set_config('lock_timeout', ?, true), set_config('statement_timeout', ?, true)",
                (rs, rowNum) -> Boolean.TRUE,
                lockTimeoutMs + "ms",
                statementTimeoutMs + "ms");
    }

    /**
     * N개 가격기억을 단일 {@code INSERT ... ON CONFLICT} statement 로 저장한다.
     *
     * <p>{@code remembered_at} 이 기존 값보다 오래된 command 는 갱신하지 않는다. 실제 DB 변경 시각인
     * {@code modified_at} 은 flush 시각을 기록하여 감사 의미와 최신성 의미를 분리한다.
     *
     * @return 실제 insert/update 된 row 수. 오래된 command 는 0건으로 계산될 수 있다.
     */
    public int upsertAll(List<PartnerProductPriceMemoryCommand> commands, LocalDateTime flushedAt) {
        if (commands == null || commands.isEmpty()) {
            return 0;
        }
        String values = IntStream.range(0, commands.size())
                .mapToObj(index -> VALUE_PLACEHOLDERS)
                .collect(Collectors.joining(", "));
        String sql = """
                INSERT INTO partner_product_price_memory
                    (id, partner_id, product_id, unit_price, source, remembered_at,
                     created_at, created_by, is_deleted)
                VALUES %s
                ON CONFLICT (partner_id, product_id) DO UPDATE
                SET unit_price = EXCLUDED.unit_price,
                    source = EXCLUDED.source,
                    remembered_at = EXCLUDED.remembered_at,
                    modified_at = EXCLUDED.created_at,
                    modified_by = EXCLUDED.created_by,
                    deleted_at = NULL,
                    deleted_by = NULL,
                    is_deleted = FALSE
                WHERE partner_product_price_memory.remembered_at <= EXCLUDED.remembered_at
                """.formatted(values);

        return jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            int parameterIndex = 1;
            for (PartnerProductPriceMemoryCommand command : commands) {
                statement.setObject(parameterIndex++, UUID.randomUUID());
                statement.setObject(parameterIndex++, command.partnerId());
                statement.setObject(parameterIndex++, command.productId());
                statement.setBigDecimal(parameterIndex++, command.unitPrice());
                statement.setString(parameterIndex++, command.source());
                statement.setObject(parameterIndex++, command.rememberedAt());
                statement.setObject(parameterIndex++, flushedAt);
                statement.setString(parameterIndex++, command.actor());
            }
            return statement;
        });
    }
}
