package reservation.limited.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    private LocalDateTime nextRetryAt;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static OutboxEvent pending(String aggregateType, Long aggregateId, String eventType, String payload) {
        return new OutboxEvent(aggregateType, aggregateId, eventType, payload);
    }

    public void markPublished() {
        // Kafka 발행 성공 시 더 이상 재발행되지 않도록 상태와 발행 시각을 기록한다.
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.updatedAt = this.publishedAt;
    }

    public void markFailedForRetry(int maxRetryCount) {
        // Kafka 장애 시 재시도 가능 상태로 남기고, 한도를 넘으면 FAILED로 고정한다.
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
        if (retryCount >= maxRetryCount) {
            this.status = OutboxStatus.FAILED;
            this.nextRetryAt = null;
            return;
        }
        this.status = OutboxStatus.PENDING;
        this.nextRetryAt = updatedAt.plusSeconds(retryDelaySeconds());
    }

    private long retryDelaySeconds() {
        return Math.min(60L, Math.max(1L, retryCount) * 5L);
    }
}
