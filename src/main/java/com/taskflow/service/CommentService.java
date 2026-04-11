package com.taskflow.service;

import com.taskflow.domain.*;
import com.taskflow.domain.enums.NotificationType;
import com.taskflow.dto.request.CommentRequest.*;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.exception.AccessDeniedException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.CommentRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.taskflow.service.impl.AuthServiceImpl.mapUserResponse;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    // Regex to detect @username mentions
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    @Transactional
    public CommentResponse addComment(UUID taskId, CreateCommentRequest request, User currentUser) {
        Task task = findTaskAndVerifyAccess(taskId, currentUser);

        Comment comment = Comment.builder()
                .content(request.content())
                .task(task)
                .author(currentUser)
                .build();
        comment = commentRepository.save(comment);

        processMentions(request.content(), task, currentUser, comment);
        notifyTaskParticipants(task, currentUser, comment);

        return mapCommentResponse(comment);
    }

    public PageResponse<CommentResponse> getComments(UUID taskId, User currentUser, int page, int size) {
        Task task = findTaskAndVerifyAccess(taskId, currentUser);
        Page<Comment> comments = commentRepository.findByTask(task, PageRequest.of(page, size));
        return toPageResponse(comments.map(this::mapCommentResponse));
    }

    @Transactional
    public CommentResponse updateComment(UUID taskId, UUID commentId,
                                         UpdateCommentRequest request, User currentUser) {
        findTaskAndVerifyAccess(taskId, currentUser);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (!comment.getAuthor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only edit your own comments");
        }

        comment.setContent(request.content());
        comment = commentRepository.save(comment);
        return mapCommentResponse(comment);
    }

    @Transactional
    public void deleteComment(UUID taskId, UUID commentId, User currentUser) {
        findTaskAndVerifyAccess(taskId, currentUser);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (!comment.getAuthor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only delete your own comments");
        }
        commentRepository.delete(comment);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void processMentions(String content, Task task, User commenter, Comment comment) {
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String username = matcher.group(1);
            userRepository.findByUsername(username).ifPresent(mentionedUser -> {
                if (!mentionedUser.getId().equals(commenter.getId())) {
                    notificationService.send(
                            mentionedUser,
                            NotificationType.MENTIONED_IN_COMMENT,
                            commenter.getFullName() + " mentioned you in a comment on task: " + task.getTitle(),
                            comment.getId(),
                            "COMMENT"
                    );
                    String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
                    emailService.sendMentionEmail(
                            mentionedUser.getEmail(),
                            mentionedUser.getFullName(),
                            commenter.getFullName(),
                            task.getTitle(),
                            preview
                    );
                }
            });
        }
    }

    private void notifyTaskParticipants(Task task, User commenter, Comment comment) {
        String preview = comment.getContent().length() > 100
                ? comment.getContent().substring(0, 100) + "..."
                : comment.getContent();
        String message = commenter.getFullName() + " commented on task: " + task.getTitle();

        // Notify assignee if not the commenter
        if (task.getAssignee() != null && !task.getAssignee().getId().equals(commenter.getId())) {
            notificationService.send(
                    task.getAssignee(),
                    NotificationType.COMMENT_ADDED,
                    message,
                    comment.getId(),
                    "COMMENT"
            );
            // ✅ NEW: Send email to assignee
            emailService.sendCommentAddedEmail(
                    task.getAssignee().getEmail(),
                    task.getAssignee().getFullName(),
                    commenter.getFullName(),
                    task.getTitle(),
                    preview
            );
        }

        // Notify task creator if not the commenter and not already notified as assignee
        if (!task.getCreator().getId().equals(commenter.getId()) &&
                (task.getAssignee() == null ||
                        !task.getCreator().getId().equals(task.getAssignee().getId()))) {
            notificationService.send(
                    task.getCreator(),
                    NotificationType.COMMENT_ADDED,
                    message,
                    comment.getId(),
                    "COMMENT"
            );
            // ✅ NEW: Send email to creator
            emailService.sendCommentAddedEmail(
                    task.getCreator().getEmail(),
                    task.getCreator().getFullName(),
                    commenter.getFullName(),
                    task.getTitle(),
                    preview
            );
        }
    }

    private Task findTaskAndVerifyAccess(UUID taskId, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        boolean isCreator  = task.getCreator().getId().equals(user.getId());
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
        boolean isProjectMember = task.getProject() != null &&
                teamMemberRepository.existsByTeamAndUser(task.getProject().getTeam(), user);

        if (!isCreator && !isAssignee && !isProjectMember) {
            throw new AccessDeniedException("You do not have access to this task");
        }
        return task;
    }

    private CommentResponse mapCommentResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                mapUserResponse(comment.getAuthor()),
                comment.getTask().getId(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast()
        );
    }
}