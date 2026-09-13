package org.nikita.minirest.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationRequest {
    private String message;
    private String channel;
}
