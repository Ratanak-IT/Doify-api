package com.taskflow.service;

import com.taskflow.domain.User;
import com.taskflow.dto.request.TaskRequests.*;
import com.taskflow.dto.response.Responses.*;
import java.util.List;
import java.util.UUID;

public interface TaskService {
    // Personal tasks
    TaskResponse createPersonalTask(CreateTaskRequest request, User currentUser);
    PageResponse<TaskResponse> getPersonalTasks(User currentUser, String search, String status,
                                                String priority, int page, int size);

    // Project tasks
    TaskResponse createProjectTask(UUID projectId, CreateTaskRequest request, User currentUser);
    PageResponse<TaskResponse> getProjectTasks(UUID projectId, User currentUser, String search,
                                               String status, String priority, UUID assigneeId,
                                               int page, int size);

    // Common
    TaskResponse getTask(UUID taskId, User currentUser);
    TaskResponse updateTask(UUID taskId, UpdateTaskRequest request, User currentUser);
    void deleteTask(UUID taskId, User currentUser);
    List<TaskResponse> getSubtasks(UUID taskId, User currentUser);

    // Attachments
    AttachmentResponse addAttachment(UUID taskId, AddAttachmentByUrlRequest request, User currentUser);
    void deleteAttachment(UUID taskId, UUID attachmentId, User currentUser);
}
