package reservation.limited.payment;

import org.springframework.stereotype.Component;

@Component
public class PayPaymentProcessor implements PaymentProcessor {

    private static final long PAY_BALANCE = 500_000L;

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.PAY;
    }

    @Override
    public PaymentResult pay(PaymentCommand command) {
        // 실제 페이 연동 전까지는 보유 잔액 초과 여부만 실패 케이스로 시뮬레이션한다.
        if (command.paymentAmount() > PAY_BALANCE) {
            return PaymentResult.failed(PaymentFailureReason.PAY_BALANCE_NOT_ENOUGH);
        }
        return PaymentResult.approved();
    }
}
