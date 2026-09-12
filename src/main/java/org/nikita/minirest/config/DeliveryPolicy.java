package org.nikita.minirest.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Delivery settings bound from {@code notification.*} properties.
 * <p>
 * Both placeholders are intentionally declared without a default value: a context that does
 * not load {@code application.yaml} fails fast instead of silently using fallbacks.
 */
@Component
@Getter
public class DeliveryPolicy {

    private static final Logger log = LoggerFactory.getLogger(DeliveryPolicy.class);

    private final String defaultChannel;
    private final int maxLength;

    public DeliveryPolicy(@Value("${notification.default-channel}") String defaultChannel,
                          @Value("${notification.max-length}") int maxLength) {
        this.defaultChannel = defaultChannel;
        this.maxLength = maxLength;
    }

    @PostConstruct
    void logPolicy() {
        log.info("DeliveryPolicy initialised: defaultChannel={}, maxLength={}", defaultChannel, maxLength);
    }
}
