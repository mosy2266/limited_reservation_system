package reservation.limited.messaging;

import reservation.limited.outbox.OutboxEvent;

public interface MessagePublisher {

    void publish(OutboxEvent event);
}
