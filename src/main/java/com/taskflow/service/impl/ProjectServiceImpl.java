package com.taskflow.service.impl;

import com.taskflow.domain.Project;
import com.taskflow.domain.Team;
import com.taskflow.domain.TeamMember;
import com.taskflow.domain.User;
import com.taskflow.domain.enums.NotificationType;
import com.taskflow.domain.enums.TaskStatus;
import com.taskflow.dto.request.ProjectRequests.*;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.exception.AccessDeniedException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
import com.taskflow.service.EmailService;
import com.taskflow.service.NotificationService;
import com.taskflow.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.taskflow.service.impl.AuthServiceImpl.mapUserResponse;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Override
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, User currentUser) {
        Team team = null;

        if (request.teamId() != null) {
            team = teamRepository.findById(request.teamId())
                    .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
            if (!teamMemberRepository.existsByTeamAndUser(team, currentUser)) {
                throw new AccessDeniedException("You are not a member of this team");
            }
        }

        Project project = Project.builder()
                .name(request.name())
                .description(request.description())
                .startDate(request.startDate())
                .dueDate(request.dueDate())
                .color(request.color())
                .team(team)
                .creator(currentUser)
                .build();

        project = projectRepository.save(project);

        if (team != null) {
            notifyTeamMembersProjectCreated(project, currentUser);
        }

        return mapProjectResponse(project, 0, 0);
    }

    @Override
    public ProjectResponse getProject(UUID projectId, User currentUser) {
        Project project = findProjectAndVerifyAccess(projectId, currentUser);
        return buildProjectResponse(project);
    }

    @Override
    public PageResponse<ProjectResponse> getProjectsByTeam(UUID teamId, User currentUser, int page, int size) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        if (!teamMemberRepository.existsByTeamAndUser(team, currentUser)) {
            throw new AccessDeniedException("You are not a member of this team");
        }

        Page<Project> projects = projectRepository.findByTeam(team, PageRequest.of(page, size));
        return toPageResponse(projects.map(this::buildProjectResponse));
    }

    @Override
    public PageResponse<ProjectResponse> getMyProjects(User currentUser, int page, int size) {
        Page<Project> projects = projectRepository.findAllAccessibleByUser(currentUser, PageRequest.of(page, size));
        return toPageResponse(projects.map(this::buildProjectResponse));
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, User currentUser) {
        Project project = findProjectAndVerifyAccess(projectId, currentUser);

        if (request.name() != null)        project.setName(request.name());
        if (request.description() != null) project.setDescription(request.description());
        if (request.startDate() != null)   project.setStartDate(request.startDate());
        if (request.dueDate() != null)     project.setDueDate(request.dueDate());
        if (request.color() != null)       project.setColor(request.color());

        project = projectRepository.save(project);

        notifyTeamMembersProjectUpdated(project, currentUser);

        return buildProjectResponse(project);
    }

    @Override
    @Transactional
    public void deleteProject(UUID projectId, User currentUser) {
        Project project = findProjectAndVerifyAccess(projectId, currentUser);
        projectRepository.delete(project);
    }

    private void notifyTeamMembersProjectCreated(Project project, User creator) {
        List<TeamMember> members = teamMemberRepository.findByTeam(project.getTeam());
        for (TeamMember member : members) {
            User recipient = member.getUser();
            if (recipient.getId().equals(creator.getId())) continue;

            notificationService.send(
                    recipient,
                    NotificationType.PROJECT_UPDATED,
                    creator.getFullName() + " created a new project: " + project.getName(),
                    project.getId(),
                    "PROJECT"
            );
            emailService.sendProjectCreatedEmail(
                    recipient.getEmail(),
                    recipient.getFullName(),
                    creator.getFullName(),
                    project.getName(),
                    project.getTeam().getName()
            );
        }
    }

    private void notifyTeamMembersProjectUpdated(Project project, User updater) {
        List<TeamMember> members = teamMemberRepository.findByTeam(project.getTeam());

        for (TeamMember member : members) {
            User recipient = member.getUser();

            // Don't notify the person who made the change
            if (recipient.getId().equals(updater.getId())) continue;

            notificationService.send(
                    recipient,
                    NotificationType.PROJECT_UPDATED,
                    updater.getFullName() + " updated project: " + project.getName(),
                    project.getId(),
                    "PROJECT"
            );

            emailService.sendProjectUpdatedEmail(
                    recipient.getEmail(),
                    recipient.getFullName(),
                    updater.getFullName(),
                    project.getName()
            );
        }
    }

    private Project findProjectAndVerifyAccess(UUID projectId, User user) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (project.getTeam() == null) {
            if (!project.getCreator().getId().equals(user.getId())) {
                throw new AccessDeniedException("You do not have access to this project");
            }
            return project;
        }
        if (!teamMemberRepository.existsByTeamAndUser(project.getTeam(), user)) {
            throw new AccessDeniedException("You do not have access to this project");
        }

        return project;
    }

    private ProjectResponse buildProjectResponse(Project project) {
        long total     = taskRepository.countByProject(project);
        long completed = taskRepository.countByProjectAndStatus(project, TaskStatus.DONE);
        return mapProjectResponse(project, total, completed);
    }
   // update
    private ProjectResponse mapProjectResponse(Project project, long total, long completed) {
        int progress = total == 0 ? 0 : (int) ((completed * 100) / total);
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStartDate(),
                project.getDueDate(),
                project.getColor(),
                project.getTeam() != null ? project.getTeam().getId() : null,
                project.getTeam() != null ? project.getTeam().getName() : null,
                mapUserResponse(project.getCreator()),
                total,
                completed,
                progress,
                project.getCreatedAt()
        );
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast()
        );
    }
}