package com.taskflow.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @Column
    private String password;

    @Column(length = 255)
    private String profilePhoto;

    @Column(nullable = false)
    @Builder.Default
    private boolean isVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean isEnabled = true;

    @Column
    private String googleId;

    @Column
    private String passwordResetToken;

    @Column
    private Instant passwordResetTokenExpiry;

    @Column
    private String emailVerificationToken;

    @Column
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column
    @Builder.Default
    private Instant updatedAt = Instant.now();

    // Relations
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Team> ownedTeams = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TeamMember> teamMemberships = new ArrayList<>();

    @OneToMany(mappedBy = "assignee", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Task> assignedTasks = new ArrayList<>();

    @OneToMany(mappedBy = "creator", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Task> createdTasks = new ArrayList<>();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return isEnabled; }

    @Override
    public String getUsername() {
        return email;
    }
    public String getProfileUsername() {
        return this.username;
    }
}
