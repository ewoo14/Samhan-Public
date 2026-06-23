package com.samhanair.logis.accounting.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.accounting.client.PartnerLookupClient;
import com.samhanair.logis.accounting.client.PartnerSummary;
import com.samhanair.logis.accounting.domain.Journal;
import com.samhanair.logis.accounting.domain.JournalLine;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JournalSeederTest {

    @Test
    @DisplayName("SLIP_ISSUE 참조 전표번호는 SlipSeeder 와 동일하게 일련번호 0-padding 없이 생성한다")
    void pickSlipNoUsesNoPaddedSequence() throws Exception {
        Method pickSlipNo = JournalSeeder.class.getDeclaredMethod("pickSlipNo", int.class);
        pickSlipNo.setAccessible(true);

        // 적요/참조 텍스트의 전표번호 표기를 slip-service SlipSeeder.formatSlipNo 와 동일한 포맷
        // (yyyy/MM/dd-N, 일련번호 0제거)으로 산출하는지 검증한다.
        // (slips.id 는 random PK, journal source_ref_id 는 번호 hash 라 cross-DB row 매칭은 불가 —
        //  여기서 검증하는 것은 전표번호 텍스트 포맷 일관성뿐이다.)
        assertThat((String) pickSlipNo.invoke(new JournalSeeder(null, null), 1))
                .isEqualTo("2026/04/01-1");
    }

    @Test
    @DisplayName("DEV 분개 seed partnerId는 partnerCode lookup 실제 UUID를 우선 사용하고 실패 시 결정 UUID로 fallback한다")
    void resolvePartnerIdsByCodeUsesLookupUuidAndFallbacksGracefully() throws Exception {
        PartnerLookupClient partnerLookupClient = mock(PartnerLookupClient.class);
        UUID realPartnerId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        when(partnerLookupClient.findByPartnerCode(anyString()))
                .thenAnswer(invocation -> {
                    String partnerCode = invocation.getArgument(0, String.class);
                    if ("P-2026-0001".equals(partnerCode)) {
                        return Optional.of(new PartnerSummary(
                                realPartnerId, partnerCode, "실거래처 1", "123-45-67890", "서울"));
                    }
                    if ("P-2026-0002".equals(partnerCode)) {
                        throw new RuntimeException("partner-service down");
                    }
                    return Optional.empty();
                });

        Method resolvePartnerIdsByCode = JournalSeeder.class.getDeclaredMethod("resolvePartnerIdsByCode");
        resolvePartnerIdsByCode.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, UUID> result = (Map<String, UUID>) resolvePartnerIdsByCode.invoke(
                new JournalSeeder(null, partnerLookupClient));

        assertThat(result).hasSize(50);
        assertThat(result.get("P-2026-0001")).isEqualTo(realPartnerId);
        assertThat(result.get("P-2026-0002"))
                .isEqualTo(JournalSeeder.deterministicId("partner", "P-2026-0002"));
        assertThat(result.get("P-2026-0050"))
                .isEqualTo(JournalSeeder.deterministicId("partner", "P-2026-0050"));
        verify(partnerLookupClient, times(50)).findByPartnerCode(anyString());
        verify(partnerLookupClient, times(1)).findByPartnerCode("P-2026-0001");
    }

    @Test
    @DisplayName("DEV 분개 라인의 partner_id는 partnerCode 캐시에 담긴 실제 거래처 UUID를 사용한다")
    void buildJournalLineUsesResolvedPartnerIdFromCache() throws Exception {
        UUID realPartnerId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        JournalSeeder seeder = new JournalSeeder(null, null);

        Method pickSpec = JournalSeeder.class.getDeclaredMethod("pickSpec", int.class);
        pickSpec.setAccessible(true);
        Object spec = pickSpec.invoke(seeder, 1);

        Class<?> journalSpecClass = Class.forName(
                "com.samhanair.logis.accounting.seed.JournalSeeder$JournalSpec");
        Method buildJournal = JournalSeeder.class.getDeclaredMethod(
                "buildJournal",
                int.class,
                journalSpecClass,
                String.class,
                LocalDate.class,
                UUID.class,
                Map.class);
        buildJournal.setAccessible(true);

        Journal journal = (Journal) buildJournal.invoke(
                seeder,
                1,
                spec,
                "2026/01/01-1",
                LocalDate.of(2026, 1, 1),
                JournalSeeder.deterministicId("journal", "seq:1"),
                Map.of("P-2026-0001", realPartnerId));

        assertThat(journal.getLines())
                .extracting(JournalLine::getPartnerId)
                .containsOnly(realPartnerId);
    }
}
