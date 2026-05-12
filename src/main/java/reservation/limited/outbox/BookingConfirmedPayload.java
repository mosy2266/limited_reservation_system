package reservation.limited.outbox;

import reservation.limited.booking.Booking;

public record BookingConfirmedPayload(
        Long bookingId,
        String bookingNo,
        Long productId,
        Long userId,
        String status
) {

    public static BookingConfirmedPayload from(Booking booking) {
        return new BookingConfirmedPayload(
                booking.getId(),
                booking.getBookingNo(),
                booking.getProductId(),
                booking.getUserId(),
                booking.getStatus().name()
        );
    }
}
