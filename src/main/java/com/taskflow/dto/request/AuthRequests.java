package com.taskflow.dto.request;

import com.taskflow.domain.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AuthRequests {

    public record RegisterRequest(

            @NotBlank(message = "Full name is required")
            @Size(max = 100, message = "Full name must not exceed 100 characters")
            String fullName,

            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
            String username,

            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password,

            @NotNull(message = "Gender is required")
            Gender gender

    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record ForgotPasswordRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "Token is required")
            String token,

            @NotBlank(message = "New password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String newPassword
    ) {}

    public record RefreshTokenRequest(
            @NotBlank(message = "Refresh token is required")
            String refreshToken
    ) {}

    public record SocialLoginRequest(
            String email,
            String name,
            String avatar,
            String provider
    ) {}

    public record SocialRegisterRequest(
            String id,
            String name,
            String email,
            String avatar
    ) {}
}
