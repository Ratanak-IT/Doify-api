package com.taskflow.service.impl;

import com.taskflow.domain.RefreshToken;
import com.taskflow.domain.User;
import com.taskflow.dto.request.AuthRequests.*;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.exception.BadRequestException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.RefreshTokenRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.JwtService;
import com.taskflow.service.AuthService;
import com.taskflow.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${app.jwt.refresh-token-expiration-days}")
    private long refreshTokenExpirationDays;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email is already registered");
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new BadRequestException("Username is already taken");
        }

        String verificationToken = UUID.randomUUID().toString();

        User user = User.builder()
                .fullName(request.fullName())
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .gender(request.gender())
                .emailVerificationToken(verificationToken)
                .isVerified(false)
                .build();

        user = userRepository.save(user);

        emailService.sendVerificationEmail(
                user.getEmail(),
                user.getFullName(),
                verificationToken
        );

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = saveRefreshToken(user);

        // ✅ now Java knows verificationToken
        return buildAuthResponse(accessToken, refreshToken, user, verificationToken);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = saveRefreshToken(user);

        emailService.sendLoginNotificationEmail(
                user.getEmail(),
                user.getFullName()
        );

        return buildAuthResponse(accessToken, refreshToken, user, null);
    }

    @Override
    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenRepository.findByToken(refreshTokenStr)
                .ifPresent(refreshTokenRepository::delete);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            throw new BadRequestException("Refresh token has expired, please log in again");
        }

        User user = stored.getUser();
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = saveRefreshToken(user);

        return buildAuthResponse(newAccessToken, newRefreshToken, user, null);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setPasswordResetToken(token);
            user.setPasswordResetTokenExpiry(Instant.now().plusSeconds(3600));
            userRepository.save(user);

            emailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getFullName(),
                    token
            );
        });

        // Always return success to avoid email enumeration
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.token())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (user.getPasswordResetTokenExpiry() == null
                || user.getPasswordResetTokenExpiry().isBefore(Instant.now())) {
            throw new BadRequestException("Reset token has expired");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid verification token"));

        user.setVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public AuthResponse googleSignIn(String idToken) {
        throw new BadRequestException("Google Sign-In requires frontend OAuth2 flow with redirect URI");
    }

    private String saveRefreshToken(User user) {
        String tokenValue = jwtService.generateRefreshToken(user);
        Instant expiry = Instant.now().plusSeconds(refreshTokenExpirationDays * 24 * 60 * 60);

        RefreshToken token = refreshTokenRepository.findByUser(user)
                .map(existing -> {
                    existing.setToken(tokenValue);
                    existing.setExpiresAt(expiry);
                    return existing;
                })
                .orElseGet(() -> RefreshToken.builder()
                        .user(user)
                        .token(tokenValue)
                        .expiresAt(expiry)
                        .build()
                );

        refreshTokenRepository.save(token);
        return tokenValue;
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user, String verificationToken) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                mapUserResponse(user),
                verificationToken
        );
    }

    public static UserResponse mapUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getProfileUsername(),
                user.getEmail(),
                user.getProfilePhoto(),
                user.getGender(),
                user.isVerified(),
                user.getCreatedAt()
        );
    }


    @Override
    @Transactional
    public AuthResponse socialLogin(SocialLoginRequest request) {

        // check email — បើមានហើយ login, បើអត់ register ថ្មី
        User user = userRepository.findByEmail(request.email())
                .orElseGet(() -> {
                    // generate unique username ពី email prefix
                    String base = request.email().split("@")[0]
                            .replaceAll("[^a-zA-Z0-9_]", "");
                    String username = base;
                    int i = 1;
                    while (userRepository.existsByUsername(username)) {
                        username = base + i++;
                    }

                    return userRepository.save(
                            User.builder()
                                    .fullName(request.name() != null ? request.name() : base)
                                    .username(username)
                                    .email(request.email())
                                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                                    .profilePhoto(request.avatar())
                                    .isVerified(true)   // OAuth user verified ហើយ
                                    .build()
                    );
                });

        // update profilePhoto បើ user មានហើយ តែ photo នៅ null
        if (request.avatar() != null && user.getProfilePhoto() == null) {
            user.setProfilePhoto(request.avatar());
            userRepository.save(user);
        }

        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = saveRefreshToken(user);

        return buildAuthResponse(accessToken, refreshToken, user, null);
    }

    @Override
    @Transactional
    public void socialRegister(SocialRegisterRequest request) {
        // better-auth ហៅ endpoint នេះ — sync user តែប៉ុណ្ណោះ
        // មិន throw error បើ email មានហើយ
        if (request.email() == null) return;

        if (!userRepository.existsByEmail(request.email())) {
            String base = request.email().split("@")[0]
                    .replaceAll("[^a-zA-Z0-9_]", "");
            String username = base;
            int i = 1;
            while (userRepository.existsByUsername(username)) {
                username = base + i++;
            }

            userRepository.save(
                    User.builder()
                            .fullName(request.name() != null ? request.name() : base)
                            .username(username)
                            .email(request.email())
                            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .profilePhoto(request.avatar())
                            .isVerified(true)
                            .build()
            );
        }
    }
}