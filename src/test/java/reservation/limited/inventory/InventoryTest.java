package reservation.limited.inventory;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    @Test
    void sellIncreasesSoldQuantityWhenStockRemains() {
        Inventory inventory = new Inventory(1L, 10, 3);
        LocalDateTime beforeUpdatedAt = inventory.getUpdatedAt();

        inventory.sell();

        assertThat(inventory.getSoldQuantity()).isEqualTo(4);
        assertThat(inventory.getUpdatedAt()).isAfterOrEqualTo(beforeUpdatedAt);
    }

    @Test
    void sellThrowsExceptionWhenStockIsSoldOut() {
        Inventory inventory = new Inventory(1L, 10, 10);

        assertThatThrownBy(inventory::sell)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("재고가 부족합니다.");
    }
}
