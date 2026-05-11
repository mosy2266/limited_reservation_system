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

    public CheckoutService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public CheckoutResponse getCheckout(Long productId, Long userId) {
        // checkout 조회는 재고를 선점하지 않고 상품의 현재 판매 가능 여부만 검증한다.
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.isAvailable()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }

        if (product.isSaleClosed(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SALE_CLOSED);
        }

        return CheckoutResponse.of(product, userId, getAvailablePoint(userId));
    }

    private long getAvailablePoint(Long userId) {
        // 포인트 도메인이 구현되기 전까지 checkout 응답에 사용할 임시 포인트 값이다.
        return TEMPORARY_AVAILABLE_POINT;
    }
}
