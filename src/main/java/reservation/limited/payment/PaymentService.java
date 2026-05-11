package reservation.limited.payment;

import org.springframework.stereotype.Service;
import reservation.limited.common.BusinessException;
import reservation.limited.common.ErrorCode;

import java.util.List;

@Service
public class PaymentService {

    private final List<PaymentProcessor> processors;

    public PaymentService(List<PaymentProcessor> processors) {
        this.processors = processors;
    }

    public PaymentResult pay(PaymentCommand command) {
        // 결제 수단별 실패 정책은 processor 구현체에 위임해 Booking 로직 변경을 줄인다.
        PaymentProcessor processor = processors.stream()
                .filter(candidate -> candidate.supports(command.method()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PAYMENT_COMBINATION));

        PaymentResult result = processor.pay(command);
        if (!result.isApproved()) {
            throw new BusinessException(result.failureReason().getErrorCode());
        }
        return result;
    }

    public void validateCombination(PaymentMethod method, long paymentAmount, long pointAmount) {
        if (method == null) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_COMBINATION);
        }

        switch (method) {
            case CARD, PAY -> {
                if (paymentAmount <= 0) {
                    throw new BusinessException(ErrorCode.INVALID_PAYMENT_COMBINATION);
                }
            }
            case POINT -> {
                if (paymentAmount != 0 || pointAmount <= 0) {
                    throw new BusinessException(ErrorCode.INVALID_PAYMENT_COMBINATION);
                }
            }
        }

        if (paymentAmount < 0 || pointAmount < 0) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
    }
}
