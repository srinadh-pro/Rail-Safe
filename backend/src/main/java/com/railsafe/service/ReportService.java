package com.railsafe.service;

import com.railsafe.dto.ReportDecisionRequest;
import com.railsafe.dto.ReportRequest;
import com.railsafe.dto.ReportResponse;
import com.railsafe.entity.*;
import com.railsafe.exception.BadRequestException;
import com.railsafe.exception.ConflictException;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.UserReportRepository;
import com.railsafe.repository.UserRepository;
import com.railsafe.security.AppUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Commuter reports are advisory. They never update a gate automatically.
 * Approving a report is the only path that turns a user report into a verified gate update.
 */
@Service
public class ReportService {

    private final UserReportRepository reportRepository;
    private final RailwayGateRepository gateRepository;
    private final UserRepository userRepository;
    private final GateService gateService;
    private final NotificationService notificationService;

    public ReportService(UserReportRepository reportRepository,
                         RailwayGateRepository gateRepository,
                         UserRepository userRepository,
                         GateService gateService,
                         NotificationService notificationService) {
        this.reportRepository = reportRepository;
        this.gateRepository = gateRepository;
        this.userRepository = userRepository;
        this.gateService = gateService;
        this.notificationService = notificationService;
    }

    @Transactional
    public ReportResponse create(ReportRequest request, AppUserPrincipal principal) {
        User user = findUser(principal.getId());
        RailwayGate gate = gateRepository.findById(request.gateId())
                .filter(found -> !DemoDataClassifier.isDemoGate(found))
                .orElseThrow(() -> new ResourceNotFoundException("Railway gate was not found."));

        if (reportRepository.existsByUserIdAndGateIdAndStatus(
                user.getId(), gate.getId(), ReportStatus.PENDING)) {
            throw new ConflictException("You already have a pending report for this gate. "
                    + "Please wait for staff to review it.");
        }

        UserReport report = new UserReport();
        report.setUser(user);
        report.setGate(gate);
        report.setReportedStatus(request.reportedStatus());
        report.setComment(trimToNull(request.comment()));
        report.setStatus(ReportStatus.PENDING);
        reportRepository.save(report);

        return toResponse(report);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> list(ReportStatus status) {
        return list(status, null);
    }

    /** STAFF callers only receive reports about their actively assigned gates. */
    @Transactional(readOnly = true)
    public List<ReportResponse> list(ReportStatus status, AppUserPrincipal principal) {
        List<UserReport> reports = status == null
                ? reportRepository.findAllByOrderByCreatedAtDesc()
                : reportRepository.findByStatusOrderByCreatedAtDesc(status);
        Set<Long> assignedGateIds = gateService.assignedGateIds(principal);
        return reports.stream()
                .filter(report -> !DemoDataClassifier.isDemoReport(report))
                .filter(report -> assignedGateIds == null
                        || assignedGateIds.contains(report.getGate().getId()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> mine(Long userId) {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(report -> !DemoDataClassifier.isDemoReport(report))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReportResponse approve(Long id, ReportDecisionRequest request, AppUserPrincipal principal) {
        UserReport report = findPendingReport(id);
        gateService.ensureReadAccess(principal, report.getGate().getId());
        User reviewer = findUser(principal.getId());
        String reviewNote = request == null ? null : trimToNull(request.reviewNote());

        report.setStatus(ReportStatus.APPROVED);
        report.setReviewedBy(reviewer);
        report.setReviewedAt(LocalDateTime.now());
        report.setReviewNote(reviewNote);
        reportRepository.save(report);

        String historyNote = "Approved user report #" + report.getId()
                + (reviewNote == null ? "" : " — " + reviewNote);
        gateService.applyStatusChange(
                report.getGate(),
                report.getReportedStatus(),
                StatusSource.STAFF,
                VerificationStatus.VERIFIED,
                reviewer,
                truncate(historyNote, 300)
        );

        notificationService.notifyReportDecision(report, true);
        return toResponse(report);
    }

    @Transactional
    public ReportResponse reject(Long id, ReportDecisionRequest request, AppUserPrincipal principal) {
        UserReport report = findPendingReport(id);
        gateService.ensureReadAccess(principal, report.getGate().getId());
        User reviewer = findUser(principal.getId());
        String reviewNote = request == null ? null : trimToNull(request.reviewNote());

        report.setStatus(ReportStatus.REJECTED);
        report.setReviewedBy(reviewer);
        report.setReviewedAt(LocalDateTime.now());
        report.setReviewNote(reviewNote);
        reportRepository.save(report);

        notificationService.notifyReportDecision(report, false);
        return toResponse(report);
    }

    private UserReport findPendingReport(Long id) {
        UserReport report = reportRepository.findById(id)
                .filter(found -> !DemoDataClassifier.isDemoReport(found))
                .orElseThrow(() -> new ResourceNotFoundException("Report with ID " + id + " was not found."));
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new BadRequestException("This report has already been reviewed.");
        }
        return report;
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found."));
    }

    private ReportResponse toResponse(UserReport report) {
        User reviewer = report.getReviewedBy();
        return new ReportResponse(
                report.getId(),
                report.getGate().getId(),
                report.getGate().getGateCode(),
                report.getGate().getName(),
                report.getUser().getId(),
                report.getUser().getFullName(),
                report.getReportedStatus(),
                report.getComment(),
                report.getStatus(),
                report.getCreatedAt(),
                reviewer == null ? null : reviewer.getFullName(),
                report.getReviewedAt(),
                report.getReviewNote()
        );
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
