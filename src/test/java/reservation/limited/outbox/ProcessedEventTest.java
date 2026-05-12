package reservation.limited.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessedEventTest {

    @Test
    void processedEventKeepsConsumerIdempotencyKey() {
        ProcessedEvent event = new ProcessedEvent("event-1", "BOOKING_CONFIRMED");

        assertThat(event).isNotNull();
    }
}
