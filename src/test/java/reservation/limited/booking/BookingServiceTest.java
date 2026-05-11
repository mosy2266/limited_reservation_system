package reservation.limited.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import reservation.limited.common.BusinessException;
import reservation.limited.common.ErrorCode;
import reservation.limited.inventory.Inventory;
import reservation.limited.inventory.InventoryRepository;
import reservation.limited.inventory.RedisInventoryStockGate;
import reservation.limited.payment.PaymentCommand;
import reservation.limited.payment.PaymentMethod;
import reservation.limited.payment.PaymentRepository;
import reservation.limited.payment.PaymentResult;
import reservation.limited.payment.PaymentService;
import reservation.limited.product.Product;
import reservation.limited.product.ProductRepository;
import reservation.limited.product.ProductStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BookingServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final InventoryRepository inventoryRepository = mock(InventoryRepository.class);
    private final RedisInventoryStockGate stockGate = mock(RedisInventoryStockGate.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final BookingNoGenerator bookingNoGenerator = mock(BookingNoGenerator.class);
    private final BookingRequestHashGenerator requestHashGenerator = mock(BookingRequestHashGenerator.class);
    private final BookingTrafficGuard trafficGuard = mock(BookingTrafficGuard.class);

    private final BookingService bookingService = new BookingService(
            productRepository,
            inventoryRepository,
            stockGate,
            bookingRepository,
            paymentRepository,
            paymentService,
            bookingNoGenerator,
            requestHashGenerator,
            trafficGuard
    );

    @BeforeEach
    void setUp() {
        given(requestHashGenerator.generate(any())).willReturn("request-hash");
        given(trafficGuard.tryEnter(any(), any(), any(), any())).willReturn(Optional.empty());
    }

    @Test
    void bookThrowsMissingIdempotencyKeyWhenHeaderIsBlank() {
        assertBusinessException(
                () -> bookingService.book(" ", request(1L, 1L, PaymentMethod.CARD, 70_000L, 30_000L)),
                ErrorCode.MISSING_IDEMPOTENCY_KEY
        );
    }

    @Test
    void bookThrowsProductNotFoundWhenProductDoesNotExist() {
        BookingRequest request = request(1L, 1L, PaymentMethod.CARD, 70_000L, 30_000L);
        given(productRepository.findById(1L)).willReturn(Optional.empty());

        assertBusinessException(() -> bookingService.book("key-1", request), ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void bookReturnsExistingBookingWhenIdempotencyKeyAndRequestAreSame() {
        Product product = product(ProductStatus.AVAILABLE, -1, 1, 100_000L);
        Booking existingBooking = savedBooking(100L, "B1", 1L, 1L, 100_000L, 70_000L, 30_000L, "key-1");
        BookingRequest request = request(1L, 1L, PaymentMethod.CARD, 70_000L, 30_000L);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(bookingRepository.findByIdempotencyKey("key-1")).willReturn(Optional.of(existingBooking));

        BookingResponse response = bookingService.book("key-1", request);

        assertThat(response.bookingId()).isEqualTo(100L);
        assertThat(response.bookingNo()).isEqualTo("B1");
        verify(stockGate, never()).reserve(any());
        verify(paymentService, never()).pay(any());
    }

    @Test
    void bookThrowsConflictWhenSameIdempotencyKeyHasDifferentRequest() {
        Product product = product(ProductStatus.AVAILABLE, -1, 1, 100_000L);
        Booking existingBooking = savedBooking(100L, "B1", 1L, 1L, 100_000L, 70_000L, 30_000L, "key-1");
        BookingRequest request = request(1L, 1L, PaymentMethod.CARD, 60_000L, 40_000L);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(bookingRepository.findByIdempotencyKey("key-1")).willReturn(Optional.of(existingBooking));

        assertBusinessException(() -> bookingService.book("key-1", request), ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    void bookCreatesBookingAndPaymentWhenRequestIsValid() {
        Product product = product(ProductStatus.AVAILABLE, -1, 1, 100_000L);
        Inventory inventory = new Inventory(1L, 5, 0);
        BookingRequest request = request(1L, 1L, PaymentMethod.CARD, 70_000L, 30_000L);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(bookingRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(bookingRepository.existsByUserIdAndProductId(1L, 1L)).willReturn(false);
        given(stockGate.reserve(1L)).willReturn(Optional.empty());
        given(inventoryRepository.findWithLockByProductId(1L)).willReturn(Optional.of(inventory));
        given(paymentService.pay(any(PaymentCommand.class))).willReturn(PaymentResult.approved());
        given(bookingNoGenerator.generate()).willReturn("B1");
        given(bookingRepository.save(any(Booking.class))).willAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            ReflectionTestUtils.setField(booking, "id", 100L);
            return booking;
        });

        BookingResponse response = bookingService.book("key-1", request);

        assertThat(response.bookingId()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(inventory.getSoldQuantity()).isEqualTo(1);
        verify(paymentRepository).save(any());
    }

    @Test
    void bookRestoresRedisStockWhenPaymentFailsAfterRedisReservation() {
        Product product = product(ProductStatus.AVAILABLE, -1, 1, 100_000L);
        Inventory inventory = new Inventory(1L, 5, 0);
        var reservation = new RedisInventoryStockGate.StockReservation(1L);
        BookingRequest request = request(1L, 1L, PaymentMethod.CARD, 70_000L, 30_000L);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(bookingRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(bookingRepository.existsByUserIdAndProductId(1L, 1L)).willReturn(false);
        given(stockGate.reserve(1L)).willReturn(Optional.of(reservation));
        given(inventoryRepository.findWithLockByProductId(1L)).willReturn(Optional.of(inventory));
        given(paymentService.pay(any(PaymentCommand.class)))
                .willThrow(new BusinessException(ErrorCode.CARD_LIMIT_EXCEEDED));

        assertBusinessException(() -> bookingService.book("key-1", request), ErrorCode.CARD_LIMIT_EXCEEDED);
        verify(stockGate).restore(reservation);
    }

    @Test
    void bookThrowsDuplicatedBookingWhenUserAlreadyBookedProduct() {
        Product product = product(ProductStatus.AVAILABLE, -1, 1, 100_000L);
        BookingRequest request = request(1L, 1L, PaymentMethod.CARD, 70_000L, 30_000L);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(bookingRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(bookingRepository.existsByUserIdAndProductId(1L, 1L)).willReturn(true);

        assertBusinessException(() -> bookingService.book("key-1", request), ErrorCode.DUPLICATED_BOOKING);
        verify(stockGate, never()).reserve(any());
    }

    @Test
    void bookThrowsSoldOutWhenDatabaseStockIsSoldOut() {
        Product product = product(ProductStatus.AVAILABLE, -1, 1, 100_000L);
        Inventory inventory = new Inventory(1L, 1, 1);
        BookingRequest request = request(1L, 1L, PaymentMethod.CARD, 70_000L, 30_000L);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(bookingRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(bookingRepository.existsByUserIdAndProductId(1L, 1L)).willReturn(false);
        given(stockGate.reserve(1L)).willReturn(Optional.empty());
        given(inventoryRepository.findWithLockByProductId(1L)).willReturn(Optional.of(inventory));

        assertBusinessException(() -> bookingService.book("key-1", request), ErrorCode.SOLD_OUT);
    }

    @Test
    void bookRestoresRedisStockWhenDatabaseUniqueConstraintFails() {
        Product product = product(ProductStatus.AVAILABLE, -1, 1, 100_000L);
        Inventory inventory = new Inventory(1L, 5, 0);
        var reservation = new RedisInventoryStockGate.StockReservation(1L);
        BookingRequest request = request(1L, 1L, PaymentMethod.CARD, 70_000L, 30_000L);
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(bookingRepository.findByIdempotencyKey("key-1")).willReturn(Optional.empty());
        given(bookingRepository.existsByUserIdAndProductId(1L, 1L)).willReturn(false);
        given(stockGate.reserve(1L)).willReturn(Optional.of(reservation));
        given(inventoryRepository.findWithLockByProductId(1L)).willReturn(Optional.of(inventory));
        given(paymentService.pay(any(PaymentCommand.class))).willReturn(PaymentResult.approved());
        given(bookingNoGenerator.generate()).willReturn("B1");
        given(bookingRepository.save(any(Booking.class))).willThrow(new DataIntegrityViolationException("duplicate"));

        assertBusinessException(() -> bookingService.book("key-1", request), ErrorCode.DUPLICATED_BOOKING);
        verify(stockGate).restore(reservation);
    }

    private Product product(ProductStatus status, int saleOpenOffsetDays, int saleCloseOffsetDays, long price) {
        LocalDateTime now = LocalDateTime.now();
        Product product = new Product(
                "테스트 상품",
                price,
                now.plusDays(10),
                now.plusDays(11),
                now.plusDays(saleOpenOffsetDays),
                now.plusDays(saleCloseOffsetDays),
                status
        );
        ReflectionTestUtils.setField(product, "id", 1L);
        return product;
    }

    private Booking savedBooking(Long id, String bookingNo, Long productId, Long userId, long totalAmount,
                                 long paymentAmount, long pointAmount, String idempotencyKey) {
        Booking booking = Booking.confirm(
                bookingNo,
                productId,
                userId,
                totalAmount,
                paymentAmount,
                pointAmount,
                PaymentMethod.CARD,
                idempotencyKey
        );
        ReflectionTestUtils.setField(booking, "id", id);
        return booking;
    }

    private BookingRequest request(Long productId, Long userId, PaymentMethod paymentMethod,
                                   long paymentAmount, long pointAmount) {
        return new BookingRequest(
                userId,
                productId,
                new BookingRequest.PaymentRequest(paymentMethod, paymentAmount, pointAmount)
        );
    }

    private void assertBusinessException(Runnable runnable, ErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
