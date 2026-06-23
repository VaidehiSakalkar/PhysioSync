package com.physiolink.exercise.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SessionEventProducer {

    private static final Logger log = LoggerFactory.getLogger(SessionEventProducer.class);
    private static final String TOPIC = "session.completed";

    private final KafkaTemplate<String, SessionCompletedEvent> kafkaTemplate;

    public SessionEventProducer(KafkaTemplate<String, SessionCompletedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(SessionCompletedEvent event) {
        kafkaTemplate.send(TOPIC, event.patientId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish session.completed event: {}", ex.getMessage());
                    } else {
                        log.info("Published session.completed for patient={} session={}",
                                event.patientId(), event.sessionLogId());
                    }
                });
    }
}
