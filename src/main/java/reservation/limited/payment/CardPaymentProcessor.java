package reservation.limited.payment;

import org.springframework.stereotype.Component;

@Component
public class CardPaymentProcessor implements PaymentProcessor {

    private static final long CARD_LIMIT = 1_000_000L;

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CARD;
    }

    @Override
    public PaymentResult pay(PaymentCommand command) {
        // 한도 초과 여부만 실패 케이스로 시뮬레이션한다.
        if (command.paymentAmount() > CARD_LIMIT) {
            return PaymentResult.failed(PaymentFailureReason.CARD_LIMIT_EXCEEDED);
        }
        return PaymentResult.approved();
    }
}
