package com.taskflow.dto.response;

import com.taskflow.domain.enums.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class Responses {

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            UserResponse user,
            String verificationToken
    ) {}

    public record UserResponse(
            UUID id,
            String fullName,
            String username,
            String email,
            String profilePhoto,
            Gender gender,
            boolean isVerified,
            Instant createdAt
    ) {}

    public record TeamResponse(
            UUID id,
            String name,
            String description,
            UserResponse owner,
            long memberCount,
            Instant createdAt
    ) {}

    public record TeamMemberResponse(
            UUID id,
            UserResponse user,
            Role role,
            Instant joinedAt
    ) {}

    public record TeamInvitationResponse(
            UUID id,
            String teamName,
            String inviterName,
            String inviteeEmail,
            Role invitedRole,
            InvitationStatus status,
            Instant expiresAt
    ) {}

    public record ProjectResponse(
            UUID id,
            String name,
            String description,
            LocalDate startDate,
            LocalDate dueDate,
            String color,
            UUID teamId,
            String teamName,
            UserResponse creator,
            long totalTasks,
            long completedTasks,
            int progressPercent,
            Instant createdAt
    ) {}

    public record TaskResponse(
            UUID id,
            String title,
            String description,
            TaskStatus status,
            Priority priority,
            LocalDate dueDate,
            UUID projectId,
            String projectName,
            UserResponse creator,
            UserResponse assignee,
            UUID parentTaskId,
            long subtaskCount,
            long commentCount,
            List<AttachmentResponse> attachments,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record SubtaskResponse(
            UUID id,
            String title,
            TaskStatus status,
            Priority priority,
            LocalDate dueDate,
            UserResponse assignee
    ) {}

    public record CommentResponse(
            UUID id,
            String content,
            UserResponse author,
            UUID taskId,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record AttachmentResponse(
            UUID id,
            String fileName,
            String fileUrl,
            String contentType,
            Long fileSize,
            UserResponse uploader,
            Instant uploadedAt
    ) {}

    public record NotificationResponse(
            UUID id,
            NotificationType type,
            String message,
            UUID referenceId,
            String referenceType,
            boolean isRead,
            Instant createdAt
    ) {}

    public record DashboardResponse(
            long totalProjects,
            long totalTasks,
            long myTasks,
            long completedTasks,
            long pendingTasks,
            long overdueTasks,
            long totalTeamMembers,
            List<UpcomingTaskResponse> upcomingDueDates,
            List<ActivityResponse> recentActivities,
            List<ProjectProgressResponse> projectProgressSummary
    ) {}

    public record UpcomingTaskResponse(
            UUID id,
            String title,
            LocalDate dueDate,
            Priority priority,
            String projectName
    ) {}

    public record ActivityResponse(
            String activityType,
            String description,
            Instant timestamp
    ) {}

    public record ProjectProgressResponse(
            UUID projectId,
            String projectName,
            long totalTasks,
            long completedTasks,
            int progressPercent
    ) {}

    public record PageResponse<T>(
            List<T> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            int totalPages,
            boolean last
    ) {}

    public record MessageResponse(String message) {}
}
