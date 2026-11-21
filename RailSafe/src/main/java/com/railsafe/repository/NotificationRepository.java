package com.railsafe.repository;

import com.railsafe.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    boolean existsByDedupeKey(String dedupeKey);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    void deleteByGateId(Long gateId);
}
