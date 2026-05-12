package reservation.limited.checkout;

import org.junit.jupiter.api.Test;
import reservation.limited.common.BusinessException;
import reservation.limited.common.ErrorCode;
import reservation.limited.product.Product;
import reservation.limited.product.ProductRepository;
import reservation.limited.product.ProductStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CheckoutServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CheckoutProductCache checkoutProductCache = mock(CheckoutProductCache.class);
    private final CheckoutService checkoutService = new CheckoutService(productRepository, checkoutProductCache);

    @Test
    void getCheckoutReturnsCachedProductWhenCacheHit() {
        CheckoutProductCacheItem item = cacheItem(ProductStatus.AVAILABLE, 1, 2);
        given(checkoutProductCache.get(1L)).willReturn(Optional.of(item));

        CheckoutResponse response = checkoutService.getCheckout(1L, 10L);

        assertThat(response.product().id()).isEqualTo(1L);
        assertThat(response.product().name()).isEqualTo("테스트 상품");
        assertThat(response.user().id()).isEqualTo(10L);
        verify(productRepository, never()).findById(1L);
    }

    @Test
    void getCheckoutLoadsFromDatabaseAndCachesWhenCacheMiss() {
        Product product = product(ProductStatus.AVAILABLE, 1, 2);
        given(checkoutProductCache.get(1L)).willReturn(Optional.empty());
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        CheckoutResponse response = checkoutService.getCheckout(1L, 10L);

        assertThat(response.product().name()).isEqualTo("테스트 상품");
        verify(productRepository).findById(1L);
        verify(checkoutProductCache).put(product);
    }

    @Test
    void getCheckoutFallsBackToDatabaseWhenCacheReturnsEmpty() {
        Product product = product(ProductStatus.AVAILABLE, 1, 2);
        given(checkoutProductCache.get(1L)).willReturn(Optional.empty());
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        CheckoutResponse response = checkoutService.getCheckout(1L, 10L);

        assertThat(response.product().id()).isEqualTo(product.getId());
    }

    @Test
    void getCheckoutRejectsUnavailableCachedProduct() {
        given(checkoutProductCache.get(1L)).willReturn(Optional.of(cacheItem(ProductStatus.UNAVAILABLE, 1, 2)));

        assertBusinessException(() -> checkoutService.getCheckout(1L, 10L), ErrorCode.PRODUCT_NOT_AVAILABLE);
        verify(productRepository, never()).findById(1L);
    }

    @Test
    void getCheckoutRejectsClosedCachedProduct() {
        given(checkoutProductCache.get(1L)).willReturn(Optional.of(cacheItem(ProductStatus.AVAILABLE, -3, -1)));

        assertBusinessException(() -> checkoutService.getCheckout(1L, 10L), ErrorCode.SALE_CLOSED);
        verify(productRepository, never()).findById(1L);
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

    private CheckoutProductCacheItem cacheItem(ProductStatus status, int saleOpenOffsetDays, int saleCloseOffsetDays) {
        return CheckoutProductCacheItem.from(product(status, saleOpenOffsetDays, saleCloseOffsetDays));
    }

    private void assertBusinessException(Runnable runnable, ErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
