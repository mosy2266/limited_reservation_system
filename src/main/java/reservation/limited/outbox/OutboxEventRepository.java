package reservation.limited.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop100ByStatusAndNextRetryAtIsNullOrStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
            OutboxStatus nullRetryStatus,
            OutboxStatus dueRetryStatus,
            LocalDateTime now
    );
}
