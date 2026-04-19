package com.taskflow.controller;

import com.taskflow.dto.request.AuthRequests.*;
import com.taskflow.dto.response.ApiResponse;
import com.taskflow.dto.response.Responses.AuthResponse;
import com.taskflow.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, Login, Logout, OAuth2, Password management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(
                "Register successful",
                authService.register(request)
        );
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(
                "Login successful",
                authService.login(request)
        );
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate refresh token")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success("Logout successful");
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ApiResponse<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(
                "Token refreshed successfully",
                authService.refreshToken(request)
        );
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset email")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.success("If that email exists, a reset link has been sent");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using token from email")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success("Password reset successfully");
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify email using token sent after registration")
    public ApiResponse<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ApiResponse.success("Email verified successfully");
    }

    @GetMapping("/oauth2/callback/google")
    @Operation(summary = "Google OAuth2 callback (handled by Spring Security OAuth2)")
    public ApiResponse<Void> googleCallback(@RequestParam String code) {
        return ApiResponse.success("OAuth2 flow is handled via Spring Security redirect");
    }

    @PostMapping("/social-login")
    @Operation(summary = "Social login — check email, create user if new, return token")
    public ApiResponse<AuthResponse> socialLogin(
            @RequestBody SocialLoginRequest request) {
        return ApiResponse.success(
                "Social login successful",
                authService.socialLogin(request)
        );
    }

    @PostMapping("/social-register")
    @Operation(summary = "Sync OAuth user from better-auth")
    public ApiResponse<Void> socialRegister(
            @RequestBody SocialRegisterRequest request) {
        authService.socialRegister(request);
        return ApiResponse.success("User synced");
    }
}