package com.samhanair.logis.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.samhanair.logis.approval.StepType;
import com.samhanair.logis.auth.domain.AccountGroup;
import com.samhanair.logis.auth.domain.ApprovalLineApprover;
import com.samhanair.logis.auth.domain.ApprovalLineConfig;
import com.samhanair.logis.auth.domain.ApproverType;
import com.samhanair.logis.auth.repository.AccountGroupRepository;
import com.samhanair.logis.auth.repository.ApprovalLineApproverRepository;
import com.samhanair.logis.auth.repository.ApprovalLineConfigRepository;
import com.samhanair.logis.auth.web.dto.ApprovalLineAuthorizeResponse;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalLineAuthorizationServiceTest {

    private static final String DOCUMENT_TYPE = "SLIP_OUTBOUND";
    private static final String ACTION_KEY = "OUTBOUND_DISPATCH";

    @Mock ApprovalLineConfigRepository configRepository;
    @Mock ApprovalLineApproverRepository approverRepository;
    @Mock AccountGroupRepository accountGroupRepository;
    @InjectMocks ApprovalLineAuthorizationService service;

    @Test
    void authorize_결재자0명은_configuredFalse_allowedFalse() {
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(configRepository.findFirstByDocumentTypeAndActionKeyAndIsDeletedFalseOrderBySequenceAsc(DOCUMENT_TYPE, ACTION_KEY))
                .thenReturn(Optional.of(role(roleId)));
        when(approverRepository.findByConfigRoleIdAndIsDeletedFalse(roleId)).thenReturn(List.of());

        ApprovalLineAuthorizeResponse result = service.authorize(DOCUMENT_TYPE, ACTION_KEY, userId);

        assertThat(result.configured()).isFalse();
        assertThat(result.allowed()).isFalse();
    }

    @Test
    void authorize_USER_결재자_일치시_allowedTrue() {
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(configRepository.findFirstByDocumentTypeAndActionKeyAndIsDeletedFalseOrderBySequenceAsc(DOCUMENT_TYPE, ACTION_KEY))
                .thenReturn(Optional.of(role(roleId)));
        when(approverRepository.findByConfigRoleIdAndIsDeletedFalse(roleId))
                .thenReturn(List.of(ApprovalLineApprover.create(roleId, ApproverType.USER, userId)));

        ApprovalLineAuthorizeResponse result = service.authorize(DOCUMENT_TYPE, ACTION_KEY, userId);

        assertThat(result.configured()).isTrue();
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void authorize_SLIP_INBOUND_USER결재자_일치시_allowedTrue() {
        // A2-3 회귀 가드 — authorize 는 documentType/actionKey generic. SLIP_INBOUND/INBOUND_RECEIVE 도 동일 동작.
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(configRepository.findFirstByDocumentTypeAndActionKeyAndIsDeletedFalseOrderBySequenceAsc(
                "SLIP_INBOUND", "INBOUND_RECEIVE")).thenReturn(Optional.of(role(roleId)));
        when(approverRepository.findByConfigRoleIdAndIsDeletedFalse(roleId))
                .thenReturn(List.of(ApprovalLineApprover.create(roleId, ApproverType.USER, userId)));

        ApprovalLineAuthorizeResponse result = service.authorize("SLIP_INBOUND", "INBOUND_RECEIVE", userId);

        assertThat(result.configured()).isTrue();
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void authorize_GROUP_소속시_allowedTrue() {
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        when(configRepository.findFirstByDocumentTypeAndActionKeyAndIsDeletedFalseOrderBySequenceAsc(DOCUMENT_TYPE, ACTION_KEY))
                .thenReturn(Optional.of(role(roleId)));
        when(approverRepository.findByConfigRoleIdAndIsDeletedFalse(roleId))
                .thenReturn(List.of(ApprovalLineApprover.create(roleId, ApproverType.GROUP, groupId)));
        when(accountGroupRepository.findByAccountIdAndIsDeletedFalseOrderByGroupIdAsc(userId))
                .thenReturn(List.of(AccountGroup.assign(userId, groupId)));

        ApprovalLineAuthorizeResponse result = service.authorize(DOCUMENT_TYPE, ACTION_KEY, userId);

        assertThat(result.configured()).isTrue();
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void authorize_비결재자는_configuredTrue_allowedFalse() {
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(configRepository.findFirstByDocumentTypeAndActionKeyAndIsDeletedFalseOrderBySequenceAsc(DOCUMENT_TYPE, ACTION_KEY))
                .thenReturn(Optional.of(role(roleId)));
        when(approverRepository.findByConfigRoleIdAndIsDeletedFalse(roleId))
                .thenReturn(List.of(
                        ApprovalLineApprover.create(roleId, ApproverType.USER, UUID.randomUUID()),
                        ApprovalLineApprover.create(roleId, ApproverType.GROUP, UUID.randomUUID())));
        when(accountGroupRepository.findByAccountIdAndIsDeletedFalseOrderByGroupIdAsc(userId))
                .thenReturn(List.of(AccountGroup.assign(userId, UUID.randomUUID())));

        ApprovalLineAuthorizeResponse result = service.authorize(DOCUMENT_TYPE, ACTION_KEY, userId);

        assertThat(result.configured()).isTrue();
        assertThat(result.allowed()).isFalse();
    }

    @Test
    void authorize_userId_null이면_configuredTrue_allowedFalse() {
        UUID roleId = UUID.randomUUID();
        when(configRepository.findFirstByDocumentTypeAndActionKeyAndIsDeletedFalseOrderBySequenceAsc(DOCUMENT_TYPE, ACTION_KEY))
                .thenReturn(Optional.of(role(roleId)));
        when(approverRepository.findByConfigRoleIdAndIsDeletedFalse(roleId))
                .thenReturn(List.of(ApprovalLineApprover.create(roleId, ApproverType.USER, UUID.randomUUID())));

        ApprovalLineAuthorizeResponse result = service.authorize(DOCUMENT_TYPE, ACTION_KEY, null);

        assertThat(result.configured()).isTrue();
        assertThat(result.allowed()).isFalse();
    }

    @Test
    void authorize_USER결재자만_불일치시_accountGroup조회없이_allowedFalse() {
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(configRepository.findFirstByDocumentTypeAndActionKeyAndIsDeletedFalseOrderBySequenceAsc(DOCUMENT_TYPE, ACTION_KEY))
                .thenReturn(Optional.of(role(roleId)));
        when(approverRepository.findByConfigRoleIdAndIsDeletedFalse(roleId))
                .thenReturn(List.of(ApprovalLineApprover.create(roleId, ApproverType.USER, UUID.randomUUID())));

        ApprovalLineAuthorizeResponse result = service.authorize(DOCUMENT_TYPE, ACTION_KEY, userId);

        assertThat(result.configured()).isTrue();
        assertThat(result.allowed()).isFalse();
        // GROUP 결재자 0 → account_groups 조회 early-return(불필요 쿼리 미발생)
        org.mockito.Mockito.verifyNoInteractions(accountGroupRepository);
    }

    @Test
    void authorize_미존재_actionKey는_configuredFalse_allowedFalse() {
        UUID userId = UUID.randomUUID();
        when(configRepository.findFirstByDocumentTypeAndActionKeyAndIsDeletedFalseOrderBySequenceAsc(DOCUMENT_TYPE, "UNKNOWN"))
                .thenReturn(Optional.empty());

        ApprovalLineAuthorizeResponse result = service.authorize(DOCUMENT_TYPE, "UNKNOWN", userId);

        assertThat(result.configured()).isFalse();
        assertThat(result.allowed()).isFalse();
    }

    private static ApprovalLineConfig role(UUID id) {
        try {
            var ctor = ApprovalLineConfig.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ApprovalLineConfig role = ctor.newInstance();
            set(role, "id", id);
            set(role, "documentType", DOCUMENT_TYPE);
            set(role, "sequence", 1);
            set(role, "label", "출고인");
            set(role, "stepType", StepType.GROUP);
            set(role, "actionKey", ACTION_KEY);
            set(role, "required", true);
            return role;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void set(ApprovalLineConfig target, String name, Object value) throws Exception {
        Field field = ApprovalLineConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
