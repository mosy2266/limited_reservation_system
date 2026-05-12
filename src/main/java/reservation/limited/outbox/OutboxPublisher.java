package reservation.limited.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reservation.limited.messaging.MessagePublisher;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final MessagePublisher messagePublisher;
    private final int maxRetryCount;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            MessagePublisher messagePublisher,
            @Value("${app.outbox.max-retry-count}") int maxRetryCount
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.messagePublisher = messagePublisher;
        this.maxRetryCount = maxRetryCount;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.outbox.publisher-fixed-delay}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository
                .findTop100ByStatusAndNextRetryAtIsNullOrStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        OutboxStatus.PENDING,
                        LocalDateTime.now()
                );

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            messagePublisher.publish(event);
            event.markPublished();
        } catch (Exception exception) {
            event.markFailedForRetry(maxRetryCount);
        }
    }
}
