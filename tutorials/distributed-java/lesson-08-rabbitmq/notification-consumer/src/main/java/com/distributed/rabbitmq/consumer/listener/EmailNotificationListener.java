package com.distributed.rabbitmq.consumer.listener;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Email Notification Consumer với manual acknowledgment.
 * Khi fail: basicNack với requeue=false → message đi vào DLX.
 */
@Component
public class EmailNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationListener.class);

    @RabbitListener(queues = "notifications.email.queue",
                    containerFactory = "rabbitListenerContainerFactory")
    public void handleEmail(Map<String, Object> message, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        log.info("Processing email notification: {}", message.get("recipient"));
        try {
            // Simulate email sending (100ms)
            Thread.sleep(100);
            log.info("Email sent successfully to: {}", message.get("recipient"));
            // ACK: Xóa message khỏi queue
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
            // NACK với requeue=false → message chuyển sang DLX/DLQ
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
