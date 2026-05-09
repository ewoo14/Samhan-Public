package com.samhanair.logis.partner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.partner.domain.BlockedPartner;
import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.repository.BlockedPartnerRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Phase 10 PR-D Part B — {@link PartnerBlockService} 단위 테스트.
 *
 * <p>커버: block (정상) / 중복 차단 (CONFLICT) / partner 미존재 (NOT_FOUND) / unblock (soft-delete) /
 * unblock 미존재 (NOT_FOUND) / isBlocked 가드.
 */
@ExtendWith(MockitoExtension.class)
class PartnerBlockServiceTest {

    @Mock
    private BlockedPartnerRepository blockedPartnerRepository;

    @Mock
    private PartnerService partnerService;

    @InjectMocks
    private PartnerBlockService service;

    private Partner samplePartner() {
        return Partner.register("P-2026-0001", "999-88-77777", "(주)에어뱅크",
                null, null, BigDecimal.ZERO);
    }

    @Test
    void block_newPartner_persistsBlockedPartner() {
        Partner p = samplePartner();
        when(blockedPartnerRepository.existsByPartnerCodeAndIsDeletedFalse("P-2026-0001"))
                .thenReturn(false);
        when(partnerService.findByCode("P-2026-0001")).thenReturn(p);
        when(blockedPartnerRepository.save(any(BlockedPartner.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BlockedPartner result = service.block("P-2026-0001", "Notion 차단");

        ArgumentCaptor<BlockedPartner> captor = ArgumentCaptor.forClass(BlockedPartner.class);
        verify(blockedPartnerRepository).save(captor.capture());
        assertThat(captor.getValue().getPartnerCode()).isEqualTo("P-2026-0001");
        assertThat(captor.getValue().getPartnerBusinessNameSnapshot()).isEqualTo("(주)에어뱅크");
        assertThat(captor.getValue().getBlockReason()).isEqualTo("Notion 차단");
        assertThat(captor.getValue().getSource()).isEqualTo("MANUAL");
        assertThat(result).isNotNull();
    }

    @Test
    void block_alreadyBlocked_throwsConflict() {
        when(blockedPartnerRepository.existsByPartnerCodeAndIsDeletedFalse("P-2026-0001"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.block("P-2026-0001", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        verify(partnerService, never()).findByCode(anyString());
        verify(blockedPartnerRepository, never()).save(any());
    }

    @Test
    void block_partnerNotFound_propagatesNotFound() {
        when(blockedPartnerRepository.existsByPartnerCodeAndIsDeletedFalse(anyString()))
                .thenReturn(false);
        when(partnerService.findByCode("P-9999-9999"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "miss"));

        assertThatThrownBy(() -> service.block("P-9999-9999", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void block_csvOverload_savesNotionImportSource() {
        Partner p = samplePartner();
        LocalDateTime blockedAt = LocalDateTime.of(2026, 4, 26, 7, 36);
        when(blockedPartnerRepository.existsByPartnerCodeAndIsDeletedFalse("P-2026-0001"))
                .thenReturn(false);
        when(partnerService.findByCode("P-2026-0001")).thenReturn(p);
        when(blockedPartnerRepository.save(any(BlockedPartner.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.block("P-2026-0001", null, blockedAt, "NOTION_IMPORT", "(주)에어뱅크 CSV 입력");

        ArgumentCaptor<BlockedPartner> captor = ArgumentCaptor.forClass(BlockedPartner.class);
        verify(blockedPartnerRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("NOTION_IMPORT");
        assertThat(captor.getValue().getBlockedAt()).isEqualTo(blockedAt);
        assertThat(captor.getValue().getPartnerBusinessNameSnapshot()).isEqualTo("(주)에어뱅크 CSV 입력");
    }

    @Test
    void unblock_existing_marksDeleted() {
        UUID id = UUID.randomUUID();
        BlockedPartner entity = BlockedPartner.create("P-2026-0001", "(주)에어뱅크",
                null, LocalDateTime.now(), "MANUAL");
        when(blockedPartnerRepository.findById(id)).thenReturn(Optional.of(entity));

        service.unblock(id, "admin-1");

        assertThat(entity.getIsDeleted()).isTrue();
        assertThat(entity.getDeletedBy()).isEqualTo("admin-1");
    }

    @Test
    void unblock_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(blockedPartnerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unblock(id, "admin-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void isBlocked_delegatesToRepository() {
        when(blockedPartnerRepository.existsByPartnerCodeAndIsDeletedFalse("P-2026-0001"))
                .thenReturn(true);

        assertThat(service.isBlocked("P-2026-0001")).isTrue();

        when(blockedPartnerRepository.existsByPartnerCodeAndIsDeletedFalse("P-9999-9999"))
                .thenReturn(false);
        assertThat(service.isBlocked("P-9999-9999")).isFalse();
    }
}
