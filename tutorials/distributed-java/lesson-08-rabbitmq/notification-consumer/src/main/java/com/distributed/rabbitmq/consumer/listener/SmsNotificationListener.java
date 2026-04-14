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
 * SMS Notification Consumer - Xử lý nhanh hơn email (OTP cần tức thì).
 */
@Component
public class SmsNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(SmsNotificationListener.class);

    @RabbitListener(queues = "notifications.sms.queue",
                    containerFactory = "rabbitListenerContainerFactory")
    public void handleSms(Map<String, Object> message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        log.info("Processing SMS notification: recipient={}", message.get("recipient"));
        try {
            Thread.sleep(30); // SMS provider call is fast
            log.info("SMS sent to: {}", message.get("recipient"));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to send SMS: {}", e.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
