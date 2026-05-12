package reservation.limited.checkout;

import org.junit.jupiter.api.Test;
import reservation.limited.product.Product;
import reservation.limited.product.ProductRepository;
import reservation.limited.product.ProductStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CheckoutCacheWarmerTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CheckoutProductCache checkoutProductCache = mock(CheckoutProductCache.class);
    private final CheckoutCacheWarmer warmer = new CheckoutCacheWarmer(productRepository, checkoutProductCache, 2);

    @Test
    void warmupCachesAvailableProductsWithinLimit() {
        Product first = product(1L);
        Product second = product(2L);
        Product third = product(3L);
        given(productRepository.findTop100ByStatusAndSaleCloseAtAfterOrderBySaleOpenAtAsc(
                eq(ProductStatus.AVAILABLE),
                any(LocalDateTime.class)
        )).willReturn(List.of(first, second, third));

        warmer.warmup();

        verify(checkoutProductCache).put(first);
        verify(checkoutProductCache).put(second);
        verify(checkoutProductCache, times(2)).put(any(Product.class));
    }

    private Product product(Long id) {
        LocalDateTime now = LocalDateTime.now();
        Product product = new Product(
                "테스트 상품 " + id,
                100_000L,
                now.plusDays(10),
                now.plusDays(11),
                now.minusDays(1),
                now.plusDays(2),
                ProductStatus.AVAILABLE
        );
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
