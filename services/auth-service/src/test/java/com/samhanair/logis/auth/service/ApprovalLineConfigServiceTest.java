package com.samhanair.logis.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.samhanair.logis.approval.StepType;
import com.samhanair.logis.auth.domain.ApprovalLineConfig;
import com.samhanair.logis.auth.repository.ApprovalLineConfigRepository;
import com.samhanair.logis.auth.repository.PermissionGroupRepository;
import com.samhanair.logis.auth.web.dto.ApprovalLineRoleView;
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
class ApprovalLineConfigServiceTest {

    @Mock ApprovalLineConfigRepository repository;
    @Mock PermissionGroupRepository groupRepository;
    @InjectMocks ApprovalLineConfigService service;

    /** 리플렉션으로 테스트 픽스처 엔티티 생성(생성자 protected). */
    static ApprovalLineConfig role(int seq, String label, StepType type) {
        try {
            var ctor = ApprovalLineConfig.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ApprovalLineConfig c = ctor.newInstance();
            set(c, "id", UUID.randomUUID());
            set(c, "documentType", "SLIP_OUTBOUND");
            set(c, "sequence", seq);
            set(c, "label", label);
            set(c, "stepType", type);
            set(c, "required", true);
            return c;
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    static void set(Object o, String f, Object v) throws Exception {
        Field fld = ApprovalLineConfig.class.getDeclaredField(f); fld.setAccessible(true); fld.set(o, v);
    }

    @Test
    void listRoles_은_sequence순_역할을_반환한다() {
        when(repository.findByDocumentTypeOrderBySequenceAsc("SLIP_OUTBOUND"))
                .thenReturn(List.of(role(0, "작성자", StepType.CREATOR), role(1, "출고인", StepType.GROUP)));
        List<ApprovalLineRoleView> views = service.listRoles("SLIP_OUTBOUND");
        assertThat(views).hasSize(2);
        assertThat(views.get(0).label()).isEqualTo("작성자");
        assertThat(views.get(1).stepType()).isEqualTo(StepType.GROUP);
    }

    @Test
    void updateRole_은_GROUP역할에_권한그룹과_필수를_갱신한다() {
        UUID id = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        ApprovalLineConfig group = role(1, "출고인", StepType.GROUP);
        when(repository.findById(id)).thenReturn(Optional.of(group));
        when(repository.save(group)).thenReturn(group);
        ApprovalLineRoleView view = service.updateRole(id, groupId, false);
        assertThat(view.approverGroupId()).isEqualTo(groupId);
        assertThat(view.required()).isFalse();
    }

    @Test
    void updateRole_은_CREATOR역할에_권한그룹지정시_거부한다() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(role(0, "작성자", StepType.CREATOR)));
        assertThatThrownBy(() -> service.updateRole(id, UUID.randomUUID(), true))
                .hasMessageContaining("GROUP 역할");
    }

    @Test
    void updateRole_은_미존재시_404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateRole(id, null, true))
                .hasMessageContaining("찾을 수 없습니다");
    }
}
