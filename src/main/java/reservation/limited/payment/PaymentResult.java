package reservation.limited.payment;

public record PaymentResult(
        PaymentStatus status,
        PaymentFailureReason failureReason
) {

    public static PaymentResult approved() {
        return new PaymentResult(PaymentStatus.APPROVED, null);
    }

    public static PaymentResult failed(PaymentFailureReason failureReason) {
        return new PaymentResult(PaymentStatus.FAILED, failureReason);
    }

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }
}
