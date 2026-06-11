package com.samhanair.logis.slip.dispatch.collab;

import com.samhanair.logis.collab.CollabCommentService;
import com.samhanair.logis.collab.CollabDocumentType;
import com.samhanair.logis.collab.CollabRealtimePublisher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;

/**
 * 배차 협업 댓글 bean 설정.
 *
 * <p>{@link CollabRealtimePublisher} 가 slip-service 의 realtime broker 를 커밋 후 발화 경로로
 * 감싸므로 collab-core 서비스가 동일 SSE 채널을 안전하게 재사용한다.
 */
@Configuration
public class DispatchCollabConfig {

    /** DispatchTask concrete comment service. */
    @Bean
    public CollabCommentService<DispatchCollabComment> dispatchCollabCommentService(
            DispatchCollabCommentRepository repository,
            CollabRealtimePublisher publisher) {
        return new CollabCommentService<>(
                new DispatchCommentRepositoryAdapter(repository),
                DispatchCollabComment::create,
                publisher);
    }

    private record DispatchCommentRepositoryAdapter(DispatchCollabCommentRepository repository)
            implements CollabCommentService.CommentRepository<DispatchCollabComment> {

        @Override
        public DispatchCollabComment save(DispatchCollabComment comment) {
            return repository.save(comment);
        }

        @Override
        public Optional<DispatchCollabComment> findByIdAndDocumentTypeAndDocumentId(
                UUID commentId, CollabDocumentType documentType, UUID documentId) {
            return repository.findByIdAndDocumentTypeAndDocumentId(
                    commentId, documentType, documentId);
        }

        @Override
        public List<DispatchCollabComment> findRecent(CollabDocumentType documentType,
                                                      UUID documentId,
                                                      Pageable pageable) {
            return repository.findRecent(documentType, documentId, pageable);
        }
    }
}
