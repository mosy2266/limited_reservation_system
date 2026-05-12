package reservation.limited.booking;

public record BookingResponse(
        Long bookingId,
        String bookingNo,
        BookingStatus status
) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getBookingNo(), booking.getStatus());
    }
}
