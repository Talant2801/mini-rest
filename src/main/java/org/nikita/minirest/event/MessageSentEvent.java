package org.nikita.minirest.event;

import java.util.UUID;

/**
 * Published every time a message is actually handed to a channel.
 */
public record MessageSentEvent(UUID messageId, String channel, String correlationId) {
}
