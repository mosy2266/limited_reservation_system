package reservation.limited.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reservation.limited.common.BusinessException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisInventoryStockGateTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @Test
    void reserveReturnsReservationWhenRedisStockIsReserved() {
        RedisInventoryStockGate stockGate = stockGateReturning(1L);

        Optional<RedisInventoryStockGate.StockReservation> reservation = stockGate.reserve(1L);

        assertThat(reservation).contains(new RedisInventoryStockGate.StockReservation(1L));
    }

    @Test
    void reserveThrowsSoldOutWhenRedisStockIsZero() {
        RedisInventoryStockGate stockGate = stockGateReturning(0L);

        assertThatThrownBy(() -> stockGate.reserve(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("재고가 모두 소진되었습니다.");
    }

    @Test
    void reserveFallsBackWhenRedisStockKeyIsMissing() {
        RedisInventoryStockGate stockGate = stockGateReturning(-1L);

        Optional<RedisInventoryStockGate.StockReservation> reservation = stockGate.reserve(1L);

        assertThat(reservation).isEmpty();
    }

    @Test
    void reserveFallsBackWhenRedisIsUnavailable() {
        RedisInventoryStockGate stockGate = new RedisInventoryStockGate(redisTemplate) {
            @Override
            protected Long executeReserveScript(Long productId) {
                throw new RedisConnectionFailureException("redis unavailable");
            }
        };

        Optional<RedisInventoryStockGate.StockReservation> reservation = stockGate.reserve(1L);

        assertThat(reservation).isEmpty();
    }

    @Test
    void restoreIncrementsRedisStock() {
        RedisInventoryStockGate stockGate = new RedisInventoryStockGate(redisTemplate);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        stockGate.restore(new RedisInventoryStockGate.StockReservation(1L));

        verify(valueOperations).increment(eq("inventory:stock:{product:1}"));
    }

    private RedisInventoryStockGate stockGateReturning(Long result) {
        return new RedisInventoryStockGate(redisTemplate) {
            @Override
            protected Long executeReserveScript(Long productId) {
                return result;
            }
        };
    }
}
