package reservation.limited.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void pendingCreatesPendingEvent() {
        OutboxEvent event = OutboxEvent.pending("BOOKING", 1L, "BOOKING_CONFIRMED", "{}");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getAggregateType()).isEqualTo("BOOKING");
        assertThat(event.getAggregateId()).isEqualTo(1L);
    }

    @Test
    void markPublishedChangesStatusAndPublishedAt() {
        OutboxEvent event = OutboxEvent.pending("BOOKING", 1L, "BOOKING_CONFIRMED", "{}");

        event.markPublished();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void markFailedForRetryKeepsPendingBeforeMaxRetry() {
        OutboxEvent event = OutboxEvent.pending("BOOKING", 1L, "BOOKING_CONFIRMED", "{}");

        event.markFailedForRetry(5);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextRetryAt()).isNotNull();
    }

    @Test
    void markFailedForRetryChangesStatusToFailedWhenRetryLimitIsReached() {
        OutboxEvent event = OutboxEvent.pending("BOOKING", 1L, "BOOKING_CONFIRMED", "{}");

        event.markFailedForRetry(1);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextRetryAt()).isNull();
    }
}
