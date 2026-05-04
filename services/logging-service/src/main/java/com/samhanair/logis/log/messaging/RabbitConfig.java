package com.samhanair.logis.log.messaging;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for audit logs.
 *
 *   - Exchange: {@code samhan.audit.exchange} (topic, durable)
 *   - Queue:    {@code samhan.audit.queue} bound with pattern {@code audit.#}
 *   - DLX:      {@code samhan.audit.dlx}
 *   - DLQ:      {@code samhan.audit.dlq}
 *
 * Producers publish with routing keys like {@code audit.slip},
 * {@code audit.account.login}, etc.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "samhan.audit.exchange";
    public static final String QUEUE = "samhan.audit.queue";
    public static final String DLX = "samhan.audit.dlx";
    public static final String DLQ = "samhan.audit.dlq";
    public static final String ROUTING_PATTERN = "audit.#";
    public static final String DLQ_ROUTING_KEY = "audit.dlq";

    @Bean
    TopicExchange auditExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    TopicExchange dlx() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    Queue auditQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", DLX,
                        "x-dead-letter-routing-key", DLQ_ROUTING_KEY
                ))
                .build();
    }

    @Bean
    Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding auditBinding(Queue auditQueue, TopicExchange auditExchange) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(ROUTING_PATTERN);
    }

    @Bean
    Binding dlqBinding(Queue auditDeadLetterQueue, TopicExchange dlx) {
        return BindingBuilder.bind(auditDeadLetterQueue).to(dlx).with(DLQ_ROUTING_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
