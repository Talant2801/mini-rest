package org.nikita.minirest.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nikita.minirest.channel.Channel;
import org.nikita.minirest.config.DeliveryPolicy;
import org.nikita.minirest.dto.NotificationRequest;
import org.nikita.minirest.event.MessageSentEvent;
import org.nikita.minirest.model.Message;
import org.nikita.minirest.repository.NotificationRepository;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock NotificationRepository repository;
    @Mock Channel email;
    @Mock Channel slack;
    @Mock DeliveryPolicy policy;
    @Mock ApplicationEventPublisher eventPublisher;

    NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(repository, List.of(email, slack), policy, eventPublisher);
    }

    @Test
    @DisplayName("Notifies the requested channel only")
    void notifiesTheRequestedChannelOnly() {
        when(slack.getName()).thenReturn("slack");
        when(policy.getMaxLength()).thenReturn(1000);

        notificationService.send(new NotificationRequest("hello", "slack"));

        verify(slack).send("hello");
        verify(email, never()).send(anyString());
        verify(repository).save(any(Message.class));
        verifyNoMoreInteractions(repository);
        verify(eventPublisher).publishEvent(any(MessageSentEvent.class));
    }

    @Test
    @DisplayName("Falls back to the default channel when the requested one is unknown")
    void fallsBackIfChannelIsInvalid() {
        when(slack.getName()).thenReturn("slack");
        when(policy.getMaxLength()).thenReturn(1000);
        when(policy.getDefaultChannel()).thenReturn("slack");

        var result = notificationService.send(new NotificationRequest("hello", "telegram"));

        verify(slack).send("hello");
        verify(email, never()).send(anyString());
        verify(repository).save(any(Message.class));
        verifyNoMoreInteractions(repository);
        ArgumentCaptor<MessageSentEvent> event = ArgumentCaptor.forClass(MessageSentEvent.class);  // empty net
        verify(eventPublisher).publishEvent(event.capture());   // verify runs, net catches the argument
        assertEquals("slack", event.getValue().channel());   // now inspect what you caught

        assertEquals("telegram", result.getChannel());
        assertEquals(List.of("slack"), result.getChannelUsed());
    }

    @Test
    @DisplayName("Rejects a message if it is too long")
    void rejectsMessageIfItIsTooLong() {
        when(policy.getMaxLength()).thenReturn(50);

        assertThrows(IllegalArgumentException.class, () -> notificationService
                .send(new NotificationRequest("x".repeat(51), "slack")));
        verifyNoInteractions(repository, eventPublisher, slack, email);
    }

    @Test
    @DisplayName("Rejects a message if it is empty")
    void rejectsMessageIfItIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> notificationService
                .send(new NotificationRequest("", "slack")));
        verifyNoInteractions(repository, eventPublisher, slack, email);
    }
}
