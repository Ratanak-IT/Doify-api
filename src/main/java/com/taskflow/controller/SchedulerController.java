package com.taskflow.controller;

import com.taskflow.service.SchedulerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dev/scheduler")
@RequiredArgsConstructor
@Tag(name = "Dev Scheduler", description = "Manually trigger scheduled jobs (dev only)")
@SecurityRequirement(name = "bearerAuth")
public class SchedulerController {

    private final SchedulerService schedulerService;

    @PostMapping("/due-date-reminders")
    @Operation(summary = "Trigger due-date reminders (normally runs at 8AM)")
    public ResponseEntity<Map<String, String>> triggerDueDateReminders() {
        schedulerService.sendDueDateReminders();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Due-date reminders triggered successfully"
        ));
    }


    @PostMapping("/overdue-notifications")
    @Operation(summary = "Trigger overdue notifications (normally runs at 9AM)")
    public ResponseEntity<Map<String, String>> triggerOverdueNotifications() {
        schedulerService.sendOverdueNotifications();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Overdue notifications triggered successfully"
        ));
    }

    @PostMapping("/all")
    @Operation(summary = "Trigger ALL scheduled jobs at once")
    public ResponseEntity<Map<String, String>> triggerAll() {
        schedulerService.sendDueDateReminders();
        schedulerService.sendOverdueNotifications();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "All scheduled jobs triggered successfully"
        ));
    }
}