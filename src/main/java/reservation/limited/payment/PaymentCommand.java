package reservation.limited.payment;

public record PaymentCommand(
        PaymentMethod method,
        long paymentAmount,
        long pointAmount
) {
}
