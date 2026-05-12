package reservation.limited.outbox;

import org.junit.jupiter.api.Test;
import reservation.limited.messaging.MessagePublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxPublisherTest {

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final MessagePublisher messagePublisher = mock(MessagePublisher.class);

    @Test
    void publishPendingEventsMarksEventPublishedWhenMessagePublishSucceeds() {
        OutboxEvent event = OutboxEvent.pending("BOOKING", 1L, "BOOKING_CONFIRMED", "{}");
        given(outboxEventRepository
                .findTop100ByStatusAndNextRetryAtIsNullOrStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
                        any(), any(), any()
                ))
                .willReturn(List.of(event));
        OutboxPublisher publisher = new OutboxPublisher(outboxEventRepository, messagePublisher, 5);

        publisher.publishPendingEvents();

        verify(messagePublisher).publish(event);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void publishPendingEventsSchedulesRetryWhenMessagePublishFails() {
        OutboxEvent event = OutboxEvent.pending("BOOKING", 1L, "BOOKING_CONFIRMED", "{}");
        given(outboxEventRepository
                .findTop100ByStatusAndNextRetryAtIsNullOrStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
                        any(), any(), any()
                ))
                .willReturn(List.of(event));
        doThrow(new RuntimeException("kafka down")).when(messagePublisher).publish(event);
        OutboxPublisher publisher = new OutboxPublisher(outboxEventRepository, messagePublisher, 5);

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextRetryAt()).isNotNull();
    }
}
