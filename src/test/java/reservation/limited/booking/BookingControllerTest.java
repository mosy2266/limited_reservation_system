package reservation.limited.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import reservation.limited.inventory.Inventory;
import reservation.limited.inventory.InventoryRepository;
import reservation.limited.inventory.RedisInventoryStockGate;
import reservation.limited.messaging.MessagePublisher;
import reservation.limited.product.Product;
import reservation.limited.product.ProductRepository;
import reservation.limited.product.ProductStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @MockitoBean
    private BookingTrafficGuard bookingTrafficGuard;

    @MockitoBean
    private RedisInventoryStockGate stockGate;

    @MockitoBean
    private MessagePublisher messagePublisher;

    @BeforeEach
    void setUp() {
        given(bookingTrafficGuard.tryEnter(any(), any(), any(), any())).willReturn(Optional.empty());
        given(stockGate.reserve(any())).willReturn(Optional.empty());
    }

    @Test
    void bookCreatesConfirmedBookingAndDecreasesStock() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -1, 1));
        inventoryRepository.save(new Inventory(product.getId(), 3, 0));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 1L, "CARD", 70_000L, 30_000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("SUCCESS")))
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));

        Inventory inventory = inventoryRepository.findById(product.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(inventory.getSoldQuantity()).isEqualTo(1);
        verify(stockGate).reserve(product.getId());
    }

    @Test
    void bookUsesRedisStockReservationBeforeDatabaseConfirmation() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -1, 1));
        inventoryRepository.save(new Inventory(product.getId(), 3, 0));
        var reservation = new RedisInventoryStockGate.StockReservation(product.getId());
        given(stockGate.reserve(product.getId())).willReturn(Optional.of(reservation));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-redis-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 10L, "CARD", 70_000L, 30_000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));

        verify(stockGate).reserve(product.getId());
    }

    @Test
    void bookRestoresRedisStockReservationWhenDatabaseStockIsSoldOut() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -1, 1));
        inventoryRepository.save(new Inventory(product.getId(), 1, 1));
        var reservation = new RedisInventoryStockGate.StockReservation(product.getId());
        given(stockGate.reserve(product.getId())).willReturn(Optional.of(reservation));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-redis-restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 11L, "CARD", 70_000L, 30_000L)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code", is("SOLD_OUT")));

        verify(stockGate).restore(reservation);
    }

    @Test
    void bookReturnsSameResultWhenSameIdempotencyKeyAndSameRequestAreRepeated() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -1, 1));
        inventoryRepository.save(new Inventory(product.getId(), 3, 0));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-repeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 1L, "CARD", 70_000L, 30_000L)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-repeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 1L, "CARD", 70_000L, 30_000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("SUCCESS")));

        org.assertj.core.api.Assertions.assertThat(bookingRepository.count()).isEqualTo(1);
        Inventory inventory = inventoryRepository.findById(product.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(inventory.getSoldQuantity()).isEqualTo(1);
    }

    @Test
    void bookReturnsConflictWhenSameIdempotencyKeyHasDifferentRequest() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -1, 1));
        inventoryRepository.save(new Inventory(product.getId(), 3, 0));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 1L, "CARD", 70_000L, 30_000L)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 1L, "CARD", 60_000L, 40_000L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_KEY_CONFLICT")));
    }

    @Test
    void bookReturnsGoneWhenInventoryIsSoldOut() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -1, 1));
        inventoryRepository.save(new Inventory(product.getId(), 1, 1));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-sold-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 1L, "CARD", 70_000L, 30_000L)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code", is("SOLD_OUT")));
    }

    @Test
    void bookReturnsBadRequestWhenIdempotencyKeyIsMissing() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -1, 1));
        inventoryRepository.save(new Inventory(product.getId(), 3, 0));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 1L, "CARD", 70_000L, 30_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MISSING_IDEMPOTENCY_KEY")));
    }

    @Test
    void bookReturnsBadRequestWhenPaymentAmountIsInvalid() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -1, 1));
        inventoryRepository.save(new Inventory(product.getId(), 3, 0));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-invalid-amount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 1L, "CARD", 50_000L, 30_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PAYMENT_AMOUNT")));
    }

    @Test
    void bookSupportsPayAndPointCombination() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -1, 1));
        inventoryRepository.save(new Inventory(product.getId(), 3, 0));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-pay-point")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 2L, "PAY", 70_000L, 30_000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));
    }

    @Test
    void bookReturnsPointNotEnoughWhenPointPaymentExceedsTemporaryBalance() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -1, 1));
        inventoryRepository.save(new Inventory(product.getId(), 3, 0));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-point-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 3L, "POINT", 0L, 100_000L)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("POINT_NOT_ENOUGH")));
    }

    @Test
    void bookReturnsPayBalanceNotEnoughWhenPayAmountExceedsTemporaryBalance() throws Exception {
        Product product = productRepository.save(newProduct(ProductStatus.AVAILABLE, -1, 1, 600_000L));
        inventoryRepository.save(new Inventory(product.getId(), 3, 0));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-pay-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(product.getId(), 4L, "PAY", 600_000L, 0L)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", is("PAY_BALANCE_NOT_ENOUGH")));
    }

    private Product newProduct(ProductStatus status, int saleOpenOffsetDays, int saleCloseOffsetDays) {
        return newProduct(status, saleOpenOffsetDays, saleCloseOffsetDays, 100_000L);
    }

    private Product newProduct(ProductStatus status, int saleOpenOffsetDays, int saleCloseOffsetDays, long price) {
        LocalDateTime now = LocalDateTime.now();
        return new Product(
                "테스트 상품",
                price,
                now.plusDays(10),
                now.plusDays(11),
                now.plusDays(saleOpenOffsetDays),
                now.plusDays(saleCloseOffsetDays),
                status
        );
    }

    private String request(Long productId, Long userId, String primaryMethod, long paymentAmount, long pointAmount) {
        return """
                {
                  "userId": %d,
                  "productId": %d,
                  "payment": {
                    "primaryMethod": "%s",
                    "paymentAmount": %d,
                    "pointAmount": %d
                  }
                }
                """.formatted(userId, productId, primaryMethod, paymentAmount, pointAmount);
    }
}
