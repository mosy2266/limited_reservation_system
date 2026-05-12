package reservation.limited.payment;

import org.springframework.stereotype.Component;

@Component
public class PointPaymentProcessor implements PaymentProcessor {

    private static final long AVAILABLE_POINT = 30_000L;

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.POINT;
    }

    @Override
    public PaymentResult pay(PaymentCommand command) {
        // 포인트 도메인 구현 전까지는 임시 보유 포인트로 부족 여부를 판단한다.
        if (command.pointAmount() > AVAILABLE_POINT) {
            return PaymentResult.failed(PaymentFailureReason.POINT_NOT_ENOUGH);
        }
        return PaymentResult.approved();
    }
}
