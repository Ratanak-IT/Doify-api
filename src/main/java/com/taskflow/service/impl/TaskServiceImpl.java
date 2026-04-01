package com.taskflow.service.impl;

import com.taskflow.domain.*;
import com.taskflow.domain.enums.NotificationType;
import com.taskflow.domain.enums.Priority;
import com.taskflow.domain.enums.TaskStatus;
import com.taskflow.dto.request.TaskRequests.*;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.exception.AccessDeniedException;
import com.taskflow.exception.BadRequestException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.*;
import com.taskflow.service.EmailService;
import com.taskflow.service.NotificationService;
import com.taskflow.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.taskflow.service.impl.AuthServiceImpl.mapUserResponse;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AttachmentRepository attachmentRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    // ─── Personal Tasks ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public TaskResponse createPersonalTask(CreateTaskRequest request, User currentUser) {
        Task task = buildTask(request, currentUser, null);
        task = taskRepository.save(task);
        return mapTaskResponse(task);
    }

    @Override
    public PageResponse<TaskResponse> getPersonalTasks(
            User currentUser,
            String search,
            String status,
            String priority,
            int page,
            int size
    ) {
        TaskStatus taskStatus = parseEnum(TaskStatus.class, status);
        Priority taskPriority = parseEnum(Priority.class, priority);

        Page<Task> tasks;

        if (search == null || search.isBlank()) {
            tasks = taskRepository.findByCreatorAndProjectIsNull(
                    currentUser,
                    PageRequest.of(page, size)
            );
        } else {
            tasks = taskRepository.searchPersonalTasks(
                    currentUser,
                    search,
                    taskStatus,
                    taskPriority,
                    PageRequest.of(page, size)
            );
        }

        return toPageResponse(tasks.map(this::mapTaskResponse));
    }


    @Override
    @Transactional
    public TaskResponse createProjectTask(UUID projectId, CreateTaskRequest request, User currentUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!teamMemberRepository.existsByTeamAndUser(project.getTeam(), currentUser)) {
            throw new AccessDeniedException("You do not have access to this project");
        }

        Task task = buildTask(request, currentUser, project);
        task = taskRepository.save(task);

        // Notify assignee if different from creator
        if (task.getAssignee() != null && !task.getAssignee().getId().equals(currentUser.getId())) {
            notifyAssignment(task, currentUser);
        }

        return mapTaskResponse(task);
    }

    @Override
    public PageResponse<TaskResponse> getProjectTasks(UUID projectId, User currentUser, String search,
                                                      String status, String priority, UUID assigneeId,
                                                      int page, int size) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!teamMemberRepository.existsByTeamAndUser(project.getTeam(), currentUser)) {
            throw new AccessDeniedException("You do not have access to this project");
        }

        TaskStatus taskStatus = parseEnum(TaskStatus.class, status);
        Priority taskPriority = parseEnum(Priority.class, priority);

        Page<Task> tasks = taskRepository.searchProjectTasks(
                project, search, taskStatus, taskPriority, assigneeId, PageRequest.of(page, size)
        );
        return toPageResponse(tasks.map(this::mapTaskResponse));
    }

    // ─── Common ──────────────────────────────────────────────────────────────

    @Override
    public TaskResponse getTask(UUID taskId, User currentUser) {
        Task task = findTaskAndVerifyAccess(taskId, currentUser);
        return mapTaskResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse updateTask(UUID taskId, UpdateTaskRequest request, User currentUser) {
        Task task = findTaskAndVerifyAccess(taskId, currentUser);

        User previousAssignee = task.getAssignee();

        if (request.title() != null) task.setTitle(request.title());
        if (request.description() != null) task.setDescription(request.description());
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.status() != null) task.setStatus(request.status());
        if (request.dueDate() != null) task.setDueDate(request.dueDate());

        if (request.assigneeId() != null) {
            User newAssignee = userRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assignee not found"));
            task.setAssignee(newAssignee);

            boolean isNewAssignment = previousAssignee == null ||
                    !previousAssignee.getId().equals(newAssignee.getId());
            if (isNewAssignment && !newAssignee.getId().equals(currentUser.getId())) {
                notifyAssignment(task, currentUser);
            }
        }

        task = taskRepository.save(task);
        return mapTaskResponse(task);
    }

    @Override
    @Transactional
    public void deleteTask(UUID taskId, User currentUser) {
        Task task = findTaskAndVerifyAccess(taskId, currentUser);

        boolean isOwner = task.getCreator().getId().equals(currentUser.getId());
        boolean isProjectAdmin = task.getProject() != null &&
                teamMemberRepository.findByTeamAndUser(task.getProject().getTeam(), currentUser)
                        .map(m -> m.getRole().ordinal() <= com.taskflow.domain.enums.Role.ADMIN.ordinal())
                        .orElse(false);

        if (!isOwner && !isProjectAdmin) {
            throw new AccessDeniedException("You do not have permission to delete this task");
        }
        taskRepository.delete(task);
    }

    @Override
    public List<TaskResponse> getSubtasks(UUID taskId, User currentUser) {
        Task parent = findTaskAndVerifyAccess(taskId, currentUser);
        return taskRepository.findByParentTask(parent)
                .stream().map(this::mapTaskResponse).toList();
    }

    // ─── Attachments ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AttachmentResponse addAttachment(UUID taskId, AddAttachmentByUrlRequest request, User currentUser) {
        Task task = findTaskAndVerifyAccess(taskId, currentUser);

        String fileUrl = normalizeHttpUrl(request.fileUrl());
        String fileName = resolveFileName(request.fileName(), fileUrl);
        String contentType = resolveContentType(request.contentType());
        long fileSize = request.fileSize() != null ? request.fileSize() : 0L;

        Attachment attachment = Attachment.builder()
                .fileName(fileName)
                .fileUrl(fileUrl)
                .contentType(contentType)
                .fileSize(fileSize)
                .task(task)
                .uploader(currentUser)
                .build();

        attachment = attachmentRepository.save(attachment);
        return mapAttachmentResponse(attachment);
    }

    @Override
    @Transactional
    public void deleteAttachment(UUID taskId, UUID attachmentId, User currentUser) {
        Task task = findTaskAndVerifyAccess(taskId, currentUser);
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));

        if (!attachment.getTask().getId().equals(task.getId())) {
            throw new BadRequestException("Attachment does not belong to this task");
        }
        if (!attachment.getUploader().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only delete your own attachments");
        }
        attachmentRepository.delete(attachment);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Task buildTask(CreateTaskRequest request, User currentUser, Project project) {
        Task task = Task.builder()
                .title(request.title())
                .description(request.description())
                .priority(request.priority() != null ? request.priority() : Priority.MEDIUM)
                .dueDate(request.dueDate())
                .project(project)
                .creator(currentUser)
                .build();

        if (request.assigneeId() != null) {
            User assignee = userRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assignee not found"));
            task.setAssignee(assignee);
        }

        if (request.parentTaskId() != null) {
            Task parent = taskRepository.findById(request.parentTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent task not found"));
            task.setParentTask(parent);
        }

        return task;
    }

    private Task findTaskAndVerifyAccess(UUID taskId, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        boolean isCreator = task.getCreator().getId().equals(user.getId());
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
        boolean isProjectMember = task.getProject() != null &&
                teamMemberRepository.existsByTeamAndUser(task.getProject().getTeam(), user);

        if (!isCreator && !isAssignee && !isProjectMember) {
            throw new AccessDeniedException("You do not have access to this task");
        }
        return task;
    }

    private void notifyAssignment(Task task, User assigner) {
        User assignee = task.getAssignee();
        notificationService.send(
                assignee,
                NotificationType.TASK_ASSIGNED,
                assigner.getFullName() + " assigned you task: " + task.getTitle(),
                task.getId(),
                "TASK"
        );
        emailService.sendTaskAssignedEmail(
                assignee.getEmail(),
                assignee.getFullName(),
                task.getTitle(),
                assigner.getFullName(),
                task.getProject() != null ? task.getProject().getName() : null
        );
    }

    TaskResponse mapTaskResponse(Task task) {
        long subtaskCount = taskRepository.findByParentTask(task).size();
        long commentCount = task.getComments() != null ? task.getComments().size() : 0;

        List<AttachmentResponse> attachments = attachmentRepository.findByTask(task)
                .stream().map(this::mapAttachmentResponse).toList();

        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getProject() != null ? task.getProject().getId() : null,
                task.getProject() != null ? task.getProject().getName() : null,
                mapUserResponse(task.getCreator()),
                task.getAssignee() != null ? mapUserResponse(task.getAssignee()) : null,
                task.getParentTask() != null ? task.getParentTask().getId() : null,
                subtaskCount,
                commentCount,
                attachments,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private AttachmentResponse mapAttachmentResponse(Attachment a) {
        return new AttachmentResponse(
                a.getId(),
                a.getFileName(),
                a.getFileUrl(),
                a.getContentType(),
                a.getFileSize(),
                mapUserResponse(a.getUploader()),
                a.getUploadedAt()
        );
    }

    private String normalizeHttpUrl(String rawUrl) {
        try {
            java.net.URI uri = new java.net.URI(rawUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new BadRequestException("File URL must be a valid http or https URL");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BadRequestException("File URL must be a valid URL");
            }
            return uri.toString();
        } catch (java.net.URISyntaxException e) {
            throw new BadRequestException("File URL must be a valid URL");
        }
    }

    private String resolveFileName(String providedFileName, String fileUrl) {
        if (providedFileName != null && !providedFileName.isBlank()) {
            return providedFileName.trim();
        }

        String path = java.net.URI.create(fileUrl).getPath();
        if (path != null && !path.isBlank()) {
            String candidate = path.substring(path.lastIndexOf('/') + 1).trim();
            if (!candidate.isBlank()) {
                return candidate.length() > 255 ? candidate.substring(0, 255) : candidate;
            }
        }

        return "attachment";
    }

    private String resolveContentType(String providedContentType) {
        if (providedContentType == null || providedContentType.isBlank()) {
            return "application/octet-stream";
        }
        return providedContentType.trim();
    }

    private <T extends Enum<T>> T parseEnum(Class<T> cls, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(cls, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid value: " + value);
        }
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast()
        );
    }
}
