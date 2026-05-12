package reservation.limited.payment;

public interface PaymentProcessor {

    boolean supports(PaymentMethod method);

    PaymentResult pay(PaymentCommand command);
}
