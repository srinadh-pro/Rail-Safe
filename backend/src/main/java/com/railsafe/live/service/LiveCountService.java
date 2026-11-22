package com.railsafe.live.service;

import com.railsafe.entity.GateStatus;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.Role;
import com.railsafe.live.dto.LiveCountsResponse;
import com.railsafe.live.model.LiveDataMode;
import com.railsafe.live.model.LiveTrainStatus;
import com.railsafe.live.model.TrainFeed;
import com.railsafe.live.model.TrainFeedTrain;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.StaffAssignmentRepository;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.DemoDataClassifier;
import com.railsafe.service.GateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for dashboard gate/train counts.
 *
 * Counts are derived from the current gate rows and the authorised provider feed.
 * Duplicate gate rows and duplicate train reports are removed before counting, and
 * every count is clamped at zero.
 */
@Service
public class LiveCountService {

    private final RailwayGateRepository gateRepository;
    private final StaffAssignmentRepository assignmentRepository;
    private final GateMapper gateMapper;
    private final TrainTrackingService trainTrackingService;

    public LiveCountService(RailwayGateRepository gateRepository,
                            StaffAssignmentRepository assignmentRepository,
                            GateMapper gateMapper,
                            TrainTrackingService trainTrackingService) {
        this.gateRepository = gateRepository;
        this.assignmentRepository = assignmentRepository;
        this.gateMapper = gateMapper;
        this.trainTrackingService = trainTrackingService;
    }

    /** Role-consistent counts: staff see their assigned gates, everyone else sees all. */
    @Transactional(readOnly = true)
    public LiveCountsResponse counts(AppUserPrincipal principal) {
        boolean staffScope = principal != null && principal.getRole() == Role.STAFF;
        return counts(staffScope ? principal.getId() : null);
    }

    @Transactional(readOnly = true)
    public LiveCountsResponse counts(Long staffUserId) {
        // Load assignments once: this keeps staff-scoped counts efficient at 1000+ gates.
        Set<Long> assignedGateIds = staffUserId == null ? null : new HashSet<>(
                assignmentRepository.findByUserIdAndActiveTrueOrderByGateNameAsc(staffUserId).stream()
                        .map(assignment -> assignment.getGate().getId())
                        .toList());
        List<RailwayGate> gates = gateRepository.findAllByOrderByNameAsc().stream()
                .filter(gate -> !DemoDataClassifier.isDemoGate(gate))
                .filter(gate -> assignedGateIds == null || assignedGateIds.contains(gate.getId()))
                .toList();

        // A gate appears at most once, even if the repository returns duplicate rows.
        Set<Long> distinctGateIds = new HashSet<>();
        List<RailwayGate> uniqueGates = gates.stream()
                .filter(gate -> gate.getId() != null && distinctGateIds.add(gate.getId()))
                .toList();

        long open = count(uniqueGates, GateStatus.OPEN);
        long closed = count(uniqueGates, GateStatus.CLOSED);
        long closing = count(uniqueGates, GateStatus.CLOSING);
        long opening = count(uniqueGates, GateStatus.OPENING);
        long maintenance = count(uniqueGates, GateStatus.MAINTENANCE);
        long awaiting = count(uniqueGates, GateStatus.AWAITING_VERIFICATION);
        long activeCrossings = closed + closing + opening;

        TrainFeed feed = trainTrackingService.currentFeed();
        // Even if a caller supplies a simulation feed, it is never a LIVE indicator.
        boolean liveDataAvailable = feed.available() && !feed.stale()
                && feed.mode() != LiveDataMode.SIMULATION
                && feed.mode() != LiveDataMode.UNAVAILABLE;
        Set<String> approaching = new HashSet<>();
        long crossingTrains = 0;
        if (liveDataAvailable) {
            Set<String> crossingKeys = new HashSet<>();
            for (TrainFeedTrain train : feed.trains()) {
                if (train.trainNumber() == null || train.trainNumber().isBlank()) {
                    continue;
                }
                String key = train.trainNumber().trim().toUpperCase(java.util.Locale.ROOT)
                        + "|" + (train.gateCode() == null ? "" : train.gateCode());
                LiveTrainStatus status = train.reportedStatus();
                if (status == LiveTrainStatus.APPROACHING || status == LiveTrainStatus.NEAR_GATE) {
                    approaching.add(train.trainNumber().trim().toUpperCase(java.util.Locale.ROOT));
                }
                if (status == LiveTrainStatus.CROSSING && crossingKeys.add(key)) {
                    crossingTrains++;
                }
            }
        }

        Instant lastUpdate = liveDataAvailable ? feed.updatedAt() : null;
        String dataSource = liveDataAvailable ? feed.provider() : "Unavailable";
        String dataMode = liveDataAvailable ? feed.mode().name() : LiveDataMode.UNAVAILABLE.name();
        String message = liveDataAvailable
                ? "Counts recalculated from current gate records and the authorised train feed."
                : "Live train data unavailable. Gate counts are from the latest verified database records.";

        return new LiveCountsResponse(
                Instant.now(),
                staffUserId == null ? "ALL_GATES" : "ASSIGNED_GATES",
                nonNegative(uniqueGates.size()),
                nonNegative(open),
                nonNegative(closed),
                nonNegative(closing),
                nonNegative(opening),
                nonNegative(maintenance),
                nonNegative(awaiting),
                nonNegative(approaching.size()),
                nonNegative(activeCrossings + crossingTrains),
                liveDataAvailable,
                dataSource,
                dataMode,
                lastUpdate,
                message
        );
    }

    /** Count of non-demo gates whose last verified update is stale. */
    @Transactional(readOnly = true)
    public long staleGateCount() {
        return gateRepository.findAllByOrderByNameAsc().stream()
                .filter(gate -> !DemoDataClassifier.isDemoGate(gate))
                .filter(gateMapper::isStale)
                .count();
    }

    private long count(List<RailwayGate> gates, GateStatus status) {
        return gates.stream()
                .filter(gate -> gateMapper.publicStatus(gate) == status)
                .count();
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }
}
