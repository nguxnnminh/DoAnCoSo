package com.shop.clothingstore.dto.api;

import java.time.LocalDateTime;

import com.shop.clothingstore.entity.Notification;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private String title;
    private String message;
    private boolean read;
    private String type;
    private Long referenceId;
    private String referenceType;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getTitle(),
                n.getMessage(),
                n.isRead(),
                n.getType(),
                n.getReferenceId(),
                n.getReferenceType(),
                n.getCreatedAt()
        );
    }
}
