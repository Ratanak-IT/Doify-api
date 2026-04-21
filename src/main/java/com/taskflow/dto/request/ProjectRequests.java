package com.taskflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public class ProjectRequests {

    public record CreateProjectRequest(
            @NotBlank(message = "Project name is required")
            @Size(max = 150, message = "Project name must not exceed 150 characters")
            String name,

            String description,
            LocalDate startDate,
            LocalDate dueDate,

            @Size(max = 7, message = "Color must be a valid hex code e.g. #FF5733")
            String color,


            UUID teamId
    ) {}

    public record UpdateProjectRequest(
            @Size(max = 150)
            String name,

            String description,
            LocalDate startDate,
            LocalDate dueDate,

            @Size(max = 7)
            String color
    ) {}
}
