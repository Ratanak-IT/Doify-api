package com.taskflow.dto.request;

import com.taskflow.domain.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProfileRequests {

    public record UpdateProfileRequest(

            @Size(max = 100, message = "Full name must not exceed 100 characters")
            String fullName,

            @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
            String username,

            @Email(message = "Email must be valid")
            String email,

            String profilePhoto,

            Gender gender
    ) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required")
            String currentPassword,

            @NotBlank(message = "New password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String newPassword
    ) {}
}
