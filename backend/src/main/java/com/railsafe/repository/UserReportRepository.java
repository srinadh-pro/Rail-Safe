package com.railsafe.repository;

import com.railsafe.entity.ReportStatus;
import com.railsafe.entity.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    List<UserReport> findAllByOrderByCreatedAtDesc();

    List<UserReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<UserReport> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByStatus(ReportStatus status);

    boolean existsByUserIdAndGateIdAndStatus(Long userId, Long gateId, ReportStatus status);

    void deleteByGateId(Long gateId);
}
