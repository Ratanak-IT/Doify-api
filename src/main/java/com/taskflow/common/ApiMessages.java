package com.taskflow.common;

public final class ApiMessages {

    private ApiMessages() {}

    public static final String VALIDATION_FAILED = "Validation failed";
    public static final String REQUEST_BODY_REQUIRED = "Request body is required";
    public static final String REQUEST_PAYLOAD_INVALID = "Request payload is invalid";
    public static final String REQUIRED_PARAMETER_MISSING = "Required request parameter is missing";
    public static final String INVALID_CREDENTIALS = "Invalid email or password";
    public static final String FILE_TOO_LARGE = "File size exceeds maximum allowed limit";
    public static final String UNEXPECTED_ERROR = "An unexpected error occurred";
    public static final String DATA_CONFLICT = "Request could not be completed because of a data conflict";

    public static String updated(String resourceName) {
        return resourceName + " updated successfully";
    }

    public static String created(String resourceName) {
        return resourceName + " created successfully";
    }

    public static String deleted(String resourceName) {
        return resourceName + " deleted successfully";
    }

    public static String fetched(String resourceName) {
        return resourceName + " fetched successfully";
    }
}
