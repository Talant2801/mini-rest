package org.nikita.minirest.channel.impl;

import org.nikita.minirest.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConsoleChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(ConsoleChannel.class);

    @Override
    public void send(String message) {
        log.info("[console] {}", message);
    }

    @Override
    public String getName() {
        return "console";
    }
}
