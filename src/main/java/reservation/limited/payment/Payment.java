package reservation.limited.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private long requestedAmount;

    @Column(nullable = false)
    private long approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(length = 255)
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Payment() {
    }

    private Payment(Long bookingId, PaymentMethod paymentMethod, long requestedAmount, long approvedAmount,
                    PaymentStatus status, String failureReason) {
        this.bookingId = bookingId;
        this.paymentMethod = paymentMethod;
        this.requestedAmount = requestedAmount;
        this.approvedAmount = approvedAmount;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static Payment approved(Long bookingId, PaymentMethod paymentMethod, long amount) {
        return new Payment(bookingId, paymentMethod, amount, amount, PaymentStatus.APPROVED, null);
    }
}
