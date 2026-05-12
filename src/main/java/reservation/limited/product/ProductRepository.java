package reservation.limited.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findTop100ByStatusAndSaleCloseAtAfterOrderBySaleOpenAtAsc(
            ProductStatus status,
            LocalDateTime now
    );
}
