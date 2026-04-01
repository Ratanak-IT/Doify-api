package com.taskflow.controller;

import com.taskflow.domain.User;
import com.taskflow.dto.request.TaskRequests.*;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Task Management", description = "Personal and project task operations")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;


    @PostMapping("/personal")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a personal task (not linked to any project)")
    public TaskResponse createPersonalTask(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.createPersonalTask(request, currentUser);
    }

    @GetMapping("/personal")
    @Operation(summary = "Get all personal tasks with search and filter")
    public PageResponse<TaskResponse> getPersonalTasks(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.getPersonalTasks(currentUser, search, status, priority, page, size);
    }


    @PostMapping("/project/{projectId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task inside a project")
    public TaskResponse createProjectTask(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.createProjectTask(projectId, request, currentUser);
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get all tasks for a project with search and filter")
    public PageResponse<TaskResponse> getProjectTasks(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.getProjectTasks(projectId, currentUser, search, status, priority, assigneeId, page, size);
    }


    @GetMapping("/{taskId}")
    @Operation(summary = "Get a task by ID")
    public TaskResponse getTask(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.getTask(taskId, currentUser);
    }

    @PutMapping("/{taskId}")
    @Operation(summary = "Update a task (title, description, status, priority, due date, assignee)")
    public TaskResponse updateTask(
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.updateTask(taskId, request, currentUser);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task")
    public void deleteTask(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal User currentUser
    ) {
        taskService.deleteTask(taskId, currentUser);
    }

    @GetMapping("/{taskId}/subtasks")
    @Operation(summary = "Get all subtasks of a task")
    public List<TaskResponse> getSubtasks(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.getSubtasks(taskId, currentUser);
    }


    @PostMapping("/{taskId}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach an existing file URL to a task")
    public AttachmentResponse addAttachment(
            @PathVariable UUID taskId,
            @Valid @RequestBody AddAttachmentByUrlRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return taskService.addAttachment(taskId, request, currentUser);
    }

    @DeleteMapping("/{taskId}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an attachment from a task")
    public void deleteAttachment(
            @PathVariable UUID taskId,
            @PathVariable UUID attachmentId,
            @AuthenticationPrincipal User currentUser
    ) {
        taskService.deleteAttachment(taskId, attachmentId, currentUser);
    }
}
