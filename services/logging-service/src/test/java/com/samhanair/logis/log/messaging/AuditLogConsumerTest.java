package com.samhanair.logis.log.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.samhanair.logis.log.domain.AuditLog;
import com.samhanair.logis.log.repository.AuditLogRepository;

@ExtendWith(MockitoExtension.class)
class AuditLogConsumerTest {

    @Mock
    private AuditLogRepository repository;

    @InjectMocks
    private AuditLogConsumer consumer;

    @Test
    void consume_persistsEvent_setsIngestedAt_andGeneratesIdWhenBlank() {
        AuditLogEvent event = new AuditLogEvent(
                null, // id blank → expect UUID generated
                "auth-service",
                "user-1",
                "MANAGER",
                "ACCOUNT_LOGIN",
                "ACCOUNT",
                "42",
                "로그인 성공",
                null,
                Map.of("ip", "127.0.0.1"),
                "127.0.0.1",
                "JUnit/5",
                Instant.parse("2026-05-04T10:15:30Z"));

        when(repository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository, times(1)).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getServiceName()).isEqualTo("auth-service");
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getAction()).isEqualTo("ACCOUNT_LOGIN");
        assertThat(saved.getOccurredAt()).isEqualTo(Instant.parse("2026-05-04T10:15:30Z"));
        assertThat(saved.getIngestedAt()).isNotNull();
    }

    @Test
    void consume_preservesProvidedId() {
        AuditLogEvent event = new AuditLogEvent(
                "fixed-id-123",
                "auth-service", "user-1", "MASTER", "SLIP_CREATE",
                "SLIP", "99", "전표 생성",
                null, null, "10.0.0.1", "JUnit/5",
                Instant.now());

        when(repository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        consumer.consume(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("fixed-id-123");
    }
}
