package org.nikita.minirest.repository.impl;

import org.nikita.minirest.model.Message;
import org.nikita.minirest.repository.NotificationRepository;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemmoryNotificationRepository implements NotificationRepository {

    private Map<UUID, Message> storage = new ConcurrentHashMap<>();

    @Override
    public void save(Message message) {
        storage.put(message.getId(), message);
    }

    @Override
    public void update(UUID id, Message message) {
        storage.put(id, message);
    }

    @Override
    public void deleteById(UUID id) {
        storage.remove(id);
    }

    @Override
    public List<Message> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Optional<Message> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }
}
