package com.distributed.rabbitmq.producer.controller;
import com.distributed.rabbitmq.producer.model.NotificationEvent;
import com.distributed.rabbitmq.producer.service.NotificationPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationPublisher publisher;
    public NotificationController(NotificationPublisher publisher) { this.publisher = publisher; }

    @PostMapping
    public ResponseEntity<Map<String, Object>> send(@RequestBody NotificationRequest req) {
        NotificationEvent event = new NotificationEvent(
            UUID.randomUUID().toString(), req.type(), req.recipient(),
            req.subject(), req.body(), req.priority() > 0 ? req.priority() : 5, Instant.now());
        switch (event.type()) {
            case EMAIL -> publisher.publishEmailNotification(event);
            case SMS   -> publisher.publishSmsNotification(event);
            default    -> publisher.publishBroadcast(event);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
            "id", event.id(), "status", "QUEUED", "type", event.type()));
    }

    record NotificationRequest(NotificationEvent.Type type, String recipient,
                               String subject, String body, int priority) {}
}
