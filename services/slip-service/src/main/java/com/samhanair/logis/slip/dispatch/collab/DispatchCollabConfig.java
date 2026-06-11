package com.samhanair.logis.slip.dispatch.collab;

import com.samhanair.logis.collab.CollabCommentService;
import com.samhanair.logis.collab.CollabDocumentType;
import com.samhanair.logis.shared.realtime.broker.RealtimeBroker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;

/**
 * 배차 협업 댓글 bean 설정.
 *
 * <p>slip-service 의 {@link com.samhanair.logis.slip.realtime.SlipRealtimeBroker} 는
 * {@link RealtimeBroker} 구현체이므로 collab-core 서비스가 동일 SSE 채널을 재사용한다.
 */
@Configuration
public class DispatchCollabConfig {

    /** DispatchTask concrete comment service. */
    @Bean
    public CollabCommentService<DispatchCollabComment> dispatchCollabCommentService(
            DispatchCollabCommentRepository repository,
            RealtimeBroker broker) {
        return new CollabCommentService<>(
                new DispatchCommentRepositoryAdapter(repository),
                DispatchCollabComment::create,
                broker);
    }

    private record DispatchCommentRepositoryAdapter(DispatchCollabCommentRepository repository)
            implements CollabCommentService.CommentRepository<DispatchCollabComment> {

        @Override
        public DispatchCollabComment save(DispatchCollabComment comment) {
            return repository.save(comment);
        }

        @Override
        public Optional<DispatchCollabComment> findById(UUID commentId) {
            return repository.findById(commentId);
        }

        @Override
        public List<DispatchCollabComment> findRecent(CollabDocumentType documentType,
                                                      UUID documentId,
                                                      Pageable pageable) {
            return repository.findRecent(documentType, documentId, pageable);
        }
    }
}
