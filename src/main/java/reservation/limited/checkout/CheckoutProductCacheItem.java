package reservation.limited.checkout;

import reservation.limited.product.Product;
import reservation.limited.product.ProductStatus;

import java.time.LocalDateTime;

public record CheckoutProductCacheItem(
        Long id,
        String name,
        long price,
        LocalDateTime checkInAt,
        LocalDateTime checkOutAt,
        LocalDateTime saleOpenAt,
        LocalDateTime saleCloseAt,
        ProductStatus status
) {

    public static CheckoutProductCacheItem from(Product product) {
        return new CheckoutProductCacheItem(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCheckInAt(),
                product.getCheckOutAt(),
                product.getSaleOpenAt(),
                product.getSaleCloseAt(),
                product.getStatus()
        );
    }

    public boolean isAvailable() {
        return status == ProductStatus.AVAILABLE;
    }

    public boolean isSaleClosed(LocalDateTime now) {
        return now.isAfter(saleCloseAt);
    }
}
