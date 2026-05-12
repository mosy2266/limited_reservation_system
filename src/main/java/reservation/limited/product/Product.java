package reservation.limited.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private LocalDateTime checkInAt;

    @Column(nullable = false)
    private LocalDateTime checkOutAt;

    @Column(nullable = false)
    private LocalDateTime saleOpenAt;

    @Column(nullable = false)
    private LocalDateTime saleCloseAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Product() {
    }

    public Product(String name, long price, LocalDateTime checkInAt, LocalDateTime checkOutAt,
                   LocalDateTime saleOpenAt, LocalDateTime saleCloseAt, ProductStatus status) {
        this.name = name;
        this.price = price;
        this.checkInAt = checkInAt;
        this.checkOutAt = checkOutAt;
        this.saleOpenAt = saleOpenAt;
        this.saleCloseAt = saleCloseAt;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public boolean isAvailable() {
        return status == ProductStatus.AVAILABLE;
    }

    public boolean isSaleClosed(LocalDateTime now) {
        return now.isAfter(saleCloseAt);
    }

    public boolean isSaleNotOpen(LocalDateTime now) {
        return now.isBefore(saleOpenAt);
    }

}
