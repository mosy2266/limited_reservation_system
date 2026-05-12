package reservation.limited.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reservation.limited.outbox.OutboxEvent;

@Component
public class KafkaMessagePublisher implements MessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String bookingEventsTopic;

    public KafkaMessagePublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.booking-events-topic}") String bookingEventsTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.bookingEventsTopic = bookingEventsTopic;
    }

    @Override
    public void publish(OutboxEvent event) {
        // aggregateId를 key로 사용해 같은 예약 이벤트가 같은 Kafka partition에 순서대로 기록되게 한다.
        kafkaTemplate.send(bookingEventsTopic, String.valueOf(event.getAggregateId()), event.getPayload()).join();
    }
}
