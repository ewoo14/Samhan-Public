package com.samhanair.logis.partner.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.repository.PartnerRepository;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class PartnerSeederTest {

    @Mock
    private PartnerRepository partnerRepository;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private PartnerSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new PartnerSeeder(partnerRepository, jdbcTemplate);
    }

    @Test
    void firstRunCreatesAll50PartnersViaJdbcWithDeterministicIds() {
        when(partnerRepository.existsByPartnerCode(anyString())).thenReturn(false);

        seeder.run();

        verify(partnerRepository, never()).save(any(Partner.class));

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate, times(50)).update(anyString(), paramsCaptor.capture());

        Set<String> codes = new HashSet<>();
        for (SqlParameterSource params : paramsCaptor.getAllValues()) {
            codes.add((String) params.getValue("partnerCode"));
        }
        assertThat(codes).hasSize(50);
        assertThat(codes).contains("P-2026-0001", "P-2026-0050");

        UUID expected = UUID.nameUUIDFromBytes(
                "samhan-seed:partner:P-2026-0001".getBytes(StandardCharsets.UTF_8));
        assertThat(paramsCaptor.getAllValues().get(0).getValue("id")).isEqualTo(expected);
        assertThat(expected.toString()).isEqualTo("8e809b05-1426-387c-a13e-14e53ffdb3ea");
    }

    @Test
    void idempotentRunSkipsExisting() {
        when(partnerRepository.existsByPartnerCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            int seq = Integer.parseInt(code.substring(code.length() - 4));
            return seq <= 30;
        });

        seeder.run();

        verify(jdbcTemplate, times(20)).update(anyString(), any(SqlParameterSource.class));
        verify(partnerRepository, never()).save(any(Partner.class));
    }

    @Test
    void allRunsAreNoOpWhenAllExist() {
        when(partnerRepository.existsByPartnerCode(anyString())).thenReturn(true);

        seeder.run();

        verify(jdbcTemplate, never()).update(anyString(), any(SqlParameterSource.class));
        verify(partnerRepository, never()).save(any(Partner.class));
    }

    @Test
    void everyTenthPartnerIsSuspended() {
        when(partnerRepository.existsByPartnerCode(anyString())).thenReturn(false);

        seeder.run();

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate, times(50)).update(anyString(), paramsCaptor.capture());

        long suspended = paramsCaptor.getAllValues().stream()
                .filter(params -> "SUSPENDED".equals(params.getValue("status")))
                .count();
        assertThat(suspended).isEqualTo(5L);
    }

    @Test
    void bizNoIsUniquePerSeed() {
        when(partnerRepository.existsByPartnerCode(anyString())).thenReturn(false);

        seeder.run();

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate, times(50)).update(anyString(), paramsCaptor.capture());

        Set<String> bizNos = new HashSet<>();
        for (SqlParameterSource params : paramsCaptor.getAllValues()) {
            bizNos.add((String) params.getValue("bizNo"));
        }
        assertThat(bizNos).hasSize(50);
    }
}
