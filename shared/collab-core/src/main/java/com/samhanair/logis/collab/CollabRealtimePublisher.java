package com.samhanair.logis.collab;

import com.samhanair.logis.shared.realtime.broker.RealtimeBroker;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 협업 SSE publish 시점 통일 게이트웨이.
 *
 * <p>{@code @Transactional} 내부 호출은 커밋 성공 이후에만 발화하고, 트랜잭션이 없는 경로는
 * 즉시 발화한다. 롤백된 댓글/제안/revision 변경이 클라이언트에 헛이벤트로 노출되는 것을 막는다.
 */
public class CollabRealtimePublisher {

    private final RealtimeBroker broker;

    public CollabRealtimePublisher(RealtimeBroker broker) {
        this.broker = broker;
    }

    /** 문서 채널 SSE 이벤트를 커밋 후 publish 한다. */
    public void publish(UUID documentId, String event, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broker.publish(documentId, event, payload);
                }
            });
        } else {
            broker.publish(documentId, event, payload);
        }
    }
}
