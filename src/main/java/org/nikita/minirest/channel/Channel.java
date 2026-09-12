package org.nikita.minirest.channel;

public interface Channel {

    void send(String message);

    String getName();
}
