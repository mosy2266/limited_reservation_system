package reservation.limited.checkout;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reservation.limited.common.BusinessException;
import reservation.limited.common.ErrorCode;
import reservation.limited.product.Product;
import reservation.limited.product.ProductRepository;

import java.time.LocalDateTime;

@Service
public class CheckoutService {

    private static final long TEMPORARY_AVAILABLE_POINT = 30_000L;

    private final ProductRepository productRepository;
    private final CheckoutProductCache checkoutProductCache;

    public CheckoutService(ProductRepository productRepository, CheckoutProductCache checkoutProductCache) {
        this.productRepository = productRepository;
        this.checkoutProductCache = checkoutProductCache;
    }

    @Transactional(readOnly = true)
    public CheckoutResponse getCheckout(Long productId, Long userId) {
        return checkoutProductCache.get(productId)
                .map(product -> getCheckoutFromCache(product, userId))
                .orElseGet(() -> getCheckoutFromDatabase(productId, userId));
    }

    private CheckoutResponse getCheckoutFromDatabase(Long productId, Long userId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        checkoutProductCache.put(product);
        validateProduct(product.isAvailable(), product.isSaleClosed(LocalDateTime.now()));

        return CheckoutResponse.of(product, userId, getAvailablePoint(userId));
    }

    private CheckoutResponse getCheckoutFromCache(CheckoutProductCacheItem product, Long userId) {
        validateProduct(product.isAvailable(), product.isSaleClosed(LocalDateTime.now()));
        return CheckoutResponse.of(product, userId, getAvailablePoint(userId));
    }

    private void validateProduct(boolean available, boolean saleClosed) {
        // checkout 조회는 재고를 선점하지 않고 상품의 현재 판매 가능 여부만 검증한다.
        if (!available) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }

        if (saleClosed) {
            throw new BusinessException(ErrorCode.SALE_CLOSED);
        }
    }

    private long getAvailablePoint(Long userId) {
        // 포인트 도메인이 구현되기 전까지 checkout 응답에 사용할 임시 포인트 값이다.
        return TEMPORARY_AVAILABLE_POINT;
    }
}
