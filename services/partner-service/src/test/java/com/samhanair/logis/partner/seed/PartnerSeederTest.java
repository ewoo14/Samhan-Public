package com.samhanair.logis.partner.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.domain.PartnerStatus;
import com.samhanair.logis.partner.repository.PartnerRepository;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Stage 1 PartnerSeeder 단위 테스트 — idempotency + 도메인 메서드 호출 검증.
 *
 * <p>Spring context 미사용 (mock repository) — 50건 build 의 결정적 부분 검증.
 */
@ExtendWith(MockitoExtension.class)
class PartnerSeederTest {

    @Mock
    private PartnerRepository partnerRepository;

    @InjectMocks
    private PartnerSeeder seeder;

    @Test
    void firstRunCreatesAll50Partners() {
        when(partnerRepository.existsByPartnerCode(anyString())).thenReturn(false);

        seeder.run();

        ArgumentCaptor<Partner> captor = ArgumentCaptor.forClass(Partner.class);
        verify(partnerRepository, times(50)).save(captor.capture());

        // partnerCode unique 50건
        Set<String> codes = new HashSet<>();
        for (Partner p : captor.getAllValues()) {
            codes.add(p.getPartnerCode());
        }
        assertThat(codes).hasSize(50);
        assertThat(codes).contains("P-2026-0001", "P-2026-0050");
    }

    @Test
    void idempotentRunSkipsExisting() {
        // 이미 30건 존재 가정
        when(partnerRepository.existsByPartnerCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            int seq = Integer.parseInt(code.substring(code.length() - 4));
            return seq <= 30;
        });

        seeder.run();

        // 31~50 = 20건만 신규 INSERT
        verify(partnerRepository, times(20)).save(any(Partner.class));
    }

    @Test
    void allRunsAreNoOpWhenAllExist() {
        when(partnerRepository.existsByPartnerCode(anyString())).thenReturn(true);

        seeder.run();

        verify(partnerRepository, never()).save(any(Partner.class));
    }

    @Test
    void everyTenthPartnerIsSuspended() {
        when(partnerRepository.existsByPartnerCode(anyString())).thenReturn(false);

        seeder.run();

        ArgumentCaptor<Partner> captor = ArgumentCaptor.forClass(Partner.class);
        verify(partnerRepository, times(50)).save(captor.capture());

        long suspended = captor.getAllValues().stream()
                .filter(p -> p.getStatus() == PartnerStatus.SUSPENDED)
                .count();
        assertThat(suspended).isEqualTo(5L); // seq 10/20/30/40/50
    }

    @Test
    void bizNoIsUniquePerSeed() {
        // ensure no biz_no collision in deterministic generator
        when(partnerRepository.existsByPartnerCode(anyString())).thenReturn(false);
        // need lenient for test without other stubs
        lenient().when(partnerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        seeder.run();

        ArgumentCaptor<Partner> captor = ArgumentCaptor.forClass(Partner.class);
        verify(partnerRepository, times(50)).save(captor.capture());

        Set<String> bizNos = new HashSet<>();
        for (Partner p : captor.getAllValues()) {
            bizNos.add(p.getBizNo());
        }
        assertThat(bizNos).hasSize(50);
    }
}
