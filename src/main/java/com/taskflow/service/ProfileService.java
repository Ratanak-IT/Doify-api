package com.taskflow.service;

import com.taskflow.domain.User;
import com.taskflow.dto.request.ProfileRequests.*;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.exception.BadRequestException;
import com.taskflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;

import static com.taskflow.service.impl.AuthServiceImpl.mapUserResponse;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse getProfile(User currentUser) {
        return mapUserResponse(currentUser);
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request, User currentUser) {
        if (request.username() != null
                && !request.username().isBlank()
                && !request.username().equals(currentUser.getUsername())) {

            if (userRepository.existsByUsername(request.username())) {
                throw new BadRequestException("Username is already taken");
            }
            currentUser.setUsername(request.username().trim());
        }

        if (request.email() != null
                && !request.email().isBlank()
                && !request.email().equals(currentUser.getEmail())) {

            if (userRepository.existsByEmail(request.email())) {
                throw new BadRequestException("Email is already registered");
            }
            currentUser.setEmail(request.email().trim());
            currentUser.setVerified(false);
        }

        if (request.fullName() != null && !request.fullName().isBlank()) {
            currentUser.setFullName(request.fullName().trim());
        }

        if (request.gender() != null) {
            currentUser.setGender(request.gender());
        }

        // save profile photo as URL directly
        if (request.profilePhoto() != null) {
            String photoUrl = request.profilePhoto().trim();

            if (photoUrl.isEmpty()) {
                currentUser.setProfilePhoto(null);
            } else {
                validatePhotoUrl(photoUrl);
                currentUser.setProfilePhoto(photoUrl);
            }
        }

        currentUser = userRepository.save(currentUser);
        return mapUserResponse(currentUser);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, User currentUser) {
        if (!passwordEncoder.matches(request.currentPassword(), currentUser.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new BadRequestException("New password must not be empty");
        }

        currentUser.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(currentUser);
    }

    private void validatePhotoUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();

            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new BadRequestException("Profile photo must be a valid http or https URL");
            }

            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BadRequestException("Profile photo must be a valid URL");
            }
        } catch (URISyntaxException e) {
            throw new BadRequestException("Profile photo must be a valid URL");
        }
    }
}