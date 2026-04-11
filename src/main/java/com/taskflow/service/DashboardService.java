package com.taskflow.service;

import com.taskflow.domain.Project;
import com.taskflow.domain.Task;
import com.taskflow.domain.Team;
import com.taskflow.domain.User;
import org.springframework.data.domain.PageRequest;
import com.taskflow.domain.enums.TaskStatus;
import com.taskflow.dto.response.Responses.*;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public DashboardResponse getDashboard(User currentUser) {
        LocalDate today = LocalDate.now();
        List<TaskStatus> doneOrCancelled = List.of(TaskStatus.DONE, TaskStatus.CANCELLED);

        // ── Task counts (ALL visible: personal + assigned + team) ────────────
        long totalTasks     = taskRepository.countAllVisibleTasks(currentUser);
        long completedTasks = taskRepository.countAllVisibleTasksByStatus(currentUser, TaskStatus.DONE);
        long pendingTasks   = taskRepository.countAllVisibleTasksByStatus(currentUser, TaskStatus.TODO)
                + taskRepository.countAllVisibleTasksByStatus(currentUser, TaskStatus.IN_PROGRESS);
        long overdueTasks   = taskRepository.countAllVisibleOverdueTasks(currentUser, today, doneOrCancelled);

        // myTasks = tasks personally assigned to me
        long myTasks = taskRepository.countByAssignee(currentUser);

        // ── Project count (accessible via team membership) ───────────────────
        long totalProjects = projectRepository
                .findAllAccessibleByUser(currentUser, PageRequest.of(0, 1))
                .getTotalElements();

        // ── Team members (across all teams user belongs to) ──────────────────
        List<Team> myTeams = teamRepository.findAllByMember(currentUser);
        long totalTeamMembers = myTeams.stream()
                .mapToLong(teamMemberRepository::countByTeam)
                .sum();

        // ── Upcoming due dates (next 7 days, ALL visible tasks) ──────────────
        List<UpcomingTaskResponse> upcomingDueDates = taskRepository
                .findUpcomingTasks(currentUser, today, today.plusDays(7), TaskStatus.DONE)
                .stream()
                .map(t -> new UpcomingTaskResponse(
                        t.getId(),
                        t.getTitle(),
                        t.getDueDate(),
                        t.getPriority(),
                        t.getProject() != null ? t.getProject().getName() : "Personal"
                ))
                .toList();

        // ── Recent activities (last 5 assigned tasks) ────────────────────────
        List<ActivityResponse> recentActivities = taskRepository
                .findByAssignee(currentUser, PageRequest.of(0, 5))
                .getContent()
                .stream()
                .map(t -> new ActivityResponse(
                        "TASK_UPDATED",
                        "Task \"" + t.getTitle() + "\" was updated",
                        t.getUpdatedAt()
                ))
                .toList();

        // ── Project progress summary ─────────────────────────────────────────
        List<ProjectProgressResponse> projectProgressSummary = projectRepository
                .findAllAccessibleByUser(currentUser, PageRequest.of(0, 10))
                .getContent()
                .stream()
                .map(p -> {
                    long total = taskRepository.countByProject(p);
                    long done  = taskRepository.countByProjectAndStatus(p, TaskStatus.DONE);
                    int pct    = total == 0 ? 0 : (int) ((done * 100) / total);
                    return new ProjectProgressResponse(p.getId(), p.getName(), total, done, pct);
                })
                .toList();

        return new DashboardResponse(
                totalProjects,
                totalTasks,
                myTasks,
                completedTasks,
                pendingTasks,
                overdueTasks,
                totalTeamMembers,
                upcomingDueDates,
                recentActivities,
                projectProgressSummary
        );
    }
}