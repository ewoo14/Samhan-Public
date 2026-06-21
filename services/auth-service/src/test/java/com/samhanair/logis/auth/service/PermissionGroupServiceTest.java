package com.samhanair.logis.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.auth.domain.ApproverType;
import com.samhanair.logis.auth.domain.PermissionGroup;
import com.samhanair.logis.auth.repository.AccountGroupRepository;
import com.samhanair.logis.auth.repository.ApprovalLineApproverRepository;
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
    @Mock ApprovalLineApproverRepository approvalLineApproverRepository;
    @InjectMocks PermissionGroupService service;

    @Test
    void softDelete_은_레거시_결재라인참조_권한그룹삭제를_차단한다() {
        UUID groupId = UUID.randomUUID();
        PermissionGroup group = PermissionGroup.create("결재라인 그룹", null);
        when(permissionGroupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        when(accountGroupRepository.countByGroupIdAndIsDeletedFalse(groupId)).thenReturn(0L);
        when(approvalLineConfigRepository.existsByApproverGroupIdAndIsDeletedFalse(groupId)).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(groupId))
                .hasMessageContaining("결재라인에 지정된 권한 그룹");

        verify(permissionGroupRepository, never()).save(group);
    }

    /** A2-1c 회귀 가드: 칩(approval_line_approver)으로 새로 추가된 GROUP 결재자도 삭제 차단해야 한다. */
    @Test
    void softDelete_은_신규_approver테이블_GROUP결재자_권한그룹삭제를_차단한다() {
        UUID groupId = UUID.randomUUID();
        PermissionGroup group = PermissionGroup.create("칩 결재 그룹", null);
        when(permissionGroupRepository.findByIdAndIsDeletedFalse(groupId)).thenReturn(Optional.of(group));
        when(accountGroupRepository.countByGroupIdAndIsDeletedFalse(groupId)).thenReturn(0L);
        when(approvalLineConfigRepository.existsByApproverGroupIdAndIsDeletedFalse(groupId)).thenReturn(false);
        when(approvalLineApproverRepository
                .existsByApproverTypeAndApproverRefIdAndIsDeletedFalse(ApproverType.GROUP, groupId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(groupId))
                .hasMessageContaining("결재라인에 지정된 권한 그룹");

        verify(permissionGroupRepository, never()).save(group);
    }
}
