package reservation.limited.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_PAYMENT_AMOUNT(HttpStatus.BAD_REQUEST, "결제 금액이 올바르지 않습니다."),
    INVALID_PAYMENT_COMBINATION(HttpStatus.BAD_REQUEST, "결제 수단 조합이 올바르지 않습니다."),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "멱등성 키가 누락되었습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    PRODUCT_NOT_AVAILABLE(HttpStatus.CONFLICT, "판매 가능한 상품이 아닙니다."),
    SALE_NOT_OPEN(HttpStatus.CONFLICT, "판매가 아직 시작되지 않았습니다."),
    DUPLICATED_BOOKING(HttpStatus.CONFLICT, "이미 예약한 상품입니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "같은 멱등성 키로 다른 요청이 존재합니다."),
    IDEMPOTENCY_ALREADY_PROCESSING(HttpStatus.CONFLICT, "같은 요청이 이미 처리 중입니다."),
    SALE_CLOSED(HttpStatus.GONE, "판매가 종료된 상품입니다."),
    SOLD_OUT(HttpStatus.GONE, "재고가 모두 소진되었습니다."),
    PAYMENT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "결제가 실패했습니다."),
    CARD_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "카드 한도를 초과했습니다."),
    POINT_NOT_ENOUGH(HttpStatus.UNPROCESSABLE_ENTITY, "포인트가 부족합니다."),
    PAY_BALANCE_NOT_ENOUGH(HttpStatus.UNPROCESSABLE_ENTITY, "페이 잔액이 부족합니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String detail;

    ErrorCode(HttpStatus status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }
}
