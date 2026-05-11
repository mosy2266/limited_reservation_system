package reservation.limited.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import reservation.limited.payment.PaymentMethod;

public record BookingRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long productId,
        @NotNull @Valid PaymentRequest payment
) {

    public record PaymentRequest(
            @NotNull PaymentMethod primaryMethod,
            @PositiveOrZero long paymentAmount,
            @PositiveOrZero long pointAmount
    ) {
    }
}
