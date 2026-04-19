package com.taskflow.service;

import com.taskflow.domain.Task;
import com.taskflow.domain.User;
import com.taskflow.domain.enums.NotificationType;
import com.taskflow.domain.enums.TaskStatus;
import com.taskflow.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final List<TaskStatus> TERMINAL_STATUSES =
            List.of(TaskStatus.DONE, TaskStatus.CANCELLED);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy");

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        log.info("=== Running missed scheduler jobs on startup ===");
        sendDueDateReminders();
        sendOverdueNotifications();
    }

    @Transactional
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDueDateReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Task> tasks = taskRepository.findTasksDueTomorrow(tomorrow, TERMINAL_STATUSES);

        log.info("Sending due-date reminders for {} tasks due on {}", tasks.size(), tomorrow);

        for (Task task : tasks) {
            String dueDateStr = tomorrow.format(DATE_FORMAT);
            String message = "Task \"" + task.getTitle() + "\" is due tomorrow (" + dueDateStr + ")";

            if (task.getAssignee() != null) {
                boolean sent = notificationService.send(
                        task.getAssignee(),
                        NotificationType.DUE_DATE_REMINDER,
                        message, task.getId(), "TASK"
                );
                if (sent) {
                    emailService.sendDueDateReminderEmail(
                            task.getAssignee().getEmail(),
                            task.getAssignee().getFullName(),
                            task.getTitle(), dueDateStr
                    );
                }
            }

            if (task.getAssignee() == null && task.getProject() == null) {
                User creator = task.getCreator();
                boolean sent = notificationService.send(
                        creator,
                        NotificationType.DUE_DATE_REMINDER,
                        message, task.getId(), "TASK"
                );
                if (sent) {
                    emailService.sendDueDateReminderEmail(
                            creator.getEmail(),
                            creator.getFullName(),
                            task.getTitle(), dueDateStr
                    );
                }
            }
        }
    }

    @Transactional
    @Scheduled(cron = "0 0 9 * * *")
    public void sendOverdueNotifications() {
        LocalDate today = LocalDate.now();
        List<Task> overdueTasks = taskRepository.findAllOverdueTasks(today, TERMINAL_STATUSES);

        log.info("Processing {} overdue task notifications for {}", overdueTasks.size(), today);

        for (Task task : overdueTasks) {
            String dueDateStr = task.getDueDate().format(DATE_FORMAT);
            String message = "Task \"" + task.getTitle() + "\" is overdue (was due " + dueDateStr + ")";

            if (task.getAssignee() != null) {
                boolean sent = notificationService.send(
                        task.getAssignee(),
                        NotificationType.OVERDUE_TASK,
                        message, task.getId(), "TASK"
                );
                if (sent) {
                    emailService.sendOverdueTaskEmail(
                            task.getAssignee().getEmail(),
                            task.getAssignee().getFullName(),
                            task.getTitle(), dueDateStr
                    );
                }
            }

            if (task.getAssignee() == null && task.getProject() == null) {
                User creator = task.getCreator();
                boolean sent = notificationService.send(
                        creator,
                        NotificationType.OVERDUE_TASK,
                        message, task.getId(), "TASK"
                );
                if (sent) {
                    emailService.sendOverdueTaskEmail(
                            creator.getEmail(),
                            creator.getFullName(),
                            task.getTitle(), dueDateStr
                    );
                }
            }
        }
    }
}