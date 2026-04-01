package com.taskflow.exception;

import com.taskflow.common.ApiMessages;
import com.taskflow.dto.response.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ValidationError(String field, String message) {}

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, ApiMessages.INVALID_CREDENTIALS, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toValidationError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, ApiMessages.VALIDATION_FAILED, fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<ValidationError> errors = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ValidationError(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage() == null ? ApiMessages.VALIDATION_FAILED : error.getDefaultMessage()
                        )))
                .toList();

        return build(HttpStatus.BAD_REQUEST, ApiMessages.VALIDATION_FAILED, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ValidationError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ValidationError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        return build(HttpStatus.BAD_REQUEST, ApiMessages.VALIDATION_FAILED, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        List<ValidationError> errors = List.of(new ValidationError(ex.getParameterName(), ex.getMessage()));
        return build(HttpStatus.BAD_REQUEST, ApiMessages.REQUIRED_PARAMETER_MISSING, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {

        Throwable root = ex.getMostSpecificCause();

        if (root instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife) {

            String field = "request";

            if (ife.getPath() != null && !ife.getPath().isEmpty()) {
                field = ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            }

            // 👇 check if enum error
            if (ife.getTargetType() != null && ife.getTargetType().isEnum()) {

                Object[] values = ife.getTargetType().getEnumConstants();

                String allowed = java.util.Arrays.stream(values)
                        .map(Object::toString)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");

                return build(
                        HttpStatus.BAD_REQUEST,
                        ApiMessages.REQUEST_PAYLOAD_INVALID,
                        List.of(new ValidationError(
                                field,
                                field.substring(0, 1).toUpperCase() + field.substring(1)
                                        + " must be one of: " + allowed
                        ))
                );
            }
        }

        String message = ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null
                ? ex.getMostSpecificCause().getMessage()
                : ApiMessages.REQUEST_PAYLOAD_INVALID;

        if (message.contains("Required request body is missing")) {
            return build(HttpStatus.BAD_REQUEST, ApiMessages.REQUEST_BODY_REQUIRED, null);
        }

        return build(
                HttpStatus.BAD_REQUEST,
                ApiMessages.REQUEST_PAYLOAD_INVALID,
                List.of(new ValidationError("request", sanitize(message)))
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ApiMessages.DATA_CONFLICT, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, ApiMessages.FILE_TOO_LARGE, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ApiMessages.UNEXPECTED_ERROR, null);
    }

    private ValidationError toValidationError(FieldError fieldError) {
        return new ValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, Object errors) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status, message, errors));
    }

    private String sanitize(String message) {
        return message.length() > 300 ? message.substring(0, 300) + "..." : message;
    }
}
