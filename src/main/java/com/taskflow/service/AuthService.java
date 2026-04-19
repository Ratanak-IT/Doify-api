package com.taskflow.service;

import com.taskflow.dto.request.AuthRequests.*;
import com.taskflow.dto.response.Responses.*;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    void logout(String refreshToken);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    void verifyEmail(String token);
    AuthResponse googleSignIn(String code);
    AuthResponse socialLogin(SocialLoginRequest request);
    void socialRegister(SocialRegisterRequest request);
}
