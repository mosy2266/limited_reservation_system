package reservation.limited.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reservation.limited.inventory.Inventory;
import reservation.limited.inventory.InventoryRepository;
import reservation.limited.inventory.RedisInventoryStockGate;
import reservation.limited.messaging.MessagePublisher;
import reservation.limited.payment.PaymentMethod;
import reservation.limited.payment.PaymentRepository;
import reservation.limited.product.Product;
import reservation.limited.product.ProductRepository;
import reservation.limited.product.ProductStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking-consistency-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BookingConsistencyTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private BookingTrafficGuard bookingTrafficGuard;

    @MockitoBean
    private RedisInventoryStockGate stockGate;

    @MockitoBean
    private MessagePublisher messagePublisher;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        given(bookingTrafficGuard.tryEnter(any(), any(), any(), any())).willReturn(Optional.empty());
        given(stockGate.reserve(any())).willReturn(Optional.empty());
    }

    @Test
    void concurrentBookingsDoNotOversellSingleStock() throws Exception {
        Product product = productRepository.save(newProduct(100_000L));
        inventoryRepository.save(new Inventory(product.getId(), 1, 0));

        List<Result> results = runConcurrently(10, index -> bookingService.book(
                "stock-key-" + index,
                request(product.getId(), (long) index, PaymentMethod.CARD, 70_000L, 30_000L)
        ));

        long successCount = results.stream().filter(Result::successful).count();
        Inventory inventory = inventoryRepository.findById(product.getId()).orElseThrow();

        assertThat(successCount).isEqualTo(1);
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(inventory.getSoldQuantity()).isEqualTo(1);
    }

    @Test
    void concurrentSameUserBookingsCreateOnlyOneBooking() throws Exception {
        Product product = productRepository.save(newProduct(100_000L));
        inventoryRepository.save(new Inventory(product.getId(), 10, 0));

        List<Result> results = runConcurrently(10, index -> bookingService.book(
                "same-user-key-" + index,
                request(product.getId(), 1L, PaymentMethod.CARD, 70_000L, 30_000L)
        ));

        long successCount = results.stream().filter(Result::successful).count();
        Inventory inventory = inventoryRepository.findById(product.getId()).orElseThrow();

        assertThat(successCount).isEqualTo(1);
        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(inventory.getSoldQuantity()).isEqualTo(1);
    }

    private List<Result> runConcurrently(int count, BookingTask task) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Result>> tasks = new ArrayList<>();

        for (int index = 1; index <= count; index++) {
            int current = index;
            tasks.add(() -> {
                ready.countDown();
                start.await();
                try {
                    task.run(current);
                    return Result.ok();
                } catch (Exception exception) {
                    return Result.failure(exception);
                }
            });
        }

        List<Future<Result>> futures = tasks.stream()
                .map(executorService::submit)
                .toList();
        ready.await();
        start.countDown();

        List<Result> results = new ArrayList<>();
        for (Future<Result> future : futures) {
            results.add(future.get());
        }
        executorService.shutdown();
        return results;
    }

    private Product newProduct(long price) {
        LocalDateTime now = LocalDateTime.now();
        return new Product(
                "테스트 상품",
                price,
                now.plusDays(10),
                now.plusDays(11),
                now.minusDays(1),
                now.plusDays(1),
                ProductStatus.AVAILABLE
        );
    }

    private BookingRequest request(Long productId, Long userId, PaymentMethod method,
                                   long paymentAmount, long pointAmount) {
        return new BookingRequest(
                userId,
                productId,
                new BookingRequest.PaymentRequest(method, paymentAmount, pointAmount)
        );
    }

    @FunctionalInterface
    private interface BookingTask {
        void run(int index);
    }

    private record Result(boolean successful, Exception exception) {

        private static Result ok() {
            return new Result(true, null);
        }

        private static Result failure(Exception exception) {
            return new Result(false, exception);
        }
    }
}
