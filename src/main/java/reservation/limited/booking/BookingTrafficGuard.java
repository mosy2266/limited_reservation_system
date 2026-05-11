package reservation.limited.booking;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reservation.limited.common.BusinessException;
import reservation.limited.common.ErrorCode;

import java.time.Duration;
import java.util.Optional;

@Component
public class BookingTrafficGuard {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(10);
    private static final Duration PROCESSING_LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration USER_RATE_TTL = Duration.ofSeconds(1);

    private final StringRedisTemplate redisTemplate;

    public BookingTrafficGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<GuardToken> tryEnter(Long productId, Long userId, String idempotencyKey, String requestHash) {
        try {
            checkUserRate(productId, userId);
            reserveIdempotencyKey(idempotencyKey, requestHash);
            if (!acquireProcessingLock(idempotencyKey)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_ALREADY_PROCESSING);
            }
            return Optional.of(new GuardToken(idempotencyKey));
        } catch (DataAccessException exception) {
            // Redis Cluster 장애 시에도 MySQL 락과 unique 제약으로 최종 재고/예약 정합성을 보장한다.
            return Optional.empty();
        }
    }

    public void release(GuardToken token) {
        try {
            redisTemplate.delete(processingKey(token.idempotencyKey()));
        } catch (DataAccessException ignored) {
            // Redis 락 해제 실패는 TTL로 복구되므로 DB 트랜잭션 결과에는 영향을 주지 않는다.
        }
    }

    private void checkUserRate(Long productId, Long userId) {
        Boolean allowed = redisTemplate.opsForValue()
                .setIfAbsent(rateKey(productId, userId), "1", USER_RATE_TTL);
        if (Boolean.FALSE.equals(allowed)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    private void reserveIdempotencyKey(String idempotencyKey, String requestHash) {
        String key = idempotencyKey(idempotencyKey);
        String storedHash = redisTemplate.opsForValue().get(key);
        if (storedHash != null && !storedHash.equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        redisTemplate.opsForValue().setIfAbsent(key, requestHash, IDEMPOTENCY_TTL);
    }

    private boolean acquireProcessingLock(String idempotencyKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(processingKey(idempotencyKey), "1", PROCESSING_LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    private String idempotencyKey(String idempotencyKey) {
        return "booking:{idem:%s}:idempotency".formatted(idempotencyKey);
    }

    private String processingKey(String idempotencyKey) {
        return "booking:{idem:%s}:processing".formatted(idempotencyKey);
    }

    private String rateKey(Long productId, Long userId) {
        return "booking:rate:{product:%d}:user:%d".formatted(productId, userId);
    }

    public record GuardToken(String idempotencyKey) {
    }
}
