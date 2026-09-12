package org.nikita.minirest.channel.impl;

import org.nikita.minirest.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SlackChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(SlackChannel.class);

    @Override
    public void send(String message) {
        log.info("[slack] {}", message);
    }

    @Override
    public String getName() {
        return "slack";
    }
}
