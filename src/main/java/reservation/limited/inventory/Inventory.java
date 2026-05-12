package reservation.limited.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "inventories")
public class Inventory {

    @Id
    private Long productId;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int soldQuantity;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Inventory() {
    }

    public Inventory(Long productId, int totalQuantity, int soldQuantity) {
        this.productId = productId;
        this.totalQuantity = totalQuantity;
        this.soldQuantity = soldQuantity;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void sell() {
        // DB 트랜잭션 안에서 잔여 재고를 검증한 뒤 판매 수량을 증가시킨다.
        if (soldQuantity >= totalQuantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }

        soldQuantity++;
        updatedAt = LocalDateTime.now();
    }
}
