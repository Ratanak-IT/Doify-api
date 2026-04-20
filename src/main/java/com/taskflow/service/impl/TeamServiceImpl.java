package com.taskflow.service.impl;

import com.taskflow.domain.*;
import com.taskflow.domain.enums.InvitationStatus;
import com.taskflow.domain.enums.NotificationType;
import com.taskflow.domain.enums.Role;
import com.taskflow.dto.request.TeamRequests.*;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.exception.AccessDeniedException;
import com.taskflow.exception.BadRequestException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.*;
import com.taskflow.service.EmailService;
import com.taskflow.service.NotificationService;
import com.taskflow.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.taskflow.service.impl.AuthServiceImpl.mapUserResponse;

@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request, User currentUser) {
        Team team = Team.builder()
                .name(request.name())
                .description(request.description())
                .owner(currentUser)
                .build();
        team = teamRepository.save(team);

        // Add owner as member with OWNER role
        TeamMember ownerMember = TeamMember.builder()
                .team(team)
                .user(currentUser)
                .role(Role.OWNER)
                .build();
        teamMemberRepository.save(ownerMember);

        return mapTeamResponse(team, 1L);
    }

    @Override
    public TeamResponse getTeam(UUID teamId, User currentUser) {
        Team team = findTeamAndVerifyAccess(teamId, currentUser);
        long memberCount = teamMemberRepository.countByTeam(team);
        return mapTeamResponse(team, memberCount);
    }

    @Override
    public PageResponse<TeamResponse> getMyTeams(User currentUser, int page, int size) {
        Page<Team> teams = teamRepository.findAllByMember(currentUser, PageRequest.of(page, size));
        Page<TeamResponse> mapped = teams.map(t -> {
            long count = teamMemberRepository.countByTeam(t);
            return mapTeamResponse(t, count);
        });
        return toPageResponse(mapped);
    }

    @Override
    @Transactional
    public TeamResponse updateTeam(UUID teamId, UpdateTeamRequest request, User currentUser) {
        Team team = findTeamAndVerifyAccess(teamId, currentUser);
        requireRole(team, currentUser, Role.ADMIN);

        if (request.name() != null) team.setName(request.name());
        if (request.description() != null) team.setDescription(request.description());
        team = teamRepository.save(team);

        long memberCount = teamMemberRepository.countByTeam(team);
        return mapTeamResponse(team, memberCount);
    }

    @Override
    @Transactional
    public void acceptInvitationById(UUID invitationId, User currentUser) {

        TeamInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Invitation already processed");
        }

        if (!invitation.getInviteeEmail().equalsIgnoreCase(currentUser.getEmail())) {
            throw new BadRequestException("You are not allowed to accept this invitation");
        }

        // add to team
        TeamMember member = TeamMember.builder()
                .team(invitation.getTeam())
                .user(currentUser)
                .role(invitation.getInvitedRole())
                .build();

        teamMemberRepository.save(member);

        // update invitation
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        // notify inviter
        notificationService.createNotification(
                invitation.getInviter(),
                NotificationType.INVITATION_ACCEPTED,
                currentUser.getFullName() + " accepted your invitation",
                invitation.getId()
        );
        emailService.sendInvitationAcceptedEmail(
                invitation.getInviter().getEmail(),
                invitation.getInviter().getFullName(),
                currentUser.getFullName(),
                invitation.getTeam().getName()
        );
    }

    @Override
    @Transactional
    public void deleteTeam(UUID teamId, User currentUser) {
        Team team = findTeamAndVerifyAccess(teamId, currentUser);
        requireRole(team, currentUser, Role.OWNER);
        teamRepository.delete(team);
    }

    @Override
    @Transactional
    public void inviteMember(UUID teamId, InviteMemberRequest request, User currentUser) {
        Team team = findTeamAndVerifyAccess(teamId, currentUser);
        requireRole(team, currentUser, Role.ADMIN);

        String email = request.email().trim().toLowerCase();

        User invitedUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("No account found with this email"));

        if (teamMemberRepository.existsByTeamAndUser(team, invitedUser)) {
            throw new BadRequestException("User is already a member of this team");
        }

        if (invitationRepository.existsByTeamAndInviteeEmailAndStatus(
                team, email, InvitationStatus.PENDING)) {
            throw new BadRequestException("An invitation is already pending for this email");
        }

        String token = UUID.randomUUID().toString();
        TeamInvitation invitation = TeamInvitation.builder()
                .team(team)
                .inviter(currentUser)
                .inviteeEmail(email)
                .token(token)
                .invitedRole(request.role())
                .expiresAt(Instant.now().plusSeconds(604800))
                .build();

        invitationRepository.save(invitation);

        notificationService.createNotification(
                invitedUser,
                NotificationType.TEAM_INVITATION,
                currentUser.getFullName() + " invited you to join team " + team.getName(),
                invitation.getId()
        );

        emailService.sendTeamInvitationEmail(
                email,
                currentUser.getFullName(),
                team.getName(),
                request.role().name(),
                token
        );
    }

    @Override
    @Transactional
    public void acceptInvitation(String token, User currentUser) {
        TeamInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found or invalid"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Invitation has already been " + invitation.getStatus().name().toLowerCase());
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new BadRequestException("Invitation has expired");
        }
        if (!invitation.getInviteeEmail().equalsIgnoreCase(currentUser.getEmail())) {
            throw new AccessDeniedException("This invitation is for a different email address");
        }

        Team team = invitation.getTeam();
        if (teamMemberRepository.existsByTeamAndUser(team, currentUser)) {
            throw new BadRequestException("You are already a member of this team");
        }

        TeamMember newMember = TeamMember.builder()
                .team(team)
                .user(currentUser)
                .role(invitation.getInvitedRole())
                .build();
        teamMemberRepository.save(newMember);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        // Notify inviter
        notificationService.send(
                invitation.getInviter(),
                NotificationType.INVITATION_ACCEPTED,
                currentUser.getFullName() + " accepted your invitation to join " + team.getName(),
                team.getId(),
                "TEAM"
        );
        emailService.sendInvitationAcceptedEmail(
                invitation.getInviter().getEmail(),
                invitation.getInviter().getFullName(),
                currentUser.getFullName(),
                team.getName()
        );
    }

    @Override
    public PageResponse<TeamMemberResponse> getMembers(UUID teamId, User currentUser, int page, int size) {
        Team team = findTeamAndVerifyAccess(teamId, currentUser);
        var members = teamMemberRepository.findByTeam(team);
        var responses = members.stream().map(m -> new TeamMemberResponse(
                m.getId(),
                mapUserResponse(m.getUser()),
                m.getRole(),
                m.getJoinedAt()
        )).toList();

        int start = Math.min(page * size, responses.size());
        int end = Math.min(start + size, responses.size());
        var pageContent = responses.subList(start, end);

        return new PageResponse<>(
                pageContent, page, size, responses.size(),
                (int) Math.ceil((double) responses.size() / size),
                end >= responses.size()
        );
    }

    @Override
    @Transactional
    public void updateMemberRole(UUID teamId, UUID memberId, UpdateMemberRoleRequest request, User currentUser) {
        Team team = findTeamAndVerifyAccess(teamId, currentUser);
        requireRole(team, currentUser, Role.ADMIN);

        TeamMember member = teamMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (member.getRole() == Role.OWNER) {
            throw new BadRequestException("Cannot change the role of the team owner");
        }
        if (request.role() == Role.OWNER) {
            throw new BadRequestException("Cannot assign OWNER role via this endpoint");
        }

        member.setRole(request.role());
        teamMemberRepository.save(member);

        notificationService.createNotification(
                member.getUser(),
                NotificationType.TEAM_INVITATION,
                "Your role in team " + team.getName() + " has been updated to " + request.role().name(),
                team.getId()
        );
        emailService.sendRoleUpdatedEmail(
                member.getUser().getEmail(),
                member.getUser().getFullName(),
                team.getName(),
                request.role().name()
        );
    }

    @Override
    @Transactional
    public void removeMember(UUID teamId, UUID userId, User currentUser) {
        Team team = findTeamAndVerifyAccess(teamId, currentUser);
        requireRole(team, currentUser, Role.ADMIN);

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        TeamMember member = teamMemberRepository.findByTeamAndUser(team, targetUser)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this team"));

        if (member.getRole() == Role.OWNER) {
            throw new BadRequestException("Cannot remove the team owner");
        }

        teamMemberRepository.delete(member);

        notificationService.createNotification(
                targetUser,
                NotificationType.TEAM_INVITATION,
                "You have been removed from team " + team.getName(),
                team.getId()
        );
        emailService.sendMemberRemovedEmail(
                targetUser.getEmail(),
                targetUser.getFullName(),
                team.getName()
        );
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Team findTeamAndVerifyAccess(UUID teamId, User user) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
        if (!teamMemberRepository.existsByTeamAndUser(team, user)) {
            throw new AccessDeniedException("You are not a member of this team");
        }
        return team;
    }

    private void requireRole(Team team, User user, Role minimumRole) {
        TeamMember member = teamMemberRepository.findByTeamAndUser(team, user)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this team"));

        boolean hasPermission = switch (minimumRole) {
            case OWNER -> member.getRole() == Role.OWNER;
            case ADMIN -> member.getRole() == Role.OWNER || member.getRole() == Role.ADMIN;
            case MEMBER -> true;
        };

        if (!hasPermission) {
            throw new AccessDeniedException("You do not have permission for this action");
        }
    }

    private TeamResponse mapTeamResponse(Team team, long memberCount) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                mapUserResponse(team.getOwner()),
                memberCount,
                team.getCreatedAt()
        );
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast()
        );
    }
}