package reservation.limited.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OutboxPayloadSerializer {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Outbox payload serialization failed.", exception);
        }
    }
}
