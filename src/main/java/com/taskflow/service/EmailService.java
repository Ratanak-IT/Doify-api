package com.taskflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async("taskExecutor")
    public void sendVerificationEmail(String to, String name, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        String body = String.format("""
                Hello %s,
                                
                Welcome to TaskFlow! Please verify your email by clicking the link below:
                                
                %s
                                
                This link will expire in 24 hours.
                                
                Best regards,
                The TaskFlow Team
                """, name, link);
        send(to, "Verify Your TaskFlow Account", body);
    }

    @Async("taskExecutor")
    public void sendPasswordResetEmail(String to, String name, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String body = String.format("""
                Hello %s,
                                
                You requested a password reset. Click the link below to set a new password:
                                
                %s
                                
                This link expires in 1 hour. If you did not request this, please ignore this email.
                                
                Best regards,
                The TaskFlow Team
                """, name, link);
        send(to, "Reset Your TaskFlow Password", body);
    }

    @Async("taskExecutor")
    public void sendLoginNotificationEmail(String to, String name) {
        String body = String.format("""
                Hello %s,
                                
                Your TaskFlow account was logged in successfully.
                                
                If this was not you, please reset your password immediately.
                                
                Best regards,
                The TaskFlow Team
                """, name);
        send(to, "New Login to Your TaskFlow Account", body);
    }

    @Async("taskExecutor")
    public void sendTeamInvitationEmail(String to, String inviterName, String teamName,
                                        String role, String token) {
        String link = frontendUrl + "/invitations/accept?token=" + token;
        String body = String.format("""
                Hello,
                                
                %s has invited you to join the team "%s" on TaskFlow as %s.
                                
                Click the link below to accept the invitation:
                                
                %s
                                
                This invitation expires in 7 days.
                                
                Best regards,
                The TaskFlow Team
                """, inviterName, teamName, role, link);
        send(to, "You've been invited to join " + teamName + " on TaskFlow", body);
    }

    @Async("taskExecutor")
    public void sendTaskAssignedEmail(String to, String name, String taskTitle,
                                      String assignerName, String projectName) {
        String body = String.format("""
                Hello %s,
                                
                %s assigned you a new task: "%s"%s
                                
                Log in to TaskFlow to view the task details.
                                
                Best regards,
                The TaskFlow Team
                """,
                name,
                assignerName,
                taskTitle,
                projectName != null ? " in project " + projectName : "");
        send(to, "New Task Assigned: " + taskTitle, body);
    }

    @Async("taskExecutor")
    public void sendDueDateReminderEmail(String to, String name, String taskTitle, String dueDate) {
        String body = String.format("""
                Hello %s,
                                
                This is a reminder that your task "%s" is due tomorrow (%s).
                                
                Log in to TaskFlow to update your progress.
                                
                Best regards,
                The TaskFlow Team
                """, name, taskTitle, dueDate);
        send(to, "Task Due Tomorrow: " + taskTitle, body);
    }

    @Async("taskExecutor")
    public void sendMentionEmail(String to, String name, String commenterName,
                                 String taskTitle, String commentPreview) {
        String body = String.format("""
                Hello %s,
                                
                %s mentioned you in a comment on task "%s":
                                
                "%s"
                                
                Log in to TaskFlow to reply.
                                
                Best regards,
                The TaskFlow Team
                """, name, commenterName, taskTitle, commentPreview);
        send(to, commenterName + " mentioned you in a comment", body);
    }

    @Async("taskExecutor")
    public void sendCommentAddedEmail(String to, String recipientName, String commenterName,
                                      String taskTitle, String commentPreview) {
        String body = String.format("""
                Hello %s,
                                
                %s commented on task "%s":
                                
                "%s"
                                
                Log in to TaskFlow to view and reply.
                                
                Best regards,
                The TaskFlow Team
                """, recipientName, commenterName, taskTitle, commentPreview);
        send(to, commenterName + " commented on: " + taskTitle, body);
    }

    @Async("taskExecutor")
    public void sendOverdueTaskEmail(String to, String name, String taskTitle, String dueDate) {
        String body = String.format("""
                Hello %s,
                                
                Your task "%s" was due on %s and has not been completed yet.
                                
                Please update the task status or reschedule it as soon as possible.
                                
                Log in to TaskFlow to manage your tasks.
                                
                Best regards,
                The TaskFlow Team
                """, name, taskTitle, dueDate);
        send(to, "Overdue Task: " + taskTitle, body);
    }

    @Async("taskExecutor")
    public void sendInvitationAcceptedEmail(String to, String inviterName,
                                            String accepterName, String teamName) {
        String body = String.format("""
                Hello %s,
                                
                Great news! %s has accepted your invitation to join the team "%s" on TaskFlow.
                                
                Log in to TaskFlow to see your updated team.
                                
                Best regards,
                The TaskFlow Team
                """, inviterName, accepterName, teamName);
        send(to, accepterName + " accepted your invitation to join " + teamName, body);
    }

    @Async("taskExecutor")
    public void sendMemberRemovedEmail(String to, String recipientName, String teamName) {
        String body = String.format("""
                Hello %s,
                                
                You have been removed from the team "%s" on TaskFlow.
                                
                If you think this was a mistake, please contact your team owner.
                                
                Best regards,
                The TaskFlow Team
                """, recipientName, teamName);
        send(to, "You have been removed from " + teamName, body);
    }

    @Async("taskExecutor")
    public void sendRoleUpdatedEmail(String to, String recipientName,
                                     String teamName, String newRole) {
        String body = String.format("""
                Hello %s,
                                
                Your role in the team "%s" on TaskFlow has been updated to: %s.
                                
                Log in to TaskFlow to see your updated permissions.
                                
                Best regards,
                The TaskFlow Team
                """, recipientName, teamName, newRole);
        send(to, "Your role in " + teamName + " has been updated", body);
    }

    @Async("taskExecutor")
    public void sendProjectCreatedEmail(String to, String recipientName,
                                        String creatorName, String projectName, String teamName) {
        String body = String.format("""
                Hello %s,
                                
                %s created a new project "%s" in your team "%s" on TaskFlow.
                                
                Log in to TaskFlow to view the project details.
                                
                Best regards,
                The TaskFlow Team
                """, recipientName, creatorName, projectName, teamName);
        send(to, "New project created: " + projectName, body);
    }

    @Async("taskExecutor")
    public void sendProjectUpdatedEmail(String to, String recipientName, String updaterName,
                                        String projectName) {
        String body = String.format("""
                Hello %s,
                                
                %s made updates to the project "%s".
                                
                Log in to TaskFlow to see what changed.
                                
                Best regards,
                The TaskFlow Team
                """, recipientName, updaterName, projectName);
        send(to, "Project Updated: " + projectName, body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {} with subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }
}