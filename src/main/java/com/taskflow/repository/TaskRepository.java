package com.taskflow.repository;

import com.taskflow.domain.Project;
import com.taskflow.domain.Task;
import com.taskflow.domain.User;
import com.taskflow.domain.enums.Priority;
import com.taskflow.domain.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    // Personal tasks (no project)
    Page<Task> findByCreatorAndProjectIsNull(User creator, Pageable pageable);

    // Project tasks
    Page<Task> findByProject(Project project, Pageable pageable);

    // Assigned tasks
    Page<Task> findByAssignee(User assignee, Pageable pageable);

    // Subtasks
    List<Task> findByParentTask(Task parentTask);

    // Search & filter
    @Query("""
            SELECT t FROM Task t
            WHERE t.creator = :user
              AND t.project IS NULL
              AND (:search IS NULL OR :search = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
            """)
    Page<Task> searchPersonalTasks(
            @Param("user") User user,
            @Param("search") String search,
            @Param("status") TaskStatus status,
            @Param("priority") Priority priority,
            Pageable pageable
    );

    @Query("""
            SELECT t FROM Task t
            WHERE t.project = :project
              AND (:search IS NULL OR :search = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)
            """)
    Page<Task> searchProjectTasks(
            @Param("project") Project project,
            @Param("search") String search,
            @Param("status") TaskStatus status,
            @Param("priority") Priority priority,
            @Param("assigneeId") UUID assigneeId,
            Pageable pageable
    );

    // ── Dashboard queries ────────────────────────────────────────────────────

    long countByCreatorAndProjectIsNull(User creator);

    long countByAssignee(User assignee);

    long countByAssigneeAndStatus(User assignee, TaskStatus status);

    long countByAssigneeAndDueDateBefore(User assignee, LocalDate date);

    /**
     * Upcoming due dates — ALL tasks visible to the user:
     *   1. Personal tasks (creator = user, no project)
     *   2. Project tasks assigned to user
     *   3. Any task in a project whose team the user is a member of
     */
    @Query("""
            SELECT DISTINCT t FROM Task t
            LEFT JOIN t.project p
            LEFT JOIN p.team team
            LEFT JOIN team.members tm
            WHERE t.dueDate >= :from
              AND t.dueDate <= :to
              AND t.status != :excludeStatus
              AND (
                (t.creator = :user AND t.project IS NULL)
                OR t.assignee = :user
                OR tm.user = :user
              )
            ORDER BY t.dueDate ASC
            """)
    List<Task> findUpcomingTasks(
            @Param("user") User user,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("excludeStatus") TaskStatus excludeStatus
    );

    /**
     * Overdue tasks — ALL tasks visible to the user:
     *   1. Personal tasks (creator = user, no project)
     *   2. Project tasks assigned to user
     *   3. Any task in a project whose team the user is a member of
     */
    @Query("""
            SELECT DISTINCT t FROM Task t
            LEFT JOIN t.project p
            LEFT JOIN p.team team
            LEFT JOIN team.members tm
            WHERE t.dueDate < :today
              AND t.status NOT IN :excludeStatuses
              AND (
                (t.creator = :user AND t.project IS NULL)
                OR t.assignee = :user
                OR tm.user = :user
              )
            """)
    List<Task> findOverdueTasks(
            @Param("user") User user,
            @Param("today") LocalDate today,
            @Param("excludeStatuses") List<TaskStatus> excludeStatuses
    );

    /**
     * Total count of ALL tasks visible to the user (personal + project + team)
     */
    @Query("""
            SELECT COUNT(DISTINCT t) FROM Task t
            LEFT JOIN t.project p
            LEFT JOIN p.team team
            LEFT JOIN team.members tm
            WHERE (
              (t.creator = :user AND t.project IS NULL)
              OR t.assignee = :user
              OR tm.user = :user
            )
            """)
    long countAllVisibleTasks(@Param("user") User user);

    /**
     * Count ALL visible tasks by status (completed, pending, etc.)
     */
    @Query("""
            SELECT COUNT(DISTINCT t) FROM Task t
            LEFT JOIN t.project p
            LEFT JOIN p.team team
            LEFT JOIN team.members tm
            WHERE t.status = :status
              AND (
                (t.creator = :user AND t.project IS NULL)
                OR t.assignee = :user
                OR tm.user = :user
              )
            """)
    long countAllVisibleTasksByStatus(
            @Param("user") User user,
            @Param("status") TaskStatus status
    );

    /**
     * Count ALL visible overdue tasks
     */
    @Query("""
            SELECT COUNT(DISTINCT t) FROM Task t
            LEFT JOIN t.project p
            LEFT JOIN p.team team
            LEFT JOIN team.members tm
            WHERE t.dueDate < :today
              AND t.status NOT IN :excludeStatuses
              AND (
                (t.creator = :user AND t.project IS NULL)
                OR t.assignee = :user
                OR tm.user = :user
              )
            """)
    long countAllVisibleOverdueTasks(
            @Param("user") User user,
            @Param("today") LocalDate today,
            @Param("excludeStatuses") List<TaskStatus> excludeStatuses
    );

    // Scheduler: all overdue tasks across all assignees
    @Query("""
            SELECT t FROM Task t
            WHERE t.dueDate < :today
              AND t.status NOT IN :excludeStatuses
              AND t.assignee IS NOT NULL
            """)
    List<Task> findAllOverdueTasks(
            @Param("today") LocalDate today,
            @Param("excludeStatuses") List<TaskStatus> excludeStatuses
    );

    // Scheduler: find tasks due tomorrow for email reminder
    @Query("""
            SELECT t FROM Task t
            WHERE t.dueDate = :tomorrow
              AND t.status NOT IN :excludeStatuses
              AND t.assignee IS NOT NULL
            """)
    List<Task> findTasksDueTomorrow(
            @Param("tomorrow") LocalDate tomorrow,
            @Param("excludeStatuses") List<TaskStatus> excludeStatuses
    );

    long countByProject(Project project);

    long countByProjectAndStatus(Project project, TaskStatus status);
}