package org.nikita.minirest.integration.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nikita.minirest.config.DeliveryPolicy;
import org.nikita.minirest.dto.NotificationRequest;
import org.nikita.minirest.event.MessageSentEvent;
import org.nikita.minirest.exception.MessageNotFoundException;
import org.nikita.minirest.model.Message;
import org.nikita.minirest.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test: the whole {@code notification.*} slice wired by Spring, with the real
 * {@code InMemmoryNotificationRepository} and the three real channels. No mocks — the
 * collaborator interactions are covered by {@code NotificationServiceTest} instead.
 */
@SpringBootTest
@RecordApplicationEvents
public class NotificationServiceIT {

    @Value("${notification.default-channel}")
    String defaultChannel;

    @Autowired
    NotificationService notificationService;

    @Autowired
    DeliveryPolicy deliveryPolicy;

    @Test
    @DisplayName("Checks if ioc container runs")
    void contextStarts() {
        assertNotNull(notificationService);
    }

    @Test
    @DisplayName("DeliveryPolicy is bound from application.yaml, so the context read the YAML")
    void deliveryPolicyIsBoundFromApplicationYaml() {
        assertEquals("console", deliveryPolicy.getDefaultChannel());
        assertEquals(200, deliveryPolicy.getMaxLength());
    }

    @Test
    @DisplayName("A sent message is persisted and can be read back by its id")
    void savedMessageCanBeFoundById() {
        var message = notificationService.send(new NotificationRequest("Hello, world!", "slack"));

        Optional<Message> foundMessage = notificationService.findById(message.getId());

        assertTrue(foundMessage.isPresent());
        assertEquals("Hello, world!", foundMessage.get().getMessage());
        assertEquals("slack", foundMessage.get().getChannel());
        assertEquals(List.of("slack"), foundMessage.get().getChannelUsed());
    }

    @Test
    @DisplayName("An unknown channel is delivered through the configured default channel")
    void unknownChannelFallsBackToDefaultChannel() {
        var message = notificationService.send(new NotificationRequest("Hello, world!", "telegram"));

        assertEquals(List.of(defaultChannel), message.getChannelUsed());
        assertEquals("telegram", message.getChannel());
    }

    @Test
    @DisplayName("send() publishes one MessageSentEvent carrying the channel actually used")
    void sendPublishesMessageSentEvent(ApplicationEvents events) {
        var message = notificationService.send(new NotificationRequest("Hello, events!", "email"));

        List<MessageSentEvent> published = events.stream(MessageSentEvent.class).toList();
        assertEquals(1, published.size());
        assertEquals(message.getId(), published.get(0).messageId());
        assertEquals("email", published.get(0).channel());
        assertEquals(message.getCorrelationId(), published.get(0).correlationId());
    }

    @Test
    @DisplayName("update() replaces the stored content and keeps the original correlationId")
    void updateKeepsCorrelationId() {
        var created = notificationService.send(new NotificationRequest("First", "slack"));

        var updated = notificationService.update(created.getId(), new NotificationRequest("Second", "email"));

        assertEquals(created.getId(), updated.getId());
        assertEquals(created.getCorrelationId(), updated.getCorrelationId());
        assertEquals("Second", notificationService.findById(created.getId()).orElseThrow().getMessage());
        assertEquals(List.of("email"), updated.getChannelUsed());
    }

    @Test
    @DisplayName("update() on an unknown id throws and stores nothing")
    void updateOnUnknownIdThrows() {
        UUID unknownId = UUID.randomUUID();
        int storedBefore = notificationService.findAll().size();

        assertThrows(MessageNotFoundException.class,
                () -> notificationService.update(unknownId, new NotificationRequest("Nope", "slack")));

        assertTrue(notificationService.findById(unknownId).isEmpty());
        assertEquals(storedBefore, notificationService.findAll().size());
    }

    @Test
    @DisplayName("deleteById() removes the message; a second delete throws")
    void deletedMessageIsGone() {
        var message = notificationService.send(new NotificationRequest("Delete me", "slack"));

        notificationService.deleteById(message.getId());

        assertTrue(notificationService.findById(message.getId()).isEmpty());
        assertThrows(MessageNotFoundException.class, () -> notificationService.deleteById(message.getId()));
    }

    @Test
    @DisplayName("findAll() returns every message still stored")
    void findAllReturnsStoredMessages() {
        var first = notificationService.send(new NotificationRequest("One", "slack"));
        var second = notificationService.send(new NotificationRequest("Two", "email"));

        List<UUID> ids = notificationService.findAll().stream().map(Message::getId).toList();

        assertTrue(ids.contains(first.getId()));
        assertTrue(ids.contains(second.getId()));
    }

    @Test
    @DisplayName("A blank message is rejected before anything is dispatched or stored")
    void blankMessageIsRejected() {
        int storedBefore = notificationService.findAll().size();

        assertThrows(IllegalArgumentException.class,
                () -> notificationService.send(new NotificationRequest("   ", "slack")));

        assertEquals(storedBefore, notificationService.findAll().size());
    }

    @Test
    @DisplayName("The configured max-length of 200 is enforced: 200 chars pass, 201 are rejected")
    void tooLongMessageIsRejected() {
        String atLimit = "a".repeat(deliveryPolicy.getMaxLength());
        String overLimit = "a".repeat(deliveryPolicy.getMaxLength() + 1);
        int storedBefore = notificationService.findAll().size();

        assertNotNull(notificationService.send(new NotificationRequest(atLimit, "slack")));
        assertThrows(IllegalArgumentException.class,
                () -> notificationService.send(new NotificationRequest(overLimit, "slack")));

        assertEquals(storedBefore + 1, notificationService.findAll().size());
        assertFalse(notificationService.findAll().stream()
                .anyMatch(message -> overLimit.equals(message.getMessage())));
    }
}
