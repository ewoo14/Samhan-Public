package com.samhanair.logis.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.auth.domain.PermissionGroup;
import com.samhanair.logis.auth.repository.AccountGroupRepository;
import com.samhanair.logis.auth.repository.ApprovalLineConfigRepository;
import com.samhanair.logis.auth.repository.PermissionGroupRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionGroupServiceTest {

    @Mock PermissionGroupRepository permissionGroupRepository;
    @Mock AccountGroupRepository accountGroupRepository;
    @Mock ApprovalLineConfigRepository approvalLineConfigRepository;
    @InjectMocks PermissionGroupService service;

    @Test
    void softDelete_은_결재라인참조_권한그룹삭제를_차단한다() {
        UUID groupId = UUID.randomUUID();
        PermissionGroup group = PermissionGroup.create("결재라인 그룹", null);
        when(permissionGroupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        when(accountGroupRepository.countByGroupIdAndIsDeletedFalse(groupId)).thenReturn(0L);
        when(approvalLineConfigRepository.existsByApproverGroupIdAndIsDeletedFalse(groupId)).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(groupId))
                .hasMessageContaining("결재라인에 지정된 권한 그룹");

        verify(permissionGroupRepository, never()).save(group);
    }
}
