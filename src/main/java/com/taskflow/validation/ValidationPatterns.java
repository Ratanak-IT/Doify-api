package com.taskflow.validation;

public final class ValidationPatterns {
    private ValidationPatterns() {}
    public static final String HEX_COLOR = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$";
    public static final String USERNAME = "^[a-zA-Z0-9._-]{3,50}$";
    public static final String URL = "^(https?://).+$";
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";
}
