package reservation.limited.booking;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class BookingRequestHashGenerator {

    public String generate(BookingRequest request) {
        String source = "%d:%d:%s:%d:%d".formatted(
                request.userId(),
                request.productId(),
                request.payment().primaryMethod(),
                request.payment().paymentAmount(),
                request.payment().pointAmount()
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
