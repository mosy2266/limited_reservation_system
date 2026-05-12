package reservation.limited.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Inventory inventory
               set inventory.soldQuantity = inventory.soldQuantity + 1,
                   inventory.version = inventory.version + 1,
                   inventory.updatedAt = CURRENT_TIMESTAMP
             where inventory.productId = :productId
               and inventory.soldQuantity < inventory.totalQuantity
            """)
    int increaseSoldQuantityIfAvailable(@Param("productId") Long productId);
}
