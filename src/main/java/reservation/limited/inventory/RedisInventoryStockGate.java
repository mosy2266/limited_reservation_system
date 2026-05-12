package reservation.limited.inventory;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reservation.limited.common.BusinessException;
import reservation.limited.common.ErrorCode;

import java.util.List;
import java.util.Optional;

@Component
public class RedisInventoryStockGate {

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local stock = redis.call('GET', KEYS[1])
            if not stock then
                return -1
            end
            stock = tonumber(stock)
            if stock <= 0 then
                return 0
            end
            redis.call('DECR', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisInventoryStockGate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<StockReservation> reserve(Long productId) {
        try {
            Long result = executeReserveScript(productId);
            if (result == null || result == -1L) {
                return Optional.empty();
            }
            if (result == 0L) {
                throw new BusinessException(ErrorCode.SOLD_OUT);
            }
            return Optional.of(new StockReservation(productId));
        } catch (DataAccessException exception) {
            // Redis Cluster 장애 시에는 DB row lock 기반 최종 검증으로 fallback한다.
            return Optional.empty();
        }
    }

    public void restore(StockReservation reservation) {
        try {
            redisTemplate.opsForValue().increment(stockKey(reservation.productId()));
        } catch (DataAccessException ignored) {
            // 복구 실패 시에도 DB가 최종 원장이며, Redis 재고는 별도 동기화 작업으로 회복한다.
        }
    }

    protected Long executeReserveScript(Long productId) {
        return redisTemplate.execute(RESERVE_SCRIPT, List.of(stockKey(productId)));
    }

    private String stockKey(Long productId) {
        return "inventory:stock:{product:%d}".formatted(productId);
    }

    public record StockReservation(Long productId) {
    }
}
