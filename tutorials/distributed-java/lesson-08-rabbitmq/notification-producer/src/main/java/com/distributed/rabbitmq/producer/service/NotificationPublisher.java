package com.distributed.rabbitmq.producer.service;
import com.distributed.rabbitmq.producer.config.RabbitMQConfig;
import com.distributed.rabbitmq.producer.model.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

/**
 * Service publish notifications đến RabbitMQ.
 * Routing key quyết định message đi vào queue nào qua Topic Exchange.
 */
@Service
public class NotificationPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);
    private final RabbitTemplate rabbitTemplate;
    public NotificationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** Gửi email notification với routing key "notification.email.transactional" */
    public void publishEmailNotification(NotificationEvent event) {
        String routingKey = "notification.email.transactional";
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        log.info("Publishing email notification: recipient={}, routingKey={}", event.recipient(), routingKey);
        rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATIONS_EXCHANGE, routingKey, event, correlation);
    }

    /** Gửi SMS notification với routing key "notification.sms.otp" */
    public void publishSmsNotification(NotificationEvent event) {
        String routingKey = "notification.sms.otp";
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        log.info("Publishing SMS notification: recipient={}", event.recipient());
        rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATIONS_EXCHANGE, routingKey, event, correlation);
    }

    /** Broadcast: routing key "notification.#" matches tất cả queues */
    public void publishBroadcast(NotificationEvent event) {
        log.info("Broadcasting notification to all queues");
        rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATIONS_EXCHANGE, "notification.all", event);
    }
}
