package reservation.limited.common;

import java.time.LocalDateTime;

public record ApiResponse<T>(
        String code,
        String detail,
        T data,
        LocalDateTime timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", null, data, LocalDateTime.now());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.name(), errorCode.getDetail(), null, LocalDateTime.now());
    }
}
