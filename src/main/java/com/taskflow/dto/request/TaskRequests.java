package com.taskflow.dto.request;

import com.taskflow.domain.enums.Priority;
import com.taskflow.domain.enums.TaskStatus;
import com.taskflow.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public class TaskRequests {

    public record CreateTaskRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 200, message = "Title must not exceed 200 characters")
            String title,

            @Size(max = 5000, message = "Description must not exceed 5000 characters")
            String description,
            Priority priority,
            LocalDate dueDate,
            UUID projectId,
            UUID assigneeId,
            UUID parentTaskId
    ) {}

    public record UpdateTaskRequest(
            @Size(max = 200, message = "Title must not exceed 200 characters")
            String title,

            @Size(max = 5000, message = "Description must not exceed 5000 characters")
            String description,
            Priority priority,
            TaskStatus status,
            LocalDate dueDate,
            UUID assigneeId
    ) {}

    public record AddAttachmentByUrlRequest(
            @NotBlank(message = "File URL is required")
            @Pattern(regexp = ValidationPatterns.URL, message = "File URL must be a valid http or https URL")
            String fileUrl,

            @Size(max = 255, message = "File name must not exceed 255 characters")
            String fileName,

            @Size(max = 100, message = "Content type must not exceed 100 characters")
            String contentType,

            @PositiveOrZero(message = "File size must be 0 or greater")
            Long fileSize
    ) {}

    public record TaskFilterRequest(
            String search,
            TaskStatus status,
            Priority priority,
            UUID assigneeId,
            @PositiveOrZero(message = "Page must be 0 or greater")
            int page,
            @PositiveOrZero(message = "Size must be 0 or greater")
            int size
    ) {}
}
