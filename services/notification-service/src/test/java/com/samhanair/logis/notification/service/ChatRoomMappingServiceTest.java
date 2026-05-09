package com.samhanair.logis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.notification.domain.PartnerChatRoomMapping;
import com.samhanair.logis.notification.dto.ChatRoomMappingCreateRequest;
import com.samhanair.logis.notification.repository.PartnerChatRoomMappingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ChatRoomMappingService} 단위 테스트 — CRUD 4 시나리오.
 *
 * <ol>
 *   <li>create — 정상 등록</li>
 *   <li>create — 활성 중복 → CONFLICT</li>
 *   <li>delete — 존재하는 매핑 soft-delete</li>
 *   <li>delete — 미존재 → NOT_FOUND</li>
 * </ol>
 */
class ChatRoomMappingServiceTest {

    private PartnerChatRoomMappingRepository repository;
    private ChatRoomMappingService service;

    @BeforeEach
    void setUp() {
        repository = mock(PartnerChatRoomMappingRepository.class);
        service = new ChatRoomMappingService(repository);
        lenient().when(repository.save(any(PartnerChatRoomMapping.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("create — 정상 등록 (MANUAL source)")
    void create_normal_savesManual() {
        when(repository.findByPartnerCodeAndChatRoomName(anyString(), anyString()))
                .thenReturn(Optional.empty());

        ChatRoomMappingCreateRequest req = new ChatRoomMappingCreateRequest(
                "P-001", "에어디자이너 주식회사", "에어디자이너 발주방");

        PartnerChatRoomMapping saved = service.create(req);

        assertThat(saved.getPartnerCode()).isEqualTo("P-001");
        assertThat(saved.getPartnerBusinessNameSnapshot()).isEqualTo("에어디자이너 주식회사");
        assertThat(saved.getChatRoomName()).isEqualTo("에어디자이너 발주방");
        verify(repository).save(any(PartnerChatRoomMapping.class));
    }

    @Test
    @DisplayName("create — 활성 중복 → CONFLICT")
    void create_duplicate_throwsConflict() {
        PartnerChatRoomMapping existing = PartnerChatRoomMapping.manual(
                "P-001", "기존 사업자", "기존 발주방");
        when(repository.findByPartnerCodeAndChatRoomName("P-001", "기존 발주방"))
                .thenReturn(Optional.of(existing));

        ChatRoomMappingCreateRequest req = new ChatRoomMappingCreateRequest(
                "P-001", "신규 사업자", "기존 발주방");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("delete — 존재 매핑 soft-delete")
    void delete_existing_marksDeleted() {
        UUID id = UUID.randomUUID();
        PartnerChatRoomMapping entity = PartnerChatRoomMapping.manual(
                "P-001", "사업자", "발주방");
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        service.delete(id, "admin-1");

        assertThat(entity.getIsDeleted()).isTrue();
        assertThat(entity.getDeletedBy()).isEqualTo("admin-1");
        verify(repository).save(entity);
    }

    @Test
    @DisplayName("delete — 미존재 → NOT_FOUND")
    void delete_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id, "admin-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("findAll — repository 위임 정렬")
    void findAll_delegates() {
        when(repository.findAllByOrderByPartnerCodeAscChatRoomNameAsc())
                .thenReturn(List.of(
                        PartnerChatRoomMapping.manual("P-001", "A", "방A"),
                        PartnerChatRoomMapping.manual("P-002", "B", "방B")));

        List<PartnerChatRoomMapping> result = service.findAll();

        assertThat(result).hasSize(2);
    }
}
