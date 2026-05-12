package reservation.limited.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reservation.limited.common.BusinessException;
import reservation.limited.common.ErrorCode;
import reservation.limited.common.GlobalExceptionHandler;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@Import(GlobalExceptionHandler.class)
class BookingControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @Test
    void bookReturnsSuccessResponse() throws Exception {
        given(bookingService.book(eq("key-1"), any(BookingRequest.class)))
                .willReturn(new BookingResponse(1L, "B202605110000001", BookingStatus.CONFIRMED));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("SUCCESS")))
                .andExpect(jsonPath("$.data.bookingId", is(1)))
                .andExpect(jsonPath("$.data.bookingNo", is("B202605110000001")))
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));
    }

    @Test
    void bookReturnsBadRequestWhenRequestBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 0,
                                  "productId": 1,
                                  "payment": {
                                    "primaryMethod": "CARD",
                                    "paymentAmount": 70000,
                                    "pointAmount": 30000
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_INPUT_VALUE")));
    }

    @Test
    void bookReturnsSoldOutWhenServiceThrowsSoldOut() throws Exception {
        given(bookingService.book(eq("key-1"), any(BookingRequest.class)))
                .willThrow(new BusinessException(ErrorCode.SOLD_OUT));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code", is("SOLD_OUT")))
                .andExpect(jsonPath("$.detail", is("재고가 모두 소진되었습니다.")));
    }

    @Test
    void bookReturnsConflictWhenServiceThrowsIdempotencyKeyConflict() throws Exception {
        given(bookingService.book(eq("key-1"), any(BookingRequest.class)))
                .willThrow(new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("IDEMPOTENCY_KEY_CONFLICT")));
    }

    private String validRequest() {
        return """
                {
                  "userId": 1,
                  "productId": 1,
                  "payment": {
                    "primaryMethod": "CARD",
                    "paymentAmount": 70000,
                    "pointAmount": 30000
                  }
                }
                """;
    }
}
