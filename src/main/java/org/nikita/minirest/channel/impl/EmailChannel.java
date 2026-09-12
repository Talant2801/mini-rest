package org.nikita.minirest.channel.impl;

import org.nikita.minirest.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    @Override
    public void send(String message) {
        log.info("[email] {}", message);
    }

    @Override
    public String getName() {
        return "email";
    }
}
