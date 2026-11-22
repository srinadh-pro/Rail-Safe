package com.railsafe.live.service;

import com.railsafe.config.LiveProperties;
import com.railsafe.dto.GateResponse;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.Role;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.live.dto.GatePredictionResponse;
import com.railsafe.live.dto.LiveDashboardResponse;
import com.railsafe.live.dto.LiveGateSummaryResponse;
import com.railsafe.live.dto.ProviderStatusResponse;
import com.railsafe.live.dto.TrainTrackingResponse;
import com.railsafe.live.model.LiveTrainStatus;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.StaffAssignmentRepository;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.DemoDataClassifier;
import com.railsafe.service.GateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only aggregation for the React dashboard. One poll returns the gate,
 * tracking, prediction and provider status so the browser does not make a burst of
 * requests that can disagree with each other. STAFF callers are scoped to gates
 * actively assigned to their account.
 */
@Service
public class LiveDashboardService {

    private final RailwayGateRepository gateRepository;
    private final StaffAssignmentRepository assignmentRepository;
    private final GateMapper gateMapper;
    private final TrainTrackingService trainTrackingService;
    private final GatePredictionService gatePredictionService;
    private final LiveProperties properties;

    public LiveDashboardService(RailwayGateRepository gateRepository,
                                StaffAssignmentRepository assignmentRepository,
                                GateMapper gateMapper,
                                TrainTrackingService trainTrackingService,
                                GatePredictionService gatePredictionService,
                                LiveProperties properties) {
        this.gateRepository = gateRepository;
        this.assignmentRepository = assignmentRepository;
        this.gateMapper = gateMapper;
        this.trainTrackingService = trainTrackingService;
        this.gatePredictionService = gatePredictionService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<LiveGateSummaryResponse> listGates() {
        return listGates(null);
    }

    @Transactional(readOnly = true)
    public List<LiveGateSummaryResponse> listGates(AppUserPrincipal principal) {
        Set<Long> assignedIds = assignedIds(principal);
        return gateRepository.findAllByOrderByNameAsc().stream()
                .filter(gate -> !DemoDataClassifier.isDemoGate(gate))
                .filter(gate -> assignedIds == null || assignedIds.contains(gate.getId()))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public LiveDashboardResponse dashboard(Long gateId) {
        return dashboard(gateId, null);
    }

    @Transactional(readOnly = true)
    public LiveDashboardResponse dashboard(Long gateId, AppUserPrincipal principal) {
        Set<Long> assignedIds = assignedIds(principal);
        RailwayGate gate;
        if (gateId == null) {
            gate = gateRepository.findAllByOrderByNameAsc().stream()
                    .filter(found -> !DemoDataClassifier.isDemoGate(found))
                    .filter(found -> assignedIds == null || assignedIds.contains(found.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            assignedIds == null
                                    ? "No railway gates are configured yet."
                                    : "No crossings are assigned to your staff account."));
        } else {
            ensureStaffAccess(principal, gateId);
            gate = gateRepository.findById(gateId)
                    .filter(found -> !DemoDataClassifier.isDemoGate(found))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Railway gate with ID " + gateId + " was not found."));
        }

        GateResponse gateResponse = gateMapper.toResponse(gate);
        TrainTrackingResponse tracking = trainTrackingService.trackingForGate(gate);
        GatePredictionResponse prediction = gatePredictionService.predict(gate);
        ProviderStatusResponse provider = trainTrackingService.providerStatus();

        return new LiveDashboardResponse(
                Instant.now(),
                Math.max(5, properties.getCacheSeconds()),
                gateResponse,
                tracking,
                prediction,
                provider
        );
    }

    private Set<Long> assignedIds(AppUserPrincipal principal) {
        if (principal == null || principal.getRole() != Role.STAFF || principal.getId() == null) {
            return null;
        }
        return assignmentRepository.findByUserIdAndActiveTrueOrderByGateNameAsc(principal.getId()).stream()
                .map(assignment -> assignment.getGate().getId())
                .collect(Collectors.toSet());
    }

    private void ensureStaffAccess(AppUserPrincipal principal, Long gateId) {
        if (principal != null && principal.getRole() == Role.STAFF) {
            if (gateId == null || !assignmentRepository.existsByUserIdAndGateIdAndActiveTrue(
                    principal.getId(), gateId)) {
                throw new ResourceNotFoundException("Railway gate with ID " + gateId + " was not found.");
            }
        }
    }

    private LiveGateSummaryResponse toSummary(RailwayGate gate) {
        GatePredictionResponse prediction = gatePredictionService.predict(gate);
        return new LiveGateSummaryResponse(
                gate.getId(),
                gate.getGateCode(),
                gate.getName(),
                gate.getLocation(),
                gate.getLatitude(),
                gate.getLongitude(),
                prediction.reportedStatus(),
                prediction.verifiedStatus(),
                prediction.statusConfidence(),
                prediction.stale(),
                prediction.liveDataAvailable(),
                prediction.dataMode(),
                prediction.relevantTrains().size(),
                (int) prediction.relevantTrains().stream()
                        .filter(train -> train.status() == LiveTrainStatus.APPROACHING
                                || train.status() == LiveTrainStatus.NEAR_GATE
                                || train.status() == LiveTrainStatus.CROSSING)
                        .count(),
                prediction.nextTrain() == null ? null : prediction.nextTrain().etaAtGate(),
                prediction.nextTrain() == null ? null
                        : prediction.nextTrain().trainName() + " (" + prediction.nextTrain().trainNumber() + ")",
                prediction.closing() != null && prediction.closing().available()
                        ? prediction.closing().estimatedCloseAt() : null,
                prediction.opening() != null && prediction.opening().available()
                        ? prediction.opening().estimatedOpenAt() : null
        );
    }
}
