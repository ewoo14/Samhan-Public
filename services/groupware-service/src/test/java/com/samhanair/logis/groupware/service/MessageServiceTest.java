package com.samhanair.logis.groupware.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.groupware.domain.Message;
import com.samhanair.logis.groupware.domain.MessageStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 메신저 도메인 단위 테스트 — 4 case:
 * <ol>
 *   <li>send 정상 흐름 (status=UNREAD, sentAt 적재)</li>
 *   <li>self-send 거부</li>
 *   <li>markRead 흐름 (UNREAD → READ + readAt 적재)</li>
 *   <li>수신자 외 markRead 호출 거부</li>
 * </ol>
 */
class MessageServiceTest {

    @Test
    void send_initialises_unread_with_sentAt_now() {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();

        Message m = Message.send(sender, recipient, "안녕하세요");

        assertThat(m.getStatus()).isEqualTo(MessageStatus.UNREAD);
        assertThat(m.getSentAt()).isNotNull();
        assertThat(m.getReadAt()).isNull();
        assertThat(m.getBody()).isEqualTo("안녕하세요");
    }

    @Test
    void send_blocks_self_send() {
        UUID self = UUID.randomUUID();

        assertThatThrownBy(() -> Message.send(self, self, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자기 자신");
    }

    @Test
    void markRead_by_recipient_transitions_unread_to_read() {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        Message m = Message.send(sender, recipient, "본문");

        m.markRead(recipient);

        assertThat(m.getStatus()).isEqualTo(MessageStatus.READ);
        assertThat(m.getReadAt()).isNotNull();
    }

    @Test
    void markRead_by_non_recipient_is_rejected() {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Message m = Message.send(sender, recipient, "본문");

        assertThatThrownBy(() -> m.markRead(other))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("수신자 본인만");
    }
}
