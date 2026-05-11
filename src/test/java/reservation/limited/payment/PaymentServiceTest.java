package reservation.limited.payment;

import org.junit.jupiter.api.Test;
import reservation.limited.common.BusinessException;
import reservation.limited.common.ErrorCode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentServiceTest {

    private final PaymentService paymentService = new PaymentService(List.of(
            new CardPaymentProcessor(),
            new PayPaymentProcessor(),
            new PointPaymentProcessor()
    ));

    @Test
    void payApprovesCardPaymentWithinLimit() {
        PaymentResult result = paymentService.pay(new PaymentCommand(PaymentMethod.CARD, 100_000L, 0L));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void payRejectsCardPaymentWhenLimitIsExceeded() {
        assertThatThrownBy(() -> paymentService.pay(new PaymentCommand(PaymentMethod.CARD, 1_000_001L, 0L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CARD_LIMIT_EXCEEDED));
    }

    @Test
    void payApprovesPayPaymentWithinBalance() {
        PaymentResult result = paymentService.pay(new PaymentCommand(PaymentMethod.PAY, 500_000L, 0L));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void payRejectsPayPaymentWhenBalanceIsNotEnough() {
        assertThatThrownBy(() -> paymentService.pay(new PaymentCommand(PaymentMethod.PAY, 500_001L, 0L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAY_BALANCE_NOT_ENOUGH));
    }

    @Test
    void payApprovesPointPaymentWithinTemporaryPoint() {
        PaymentResult result = paymentService.pay(new PaymentCommand(PaymentMethod.POINT, 0L, 30_000L));

        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void payRejectsPointPaymentWhenPointIsNotEnough() {
        assertThatThrownBy(() -> paymentService.pay(new PaymentCommand(PaymentMethod.POINT, 0L, 30_001L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_NOT_ENOUGH));
    }

    @Test
    void validateCombinationAllowsCardAndPoint() {
        paymentService.validateCombination(PaymentMethod.CARD, 70_000L, 30_000L);
    }

    @Test
    void validateCombinationAllowsPayAndPoint() {
        paymentService.validateCombination(PaymentMethod.PAY, 70_000L, 30_000L);
    }

    @Test
    void validateCombinationAllowsPointOnly() {
        paymentService.validateCombination(PaymentMethod.POINT, 0L, 30_000L);
    }

    @Test
    void validateCombinationRejectsPointMethodWithCashPaymentAmount() {
        assertThatThrownBy(() -> paymentService.validateCombination(PaymentMethod.POINT, 1L, 30_000L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_COMBINATION));
    }

    @Test
    void validateCombinationRejectsCardWithoutCashPaymentAmount() {
        assertThatThrownBy(() -> paymentService.validateCombination(PaymentMethod.CARD, 0L, 30_000L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_COMBINATION));
    }
}
