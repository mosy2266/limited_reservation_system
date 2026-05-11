package reservation.limited.booking;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reservation.limited.common.ApiResponse;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ApiResponse<BookingResponse> book(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody BookingRequest request
    ) {
        // 예약 생성 요청은 멱등성 키와 요청 본문을 함께 서비스 계층으로 전달한다.
        return ApiResponse.success(bookingService.book(idempotencyKey, request));
    }
}
