package com.railsafe.service;

import com.railsafe.dto.GateDetailResponse;
import com.railsafe.entity.GateStatus;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.StaffAssignment;
import com.railsafe.live.dto.GatePredictionResponse;
import com.railsafe.live.dto.LiveTrainResponse;
import com.railsafe.live.service.GatePredictionService;
import com.railsafe.repository.StaffAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Builds the complete operational detail view for one gate. */
@Service
public class GateDetailService {

    private static final String NOT_AVAILABLE = "Not Available";

    private final GateService gateService;
    private final GateMapper gateMapper;
    private final GatePredictionService predictionService;
    private final StaffAssignmentRepository assignmentRepository;

    public GateDetailService(GateService gateService,
                             GateMapper gateMapper,
                             GatePredictionService predictionService,
                             StaffAssignmentRepository assignmentRepository) {
        this.gateService = gateService;
        this.gateMapper = gateMapper;
        this.predictionService = predictionService;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public GateDetailResponse detail(Long id) {
        RailwayGate gate = gateService.requireGate(id);
        GatePredictionResponse prediction = predictionService.predict(gate);
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime since = gate.getStatusChangedAt() != null
                ? gate.getStatusChangedAt() : gate.getLastUpdatedAt();
        long duration = since == null ? 0 : Math.max(0, Duration.between(since, now).getSeconds());

        LocalDateTime expectedClose = gate.getExpectedClosingAt();
        String expectedCloseSource = expectedClose == null ? NOT_AVAILABLE : "Recorded estimate";
        if (expectedClose == null && prediction.closing() != null && prediction.closing().available()
                && prediction.closing().estimatedCloseAt() != null) {
            expectedClose = toLocal(prediction.closing().estimatedCloseAt());
            expectedCloseSource = "Authorised provider estimate";
        }

        LocalDateTime expectedOpen = gate.getExpectedOpeningAt();
        String expectedOpenSource = expectedOpen == null ? NOT_AVAILABLE : "Recorded estimate";
        if (expectedOpen == null && prediction.opening() != null && prediction.opening().available()
                && prediction.opening().estimatedOpenAt() != null) {
            expectedOpen = toLocal(prediction.opening().estimatedOpenAt());
            expectedOpenSource = "Authorised provider estimate";
        }

        LiveTrainResponse next = prediction.nextTrain();
        List<String> staff = assignmentRepository.findByGateIdAndActiveTrue(gate.getId()).stream()
                .map(StaffAssignment::getUser)
                .filter(user -> user != null)
                .map(user -> user.getFullName())
                .distinct()
                .toList();

        GateStatus publicStatus = gateMapper.publicStatus(gate);
        String status = publicStatus == GateStatus.AWAITING_VERIFICATION
                ? "Awaiting Verification" : publicStatus.name();

        String note = publicStatus == GateStatus.AWAITING_VERIFICATION
                ? "Gate status is awaiting verification. Do not rely on it for a crossing decision."
                : "Reported gate information only. Always obey physical barriers and railway staff.";

        return new GateDetailResponse(
                gate.getId(),
                gate.getGateCode(),
                gate.getName(),
                value(gate.getStation()),
                value(gate.getDistrict()),
                value(gate.getState()),
                value(gate.getRailwayZone()),
                value(gate.getDivision()),
                gate.getLatitude(),
                gate.getLongitude(),
                value(gate.getGateType()),
                status,
                gate.getVerificationStatus() == null ? NOT_AVAILABLE : gate.getVerificationStatus().name(),
                gate.getStatusSource() == null ? NOT_AVAILABLE : gate.getStatusSource().name(),
                gate.getDataClassification() == null ? NOT_AVAILABLE : gate.getDataClassification().name(),
                gate.getLastUpdatedAt(),
                since,
                duration,
                expectedClose,
                expectedOpen,
                gate.getActualClosingAt(),
                gate.getActualOpeningAt(),
                expectedCloseSource,
                expectedOpenSource,
                next == null ? null : next.trainNumber(),
                next == null ? null : next.trainName(),
                next == null || next.direction() == null ? null : next.direction().name(),
                next == null ? null : next.etaAtGate() == null ? null : toLocal(next.etaAtGate()),
                next == null ? null : next.distanceToGateKm(),
                next == null || next.status() == null ? null : next.status().name(),
                staff.isEmpty() ? List.of(NOT_AVAILABLE) : staff,
                value(gate.getAssociatedRoutes()),
                note
        );
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static String value(String text) {
        return text == null || text.isBlank() ? NOT_AVAILABLE : text;
    }
}
