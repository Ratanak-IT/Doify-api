package com.taskflow.service;

import com.taskflow.domain.Notification;
import com.taskflow.domain.User;
import com.taskflow.domain.enums.NotificationType;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.exception.AccessDeniedException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void send(User recipient, NotificationType type, String message,
                     UUID referenceId, String referenceType) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .message(message)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .build();
        notificationRepository.save(notification);
    }

//    create notification
public void createNotification(User receiver,
                               NotificationType type,
                               String message,
                               UUID referenceId) {

    Notification notification = Notification.builder()
            .recipient(receiver)
            .type(type)
            .message(message)
            .referenceId(referenceId)
            .isRead(false)
            .build();

    notificationRepository.save(notification);
}

    public PageResponse<NotificationResponse> getMyNotifications(User user, int page, int size) {
        Page<Notification> result = notificationRepository
                .findByRecipientOrderByCreatedAtDesc(user, PageRequest.of(page, size));

        Page<NotificationResponse> mapped = result.map(this::mapResponse);
        return toPageResponse(mapped);
    }

    public long countUnread(User user) {
        return notificationRepository.countByRecipientAndIsReadFalse(user);
    }

    @Transactional
    public void markAllAsRead(User user) {
        notificationRepository.markAllAsReadByRecipient(user);
    }

    @Transactional
    public void markAsRead(UUID notificationId, User user) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new AccessDeniedException("You cannot access this notification");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private NotificationResponse mapResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getMessage(),
                n.getReferenceId(),
                n.getReferenceType(),
                n.isRead(),
                n.getCreatedAt()
        );
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
