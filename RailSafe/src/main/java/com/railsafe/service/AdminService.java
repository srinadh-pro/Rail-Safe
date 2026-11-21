package com.railsafe.service;

import com.railsafe.dto.AdminStatsResponse;
import com.railsafe.entity.ReportStatus;
import com.railsafe.live.dto.LiveCountsResponse;
import com.railsafe.live.service.LiveCountService;
import com.railsafe.repository.UserReportRepository;
import com.railsafe.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations statistics. Gate and train counts are delegated to the shared live
 * counter so every dashboard uses exactly the same calculation.
 */
@Service
public class AdminService {

    private final UserReportRepository reportRepository;
    private final UserRepository userRepository;
    private final LiveCountService countService;

    public AdminService(UserReportRepository reportRepository,
                        UserRepository userRepository,
                        LiveCountService countService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.countService = countService;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        LiveCountsResponse counts = countService.counts((Long) null);
        long stale = countService.staleGateCount();
        long pendingReports = reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(report -> !DemoDataClassifier.isDemoReport(report))
                .filter(report -> report.getStatus() == ReportStatus.PENDING)
                .count();
        long users = userRepository.findAll().stream()
                .filter(user -> !DemoDataClassifier.isDemoUser(user))
                .count();

        return new AdminStatsResponse(
                counts.totalGates(),
                counts.openGates(),
                counts.closedGates(),
                counts.awaitingVerificationGates(),
                stale,
                counts.closingGates(),
                counts.openingGates(),
                counts.maintenanceGates(),
                counts.awaitingVerificationGates(),
                counts.approachingTrains(),
                counts.activeCrossings(),
                counts.liveDataAvailable(),
                counts.dataMode(),
                counts.lastSuccessfulUpdate(),
                pendingReports,
                users
        );
    }
}
