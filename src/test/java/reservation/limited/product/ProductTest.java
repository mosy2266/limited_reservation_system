package reservation.limited.product;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Test
    void isAvailableReturnsTrueWhenStatusIsAvailable() {
        Product product = newProduct(ProductStatus.AVAILABLE);

        assertThat(product.isAvailable()).isTrue();
    }

    @Test
    void isAvailableReturnsFalseWhenStatusIsUnavailable() {
        Product product = newProduct(ProductStatus.UNAVAILABLE);

        assertThat(product.isAvailable()).isFalse();
    }

    @Test
    void isSaleNotOpenReturnsTrueBeforeSaleOpenAt() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 10, 0);
        Product product = new Product(
                "테스트 상품",
                100_000L,
                now.plusDays(10),
                now.plusDays(11),
                now.plusHours(1),
                now.plusHours(2),
                ProductStatus.AVAILABLE
        );

        assertThat(product.isSaleNotOpen(now)).isTrue();
    }

    @Test
    void isSaleClosedReturnsTrueAfterSaleCloseAt() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 10, 0);
        Product product = new Product(
                "테스트 상품",
                100_000L,
                now.plusDays(10),
                now.plusDays(11),
                now.minusHours(2),
                now.minusHours(1),
                ProductStatus.AVAILABLE
        );

        assertThat(product.isSaleClosed(now)).isTrue();
    }

    private Product newProduct(ProductStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 10, 0);
        return new Product(
                "테스트 상품",
                100_000L,
                now.plusDays(10),
                now.plusDays(11),
                now.minusHours(1),
                now.plusHours(1),
                status
        );
    }
}
