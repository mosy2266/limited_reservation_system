package reservation.limited.checkout;

import reservation.limited.product.Product;

import java.time.LocalDateTime;

public record CheckoutResponse(
        ProductResponse product,
        UserResponse user
) {

    public static CheckoutResponse of(Product product, long userId, long availablePoint) {
        return new CheckoutResponse(
                ProductResponse.from(product),
                new UserResponse(userId, availablePoint)
        );
    }

    public static CheckoutResponse of(CheckoutProductCacheItem product, long userId, long availablePoint) {
        return new CheckoutResponse(
                ProductResponse.from(product),
                new UserResponse(userId, availablePoint)
        );
    }

    public record ProductResponse(
            Long id,
            String name,
            long price,
            LocalDateTime checkInAt,
            LocalDateTime checkOutAt
    ) {

        private static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    product.getCheckInAt(),
                    product.getCheckOutAt()
            );
        }

        private static ProductResponse from(CheckoutProductCacheItem product) {
            return new ProductResponse(
                    product.id(),
                    product.name(),
                    product.price(),
                    product.checkInAt(),
                    product.checkOutAt()
            );
        }
    }

    public record UserResponse(
            long id,
            long availablePoint
    ) {
    }
}
