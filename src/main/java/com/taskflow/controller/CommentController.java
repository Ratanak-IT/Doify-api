package com.taskflow.controller;

import com.taskflow.domain.User;
import com.taskflow.dto.request.CommentRequest.*;
import com.taskflow.dto.response.ApiSuccessResponse;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Comment on tasks, mention users with @username")
@SecurityRequirement(name = "bearerAuth")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a comment to a task. Use @username to mention teammates.")
    public ApiSuccessResponse<CommentResponse> addComment(
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ApiSuccessResponse.of(
                HttpStatus.CREATED,
                "Comment added successfully",
                commentService.addComment(taskId, request, currentUser)
        );
    }

    @GetMapping
    @Operation(summary = "Get all comments for a task")
    public ApiSuccessResponse<PageResponse<CommentResponse>> getComments(
            @PathVariable UUID taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser
    ) {
        return ApiSuccessResponse.ok(
                "Comments fetched successfully",
                commentService.getComments(taskId, currentUser, page, size)
        );
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "Update a comment (author only)")
    public ApiSuccessResponse<CommentResponse> updateComment(
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ApiSuccessResponse.ok(
                "Comment updated successfully",
                commentService.updateComment(taskId, commentId, request, currentUser)
        );
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete a comment (author only)")
    public ApiSuccessResponse<Void> deleteComment(
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal User currentUser
    ) {
        commentService.deleteComment(taskId, commentId, currentUser);
        return ApiSuccessResponse.ok("Comment deleted successfully");
    }
}