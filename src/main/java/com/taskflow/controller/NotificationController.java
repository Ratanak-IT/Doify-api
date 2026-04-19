package com.taskflow.controller;

import com.taskflow.domain.User;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Website notifications for tasks, mentions, and invitations")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE stream for real-time notifications (pass JWT as ?token=)")
    public SseEmitter stream(@AuthenticationPrincipal User currentUser) {
        return notificationService.createEmitter(currentUser);
    }

    @GetMapping
    @Operation(summary = "Get all notifications (paginated, newest first)")
    public PageResponse<NotificationResponse> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser
    ) {
        return notificationService.getMyNotifications(currentUser, page, size);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread notifications")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal User currentUser) {
        return Map.of("unreadCount", notificationService.countUnread(currentUser));
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark all notifications as read")
    public void markAllAsRead(@AuthenticationPrincipal User currentUser) {
        notificationService.markAllAsRead(currentUser);
    }

    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark a single notification as read")
    public void markAsRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal User currentUser
    ) {
        notificationService.markAsRead(notificationId, currentUser);
    }
}