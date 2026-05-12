package reservation.limited.booking;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reservation.limited.common.BusinessException;
import reservation.limited.common.ErrorCode;
import reservation.limited.inventory.InventoryRepository;
import reservation.limited.inventory.RedisInventoryStockGate;
import reservation.limited.outbox.BookingConfirmedPayload;
import reservation.limited.outbox.OutboxEvent;
import reservation.limited.outbox.OutboxEventRepository;
import reservation.limited.outbox.OutboxPayloadSerializer;
import reservation.limited.payment.Payment;
import reservation.limited.payment.PaymentCommand;
import reservation.limited.payment.PaymentRepository;
import reservation.limited.payment.PaymentService;
import reservation.limited.product.Product;
import reservation.limited.product.ProductRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class BookingService {

    private static final String BOOKING_AGGREGATE_TYPE = "BOOKING";
    private static final String BOOKING_CONFIRMED_EVENT_TYPE = "BOOKING_CONFIRMED";

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final RedisInventoryStockGate stockGate;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPayloadSerializer outboxPayloadSerializer;
    private final PaymentService paymentService;
    private final BookingNoGenerator bookingNoGenerator;
    private final BookingRequestHashGenerator requestHashGenerator;
    private final BookingTrafficGuard trafficGuard;

    public BookingService(ProductRepository productRepository, InventoryRepository inventoryRepository,
                          RedisInventoryStockGate stockGate, BookingRepository bookingRepository,
                          PaymentRepository paymentRepository, OutboxEventRepository outboxEventRepository,
                          OutboxPayloadSerializer outboxPayloadSerializer, PaymentService paymentService,
                          BookingNoGenerator bookingNoGenerator, BookingRequestHashGenerator requestHashGenerator,
                          BookingTrafficGuard trafficGuard) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.stockGate = stockGate;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxPayloadSerializer = outboxPayloadSerializer;
        this.paymentService = paymentService;
        this.bookingNoGenerator = bookingNoGenerator;
        this.requestHashGenerator = requestHashGenerator;
        this.trafficGuard = trafficGuard;
    }

    @Transactional
    public BookingResponse book(String idempotencyKey, BookingRequest request) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(ErrorCode.MISSING_IDEMPOTENCY_KEY);
        }

        var guardToken = trafficGuard.tryEnter(request.productId(), request.userId(), idempotencyKey,
                requestHashGenerator.generate(request));
        try {
            Product product = productRepository.findById(request.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

            validateProduct(product);
            validatePayment(product, request.payment());

            return bookingRepository.findByIdempotencyKey(idempotencyKey)
                    .map(existingBooking -> handleIdempotentRequest(existingBooking, product, request))
                    .orElseGet(() -> createBooking(idempotencyKey, product, request));
        } finally {
            guardToken.ifPresent(trafficGuard::release);
        }
    }

    private BookingResponse handleIdempotentRequest(Booking booking, Product product, BookingRequest request) {
        // 같은 멱등성 키가 이미 있으면 동일 요청인 경우에만 기존 예약 결과를 반환한다.
        if (!booking.isSameRequest(product.getId(), request.userId(), product.getPrice(),
                request.payment().paymentAmount(), request.payment().pointAmount(),
                request.payment().primaryMethod())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }

        return BookingResponse.from(booking);
    }

    private BookingResponse createBooking(String idempotencyKey, Product product, BookingRequest request) {
        if (bookingRepository.existsByUserIdAndProductId(request.userId(), product.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATED_BOOKING);
        }

        Optional<RedisInventoryStockGate.StockReservation> stockReservation = stockGate.reserve(product.getId());
        boolean completed = false;
        try {
            BookingResponse response = confirmWithDatabase(idempotencyKey, product, request);
            completed = true;
            return response;
        } finally {
            if (!completed) {
                stockReservation.ifPresent(stockGate::restore);
            }
        }
    }

    private BookingResponse confirmWithDatabase(String idempotencyKey, Product product, BookingRequest request) {
        int updatedCount = inventoryRepository.increaseSoldQuantityIfAvailable(product.getId());
        if (updatedCount == 0) {
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }

        paymentService.pay(new PaymentCommand(
                request.payment().primaryMethod(),
                request.payment().paymentAmount(),
                request.payment().pointAmount()
        ));

        Booking booking = Booking.confirm(
                bookingNoGenerator.generate(),
                product.getId(),
                request.userId(),
                product.getPrice(),
                request.payment().paymentAmount(),
                request.payment().pointAmount(),
                request.payment().primaryMethod(),
                idempotencyKey
        );

        try {
            Booking savedBooking = bookingRepository.save(booking);
            paymentRepository.save(Payment.approved(savedBooking.getId(), request.payment().primaryMethod(),
                    request.payment().paymentAmount()));
            saveBookingConfirmedOutbox(savedBooking);
            return BookingResponse.from(savedBooking);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATED_BOOKING);
        }
    }

    private void saveBookingConfirmedOutbox(Booking booking) {
        // 예약 확정 이벤트는 Booking/Payment/Inventory 변경과 같은 DB 트랜잭션에 저장한다.
        String payload = outboxPayloadSerializer.serialize(BookingConfirmedPayload.from(booking));
        outboxEventRepository.save(OutboxEvent.pending(
                BOOKING_AGGREGATE_TYPE,
                booking.getId(),
                BOOKING_CONFIRMED_EVENT_TYPE,
                payload
        ));
    }

    private void validateProduct(Product product) {
        LocalDateTime now = LocalDateTime.now();
        if (!product.isAvailable()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        if (product.isSaleNotOpen(now)) {
            throw new BusinessException(ErrorCode.SALE_NOT_OPEN);
        }
        if (product.isSaleClosed(now)) {
            throw new BusinessException(ErrorCode.SALE_CLOSED);
        }
    }

    private void validatePayment(Product product, BookingRequest.PaymentRequest payment) {
        // 상품 금액은 현금성 결제 금액과 포인트 사용액의 합과 정확히 일치해야 한다.
        if (payment.paymentAmount() + payment.pointAmount() != product.getPrice()) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_AMOUNT);
        }

        paymentService.validateCombination(payment.primaryMethod(), payment.paymentAmount(), payment.pointAmount());
    }
}
