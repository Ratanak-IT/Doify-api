package com.taskflow.repository;

import com.taskflow.domain.Notification;
import com.taskflow.domain.User;
import com.taskflow.domain.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    long countByRecipientAndIsReadFalse(User recipient);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient = :recipient AND n.isRead = false")
    void markAllAsReadByRecipient(User recipient);
    
    @Query("""
            SELECT COUNT(n) > 0 FROM Notification n
            WHERE n.recipient = :recipient
              AND n.type = :type
              AND n.referenceId = :referenceId
              AND n.createdAt >= :startOfDay
            """)
    boolean existsTodayNotification(
            @Param("recipient") User recipient,
            @Param("type") NotificationType type,
            @Param("referenceId") UUID referenceId,
            @Param("startOfDay") Instant startOfDay
    );
}