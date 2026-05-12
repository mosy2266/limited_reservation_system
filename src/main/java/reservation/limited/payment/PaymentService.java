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
        PaymentResult result = findProcessor(command.method()).pay(command);
        if (!result.isApproved()) {
            throw new BusinessException(result.failureReason().getErrorCode());
        }

        if (command.method() != PaymentMethod.POINT && command.pointAmount() > 0) {
            PaymentResult pointResult = findProcessor(PaymentMethod.POINT).pay(command);
            if (!pointResult.isApproved()) {
                throw new BusinessException(pointResult.failureReason().getErrorCode());
            }
        }

        return result;
    }

    private PaymentProcessor findProcessor(PaymentMethod method) {
        return processors.stream()
                .filter(candidate -> candidate.supports(method))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PAYMENT_COMBINATION));
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
