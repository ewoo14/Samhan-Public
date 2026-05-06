package com.samhanair.logis.notification.repository;

import com.samhanair.logis.notification.domain.NotificationLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 발송 이력 저장소 — request 단건 + sent_at 역순. */
@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findAllByRequest_IdOrderBySentAtDesc(UUID requestId);
}
