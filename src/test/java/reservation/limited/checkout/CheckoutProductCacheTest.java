package reservation.limited.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reservation.limited.product.Product;
import reservation.limited.product.ProductStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CheckoutProductCacheTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final CheckoutProductCache cache = new CheckoutProductCache(redisTemplate, Duration.ofMinutes(10));

    @Test
    void getReturnsCacheItemWhenRedisPayloadExists() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("checkout:product:{product:1}")).willReturn("""
                {
                  "id": 1,
                  "name": "테스트 상품",
                  "price": 100000,
                  "checkInAt": "2026-05-10T15:00:00",
                  "checkOutAt": "2026-05-11T11:00:00",
                  "saleOpenAt": "2026-05-01T00:00:00",
                  "saleCloseAt": "2026-05-20T00:00:00",
                  "status": "AVAILABLE"
                }
                """);

        Optional<CheckoutProductCacheItem> item = cache.get(1L);

        assertThat(item).isPresent();
        assertThat(item.orElseThrow().name()).isEqualTo("테스트 상품");
    }

    @Test
    void getReturnsEmptyWhenRedisIsUnavailable() {
        given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("redis unavailable"));

        Optional<CheckoutProductCacheItem> item = cache.get(1L);

        assertThat(item).isEmpty();
    }

    @Test
    void putStoresProductSnapshotWithTtl() {
        Product product = product(ProductStatus.AVAILABLE, 1, 2);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        cache.put(product);

        verify(valueOperations).set(eq("checkout:product:{product:1}"), any(String.class), any(Duration.class));
    }

    private Product product(ProductStatus status, int saleOpenOffsetDays, int saleCloseOffsetDays) {
        LocalDateTime now = LocalDateTime.now();
        Product product = new Product(
                "테스트 상품",
                100_000L,
                now.plusDays(10),
                now.plusDays(11),
                now.plusDays(saleOpenOffsetDays),
                now.plusDays(saleCloseOffsetDays),
                status
        );
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", 1L);
        return product;
    }
}
