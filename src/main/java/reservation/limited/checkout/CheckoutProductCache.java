package reservation.limited.checkout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reservation.limited.product.Product;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class CheckoutProductCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Duration maxTtl;

    public CheckoutProductCache(
            StringRedisTemplate redisTemplate,
            @Value("${app.checkout-cache.max-ttl}") Duration maxTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.maxTtl = maxTtl;
    }

    public Optional<CheckoutProductCacheItem> get(Long productId) {
        try {
            String payload = redisTemplate.opsForValue().get(key(productId));
            if (payload == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, CheckoutProductCacheItem.class));
        } catch (DataAccessException | JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    public void put(Product product) {
        put(CheckoutProductCacheItem.from(product));
    }

    public void put(CheckoutProductCacheItem item) {
        Duration ttl = ttlUntilSaleClose(item.saleCloseAt());
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(key(item.id()), objectMapper.writeValueAsString(item), ttl);
        } catch (DataAccessException | JsonProcessingException ignored) {
            // Checkout 캐시는 조회 최적화 용도이므로 실패해도 MySQL 조회 흐름을 유지한다.
        }
    }

    private Duration ttlUntilSaleClose(LocalDateTime saleCloseAt) {
        Duration ttl = Duration.between(LocalDateTime.now(), saleCloseAt);
        if (ttl.compareTo(maxTtl) > 0) {
            return maxTtl;
        }
        return ttl;
    }

    private String key(Long productId) {
        return "checkout:product:{product:%d}".formatted(productId);
    }
}
