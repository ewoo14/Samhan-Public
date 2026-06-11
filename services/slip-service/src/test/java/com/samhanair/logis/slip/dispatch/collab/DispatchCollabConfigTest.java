package com.samhanair.logis.slip.dispatch.collab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.collab.CollabCommentService;
import com.samhanair.logis.collab.CollabDocumentType;
import com.samhanair.logis.collab.CollabRealtimePublisher;
import com.samhanair.logis.shared.realtime.broker.RealtimeBroker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 배차 협업 댓글 설정 테스트.
 *
 * <p>shared/collab-core 의 {@link CollabCommentService} 를 DispatchTask concrete entity 로
 * 실배선하는 첫 레퍼런스가 DISPATCH_TASK 문서 타입과 RealtimeBroker publish 를 보존하는지 검증한다.
 */
class DispatchCollabConfigTest {

    @Test
    void commentService_addDispatchTaskComment_persistsAndPublishes() {
        DispatchCollabCommentRepository repository =
                org.mockito.Mockito.mock(DispatchCollabCommentRepository.class);
        RealtimeBroker broker = org.mockito.Mockito.mock(RealtimeBroker.class);
        CollabRealtimePublisher publisher = new CollabRealtimePublisher(broker);
        CollabCommentService<DispatchCollabComment> service =
                new DispatchCollabConfig().dispatchCollabCommentService(repository, publisher);
        UUID taskId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        when(repository.save(any(DispatchCollabComment.class))).thenAnswer(inv -> {
            DispatchCollabComment comment = inv.getArgument(0);
            ReflectionTestUtils.setField(comment, "id", UUID.randomUUID());
            return comment;
        });

        DispatchCollabComment saved = service.add(
                CollabDocumentType.DISPATCH_TASK,
                taskId,
                "vehicleGroups[0]",
                authorId,
                "배차담당자",
                "1톤 차량 확인 필요",
                null);

        assertThat(saved.getDocumentType()).isEqualTo(CollabDocumentType.DISPATCH_TASK);
        assertThat(saved.getDocumentId()).isEqualTo(taskId);
        assertThat(saved.getAuthorName()).isEqualTo("배차담당자");
        verify(repository, times(1)).save(any(DispatchCollabComment.class));
        verify(broker, times(1))
                .publish(eq(taskId), eq(CollabCommentService.EVENT_COMMENT_CREATED), any());
    }

    @Test
    void commentService_listRecent_clampsLimitAndUsesDispatchDocumentType() {
        DispatchCollabCommentRepository repository =
                org.mockito.Mockito.mock(DispatchCollabCommentRepository.class);
        RealtimeBroker broker = org.mockito.Mockito.mock(RealtimeBroker.class);
        CollabRealtimePublisher publisher = new CollabRealtimePublisher(broker);
        CollabCommentService<DispatchCollabComment> service =
                new DispatchCollabConfig().dispatchCollabCommentService(repository, publisher);
        UUID taskId = UUID.randomUUID();
        when(repository.findRecent(eq(CollabDocumentType.DISPATCH_TASK), eq(taskId), any(Pageable.class)))
                .thenReturn(List.of());

        service.listRecent(CollabDocumentType.DISPATCH_TASK, taskId, 999);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findRecent(
                eq(CollabDocumentType.DISPATCH_TASK),
                eq(taskId),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize())
                .isEqualTo(CollabCommentService.MAX_RECENT_LIMIT);
    }

    @Test
    void repositoryAdapter_exposesFindByIdContract() {
        DispatchCollabCommentRepository repository =
                org.mockito.Mockito.mock(DispatchCollabCommentRepository.class);
        UUID commentId = UUID.randomUUID();
        when(repository.findById(commentId)).thenReturn(Optional.empty());

        assertThat(repository.findById(commentId)).isEmpty();
    }
}
