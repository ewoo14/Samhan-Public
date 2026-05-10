package com.samhanair.logis.slip.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.comment.domain.SlipComment;
import com.samhanair.logis.slip.comment.repository.SlipCommentRepository;
import com.samhanair.logis.slip.realtime.SlipRealtimeBroker;
import com.samhanair.logis.slip.repository.SlipRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PR-H1 BE — SlipCommentService 단위 테스트.
 *
 * <p>Test case:
 * <ol>
 *   <li>add — slip 존재 시 INSERT + broker.publish(comment.created) 호출</li>
 *   <li>add — slip 미존재 시 BusinessException(NOT_FOUND), broker.publish 미호출</li>
 *   <li>listRecent — 정상 limit, repository 호출 위임</li>
 *   <li>listRecent — limit clamp (0 → 1, 999 → MAX 100)</li>
 *   <li>softDelete — comment 존재 시 markDeleted + broker.publish(comment.deleted)</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SlipCommentServiceTest {

    @Mock private SlipCommentRepository commentRepository;
    @Mock private SlipRepository slipRepository;
    @Mock private SlipRealtimeBroker broker;

    @InjectMocks private SlipCommentService service;

    private UUID slipId;
    private UUID authorId;

    @BeforeEach
    void setUp() {
        slipId = UUID.randomUUID();
        authorId = UUID.randomUUID();
    }

    @Test
    void add_slipExists_insertsAndPublishes() {
        when(slipRepository.existsById(slipId)).thenReturn(true);
        // 영속화 시점에 부여되는 id 시뮬레이션 (UuidGenerator 가 production 환경에서 자동 부여)
        when(commentRepository.save(any(SlipComment.class)))
                .thenAnswer(inv -> {
                    SlipComment c = inv.getArgument(0);
                    ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
                    return c;
                });

        SlipComment saved = service.add(slipId, authorId, "홍길동", "검수 시작합니다");

        assertThat(saved.getSlipId()).isEqualTo(slipId);
        assertThat(saved.getAuthorName()).isEqualTo("홍길동");
        assertThat(saved.getBody()).isEqualTo("검수 시작합니다");
        verify(commentRepository, times(1)).save(any(SlipComment.class));
        verify(broker, times(1))
                .publish(eq(slipId), eq(SlipCommentService.EVENT_COMMENT_CREATED), any());
    }

    @Test
    void add_slipMissing_throwsNotFoundAndSkipsPublish() {
        when(slipRepository.existsById(slipId)).thenReturn(false);

        assertThatThrownBy(() -> service.add(slipId, authorId, "홍길동", "본문"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(commentRepository, never()).save(any(SlipComment.class));
        verify(broker, never()).publish(any(), anyString(), any());
    }

    @Test
    void listRecent_normalLimit_delegatesToRepository() {
        when(commentRepository.findBySlipIdOrderByCreatedAtDesc(eq(slipId), any(Pageable.class)))
                .thenReturn(List.of());

        service.listRecent(slipId, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findBySlipIdOrderByCreatedAtDesc(eq(slipId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    void listRecent_limitClamp_appliesMinAndMax() {
        when(commentRepository.findBySlipIdOrderByCreatedAtDesc(eq(slipId), any(Pageable.class)))
                .thenReturn(List.of());

        service.listRecent(slipId, 0);
        service.listRecent(slipId, 999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository, times(2))
                .findBySlipIdOrderByCreatedAtDesc(eq(slipId), captor.capture());
        List<Pageable> values = captor.getAllValues();
        assertThat(values.get(0).getPageSize()).isEqualTo(1);
        assertThat(values.get(1).getPageSize()).isEqualTo(SlipCommentService.MAX_RECENT_LIMIT);
    }

    @Test
    void softDelete_existingComment_marksDeletedAndPublishes() {
        SlipComment comment = SlipComment.create(slipId, authorId, "홍길동", "본문");
        UUID commentId = UUID.randomUUID();
        ReflectionTestUtils.setField(comment, "id", commentId);
        when(commentRepository.findById(commentId)).thenReturn(java.util.Optional.of(comment));

        service.softDelete(commentId, "user-42");

        assertThat(comment.getIsDeleted()).isTrue();
        assertThat(comment.getDeletedBy()).isEqualTo("user-42");
        verify(broker, times(1))
                .publish(eq(slipId), eq(SlipCommentService.EVENT_COMMENT_DELETED), any());
    }
}
