package reservation.limited.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import reservation.limited.payment.PaymentMethod;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "bookings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_booking_user_product", columnNames = {"userId", "productId"}),
                @UniqueConstraint(name = "uq_booking_idempotency", columnNames = "idempotencyKey")
        }
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String bookingNo;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false)
    private long paymentAmount;

    @Column(nullable = false)
    private long pointAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Booking() {
    }

    private Booking(String bookingNo, Long productId, Long userId, long totalAmount,
                    long paymentAmount, long pointAmount, PaymentMethod paymentMethod,
                    String idempotencyKey) {
        this.bookingNo = bookingNo;
        this.productId = productId;
        this.userId = userId;
        this.status = BookingStatus.CONFIRMED;
        this.totalAmount = totalAmount;
        this.paymentAmount = paymentAmount;
        this.pointAmount = pointAmount;
        this.paymentMethod = paymentMethod;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static Booking confirm(String bookingNo, Long productId, Long userId, long totalAmount,
                                  long paymentAmount, long pointAmount, PaymentMethod paymentMethod,
                                  String idempotencyKey) {
        return new Booking(bookingNo, productId, userId, totalAmount, paymentAmount, pointAmount, paymentMethod,
                idempotencyKey);
    }

    public boolean isSameRequest(Long productId, Long userId, long totalAmount, long paymentAmount,
                                 long pointAmount, PaymentMethod paymentMethod) {
        return this.productId.equals(productId)
                && this.userId.equals(userId)
                && this.totalAmount == totalAmount
                && this.paymentAmount == paymentAmount
                && this.pointAmount == pointAmount
                && this.paymentMethod == paymentMethod;
    }
}
