package com.taskflow.service;

import com.taskflow.domain.Notification;
import com.taskflow.domain.User;
import com.taskflow.domain.enums.NotificationType;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.exception.AccessDeniedException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    // ── SSE ──────────────────────────────────────────────────────────────────

    public SseEmitter createEmitter(User user) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        UUID userId = user.getId();
        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));
        try {
            emitter.send(SseEmitter.event().name("ping").data("connected"));
        } catch (IOException e) {
            emitters.remove(userId);
        }
        return emitter;
    }

    public void pushToUser(UUID userId, Object payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("notification").data(payload));
        } catch (IOException e) {
            log.warn("Failed to push SSE to user {}, removing emitter", userId);
            emitters.remove(userId);
        }
    }

    // ── Core ─────────────────────────────────────────────────────────────────

    @Transactional
    public boolean send(User recipient, NotificationType type, String message,
                        UUID referenceId, String referenceType) {
        if (isDailySchedulerType(type) && referenceId != null) {
            Instant startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
            if (notificationRepository.existsTodayNotification(recipient, type, referenceId, startOfDay))
                return false;
        }
        Notification notification = Notification.builder()
                .recipient(recipient).type(type).message(message)
                .referenceId(referenceId).referenceType(referenceType).build();
        notificationRepository.save(notification);
        pushToUser(recipient.getId(), mapResponse(notification));
        return true;
    }

    @Transactional
    public void createNotification(User receiver, NotificationType type,
                                   String message, UUID referenceId) {
        Notification notification = Notification.builder()
                .recipient(receiver).type(type).message(message)
                .referenceId(referenceId).isRead(false).build();
        notificationRepository.save(notification);
        pushToUser(receiver.getId(), mapResponse(notification));
    }

    public PageResponse<NotificationResponse> getMyNotifications(User user, int page, int size) {
        Page<Notification> result = notificationRepository
                .findByRecipientOrderByCreatedAtDesc(user, PageRequest.of(page, size));
        return toPageResponse(result.map(this::mapResponse));
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
        if (!notification.getRecipient().getId().equals(user.getId()))
            throw new AccessDeniedException("You cannot access this notification");
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private boolean isDailySchedulerType(NotificationType type) {
        return type == NotificationType.DUE_DATE_REMINDER || type == NotificationType.OVERDUE_TASK;
    }

    private NotificationResponse mapResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getMessage(),
                n.getReferenceId(), n.getReferenceType(), n.isRead(), n.getCreatedAt());
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}