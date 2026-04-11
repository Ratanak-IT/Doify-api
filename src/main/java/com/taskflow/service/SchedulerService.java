package com.taskflow.service;

import com.taskflow.domain.Task;
import com.taskflow.domain.enums.NotificationType;
import com.taskflow.domain.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    private static final List<TaskStatus> TERMINAL_STATUSES = List.of(TaskStatus.DONE, TaskStatus.CANCELLED);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    @Scheduled(cron = "0 0 8 * * *")
    public void sendDueDateReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Task> tasksDueTomorrow = taskRepository.findTasksDueTomorrow(tomorrow, TERMINAL_STATUSES);

        log.info("Sending due-date reminders for {} tasks due on {}", tasksDueTomorrow.size(), tomorrow);

        for (Task task : tasksDueTomorrow) {
            if (task.getAssignee() == null) continue;

            notificationService.send(
                    task.getAssignee(),
                    NotificationType.DUE_DATE_REMINDER,
                    "Task \"" + task.getTitle() + "\" is due tomorrow (" + tomorrow.format(DATE_FORMAT) + ")",
                    task.getId(),
                    "TASK"
            );

            emailService.sendDueDateReminderEmail(
                    task.getAssignee().getEmail(),
                    task.getAssignee().getFullName(),
                    task.getTitle(),
                    tomorrow.format(DATE_FORMAT)
            );
        }
    }

    /**
     * Runs every day at 9:00 AM — notifies assignees of overdue tasks.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendOverdueNotifications() {
        LocalDate today = LocalDate.now();
        List<Task> overdueTasks = taskRepository.findAllOverdueTasks(today, TERMINAL_STATUSES);

        log.info("Processing overdue task notifications for {} tasks on {}", overdueTasks.size(), today);

        overdueTasks.stream()
                .filter(t -> t.getAssignee() != null)
                .forEach(task -> {
                    String dueDateStr = task.getDueDate().format(DATE_FORMAT);

                    notificationService.send(
                            task.getAssignee(),
                            NotificationType.OVERDUE_TASK,
                            "Task \"" + task.getTitle() + "\" is overdue (was due " + dueDateStr + ")",
                            task.getId(),
                            "TASK"
                    );

                    emailService.sendOverdueTaskEmail(
                            task.getAssignee().getEmail(),
                            task.getAssignee().getFullName(),
                            task.getTitle(),
                            dueDateStr
                    );
                });
    }
}