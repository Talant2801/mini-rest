package org.nikita.minirest.model;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class Message {

    private final UUID id;
    private final String message;
    private final String channel;
    private final String correlationId;
    private final List<String> channelUsed;

    public Message(String message, String channel, String correlationId, List<String> channelUsed) {
        this(UUID.randomUUID(), message, channel, correlationId, channelUsed);
    }

    public Message(UUID id, String message, String channel, String correlationId, List<String> channelUsed) {
        this.id = id;
        this.message = message;
        this.channel = channel;
        this.correlationId = correlationId;
        this.channelUsed = channelUsed == null ? List.of() : List.copyOf(channelUsed);
    }
}
