package reservation.limited.booking;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class BookingNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String generate() {
        // 예약 번호는 시간 기반 prefix와 짧은 난수를 조합해 DB unique 제약으로 최종 보장한다.
        return "B" + LocalDateTime.now().format(FORMATTER) + UUID.randomUUID().toString().substring(0, 8);
    }
}
