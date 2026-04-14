package com.distributed.rabbitmq.producer.config;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Exchange/Queue/Binding configuration.
 * Khai báo tất cả infrastructure objects - RabbitMQ tự tạo nếu chưa tồn tại.
 */
@Configuration
public class RabbitMQConfig {

    // Exchange và Queue names
    public static final String NOTIFICATIONS_EXCHANGE = "notifications.exchange";
    public static final String EMAIL_QUEUE = "notifications.email.queue";
    public static final String SMS_QUEUE = "notifications.sms.queue";
    public static final String DLX = "notifications.dlx";
    public static final String DLQ = "notifications.dlq";

    // Topic Exchange: Routing linh hoạt với wildcard (*, #)
    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(NOTIFICATIONS_EXCHANGE, true, false);
    }

    // Dead Letter Exchange: Direct exchange nhận failed messages
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    // Email queue với DLX configured
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", "notification.dead")
                .withArgument("x-message-ttl", 300000)  // 5 phút TTL
                .build();
    }

    // SMS queue với DLX configured
    @Bean
    public Queue smsQueue() {
        return QueueBuilder.durable(SMS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", "notification.dead")
                .build();
    }

    // Dead Letter Queue - nhận tất cả failed messages
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    // Binding: email queue nhận messages khớp "notification.email.#"
    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue())
                .to(notificationsExchange())
                .with("notification.email.#");
    }

    // Binding: sms queue nhận messages khớp "notification.sms.#"
    @Bean
    public Binding smsBinding() {
        return BindingBuilder.bind(smsQueue())
                .to(notificationsExchange())
                .with("notification.sms.#");
    }

    // Binding: DLQ nhận tất cả dead letters
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("notification.dead");
    }

    // JSON message converter - tự động serialize/deserialize POJO
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // RabbitTemplate với JSON converter và publisher confirms
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());

        // Publisher confirm callback
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                System.err.println("Message NOT confirmed: " + cause);
            }
        });

        // Mandatory: trả về message nếu không route được đến queue nào
        template.setMandatory(true);
        return template;
    }
}
