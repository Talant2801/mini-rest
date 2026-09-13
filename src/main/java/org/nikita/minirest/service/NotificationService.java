package org.nikita.minirest.service;

import org.nikita.minirest.channel.Channel;
import org.nikita.minirest.config.DeliveryPolicy;
import org.nikita.minirest.dto.NotificationRequest;
import org.nikita.minirest.exception.MessageNotFoundException;
import org.nikita.minirest.model.Message;
import org.nikita.minirest.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final List<Channel> channels;
    private final DeliveryPolicy policy;

    public NotificationService(NotificationRepository repository,
                               List<Channel> channels,
                               DeliveryPolicy policy) {
        this.repository = repository;
        this.channels = channels;
        this.policy = policy;
    }

    public Message send(NotificationRequest request) {
        validate(request);

        Channel target = resolveChannel(request.getChannel());
        target.send(request.getMessage());

        Message message = new Message(request.getMessage(),
                request.getChannel(),
                UUID.randomUUID().toString(),
                List.of(target.getName()));
        repository.save(message);
        return message;
    }

    public Message update(UUID id, NotificationRequest request) {
        validate(request);

        Message existing = repository.findById(id)
                .orElseThrow(() -> new MessageNotFoundException(id));

        Channel target = resolveChannel(request.getChannel());
        target.send(request.getMessage());

        Message updated = new Message(id,
                request.getMessage(),
                request.getChannel(),
                existing.getCorrelationId(),
                List.of(target.getName()));
        repository.update(id, updated);
        return updated;
    }

    public List<Message> findAll() {
        return repository.findAll();
    }

    public Optional<Message> findById(UUID id) {
        return repository.findById(id);
    }

    public void deleteById(UUID id) {
        if (repository.findById(id).isEmpty()) {
            throw new MessageNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private void validate(NotificationRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("Message must not be blank");
        }
        if (request.getMessage().length() > policy.getMaxLength()) {
            throw new IllegalArgumentException(
                    "Message must not exceed " + policy.getMaxLength() + " characters");
        }
    }

    /**
     * Resolves the requested channel by name, falling back to the configured default channel
     * when the requested one is unknown, blank or missing.
     */
    private Channel resolveChannel(String requested) {
        return findByName(requested)
                .or(() -> findByName(policy.getDefaultChannel()))
                .orElseThrow(() -> new IllegalStateException(
                        "Default channel '" + policy.getDefaultChannel() + "' is not registered"));
    }

    private Optional<Channel> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return channels.stream()
                .filter(channel -> name.equalsIgnoreCase(channel.getName()))
                .findFirst();
    }
}
