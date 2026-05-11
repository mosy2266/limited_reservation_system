package reservation.limited.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reservation.limited.common.BusinessException;
import reservation.limited.common.ErrorCode;
import reservation.limited.common.GlobalExceptionHandler;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheckoutController.class)
@Import(GlobalExceptionHandler.class)
class CheckoutControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutService checkoutService;

    @Test
    void getCheckoutReturnsSuccessResponse() throws Exception {
        CheckoutResponse response = new CheckoutResponse(
                new CheckoutResponse.ProductResponse(
                        1L,
                        "테스트 상품",
                        100_000L,
                        LocalDateTime.of(2026, 5, 10, 15, 0),
                        LocalDateTime.of(2026, 5, 11, 11, 0)
                ),
                new CheckoutResponse.UserResponse(10L, 30_000L)
        );
        given(checkoutService.getCheckout(1L, 10L)).willReturn(response);

        mockMvc.perform(get("/api/v1/products/{productId}/checkout", 1L)
                        .param("userId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("SUCCESS")))
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.data.product.id", is(1)))
                .andExpect(jsonPath("$.data.product.name", is("테스트 상품")))
                .andExpect(jsonPath("$.data.product.price", is(100000)))
                .andExpect(jsonPath("$.data.product.checkInAt", is("2026-05-10T15:00:00")))
                .andExpect(jsonPath("$.data.product.checkOutAt", is("2026-05-11T11:00:00")))
                .andExpect(jsonPath("$.data.user.id", is(10)))
                .andExpect(jsonPath("$.data.user.availablePoint", is(30000)));

        verify(checkoutService).getCheckout(1L, 10L);
    }

    @Test
    void getCheckoutReturnsNotFoundWhenServiceThrowsProductNotFound() throws Exception {
        given(checkoutService.getCheckout(999L, 10L))
                .willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/products/{productId}/checkout", 999L)
                        .param("userId", "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("PRODUCT_NOT_FOUND")))
                .andExpect(jsonPath("$.detail", is("상품을 찾을 수 없습니다.")))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getCheckoutReturnsConflictWhenServiceThrowsProductNotAvailable() throws Exception {
        given(checkoutService.getCheckout(1L, 10L))
                .willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE));

        mockMvc.perform(get("/api/v1/products/{productId}/checkout", 1L)
                        .param("userId", "10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PRODUCT_NOT_AVAILABLE")))
                .andExpect(jsonPath("$.detail", is("판매 가능한 상품이 아닙니다.")));
    }

    @Test
    void getCheckoutReturnsGoneWhenServiceThrowsSaleClosed() throws Exception {
        given(checkoutService.getCheckout(1L, 10L))
                .willThrow(new BusinessException(ErrorCode.SALE_CLOSED));

        mockMvc.perform(get("/api/v1/products/{productId}/checkout", 1L)
                        .param("userId", "10"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code", is("SALE_CLOSED")))
                .andExpect(jsonPath("$.detail", is("판매가 종료된 상품입니다.")));
    }

    @Test
    void getCheckoutReturnsBadRequestWhenUserIdIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/products/{productId}/checkout", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_INPUT_VALUE")))
                .andExpect(jsonPath("$.detail", is("요청 값이 올바르지 않습니다.")));
    }

    @Test
    void getCheckoutReturnsBadRequestWhenPathVariableIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/products/{productId}/checkout", 0L)
                        .param("userId", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_INPUT_VALUE")))
                .andExpect(jsonPath("$.detail", is("요청 값이 올바르지 않습니다.")));
    }

    @Test
    void getCheckoutReturnsBadRequestWhenUserIdIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/products/{productId}/checkout", 1L)
                        .param("userId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_INPUT_VALUE")))
                .andExpect(jsonPath("$.detail", is("요청 값이 올바르지 않습니다.")));
    }
}
