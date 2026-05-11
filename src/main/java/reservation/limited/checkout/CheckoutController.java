package reservation.limited.checkout;

import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reservation.limited.common.ApiResponse;

@Validated
@RestController
@RequestMapping("/api/v1/products")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @GetMapping("/{productId}/checkout")
    public ApiResponse<CheckoutResponse> getCheckout(
            @PathVariable @Positive Long productId,
            @RequestParam @Positive Long userId
    ) {
        // 주문 진입 화면에 필요한 checkout 정보를 공통 응답 형식으로 반환한다.
        return ApiResponse.success(checkoutService.getCheckout(productId, userId));
    }
}
