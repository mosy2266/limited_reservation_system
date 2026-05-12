package reservation.limited.checkout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reservation.limited.product.Product;
import reservation.limited.product.ProductRepository;
import reservation.limited.product.ProductStatus;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class CheckoutCacheWarmer {

    private final ProductRepository productRepository;
    private final CheckoutProductCache checkoutProductCache;
    private final int warmupLimit;

    public CheckoutCacheWarmer(
            ProductRepository productRepository,
            CheckoutProductCache checkoutProductCache,
            @Value("${app.checkout-cache.warmup-limit}") int warmupLimit
    ) {
        this.productRepository = productRepository;
        this.checkoutProductCache = checkoutProductCache;
        this.warmupLimit = warmupLimit;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void warmupOnApplicationReady() {
        warmup();
    }

    @Scheduled(fixedDelayString = "${app.checkout-cache.warmup-fixed-delay}")
    @Transactional(readOnly = true)
    public void warmup() {
        List<Product> products = productRepository
                .findTop100ByStatusAndSaleCloseAtAfterOrderBySaleOpenAtAsc(
                        ProductStatus.AVAILABLE,
                        LocalDateTime.now()
                );

        products.stream()
                .limit(warmupLimit)
                .forEach(checkoutProductCache::put);
    }
}
