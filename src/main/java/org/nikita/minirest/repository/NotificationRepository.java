package org.nikita.minirest.repository;

import org.nikita.minirest.model.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    void save(Message message);

    void update(UUID id, Message message);

    void deleteById(UUID id);

    List<Message> findAll();

    Optional<Message> findById(UUID id);
}
