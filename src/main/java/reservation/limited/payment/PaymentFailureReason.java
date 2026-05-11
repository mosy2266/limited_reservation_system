package reservation.limited.payment;

import lombok.Getter;
import reservation.limited.common.ErrorCode;

@Getter
public enum PaymentFailureReason {
    CARD_LIMIT_EXCEEDED(ErrorCode.CARD_LIMIT_EXCEEDED),
    POINT_NOT_ENOUGH(ErrorCode.POINT_NOT_ENOUGH),
    PAY_BALANCE_NOT_ENOUGH(ErrorCode.PAY_BALANCE_NOT_ENOUGH),
    PAYMENT_FAILED(ErrorCode.PAYMENT_FAILED);

    private final ErrorCode errorCode;

    PaymentFailureReason(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }
}
