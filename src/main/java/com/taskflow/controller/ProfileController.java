package com.taskflow.controller;

import com.taskflow.domain.User;
import com.taskflow.dto.request.ProfileRequests.*;
import com.taskflow.dto.response.ApiResponse;
import com.taskflow.dto.response.Responses.UserResponse;
import com.taskflow.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "View and update user profile")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    @Operation(summary = "Get current user profile")
    public ApiResponse<UserResponse> getProfile(@AuthenticationPrincipal User currentUser) {
        return ApiResponse.success(
                "Profile fetched successfully",
                profileService.getProfile(currentUser)
        );
    }

    @PutMapping
    @Operation(summary = "Update profile (name, username, email, profile photo URL)")
    public ApiResponse<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ApiResponse.success(
                "Profile updated successfully",
                profileService.updateProfile(request, currentUser)
        );
    }

    @PatchMapping("/password")
    @Operation(summary = "Change password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        profileService.changePassword(request, currentUser);
        return ApiResponse.success("Password changed successfully", null);
    }
}