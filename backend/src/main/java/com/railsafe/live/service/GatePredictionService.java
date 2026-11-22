package com.railsafe.live.service;

import com.railsafe.config.LiveProperties;
import com.railsafe.dto.GateResponse;
import com.railsafe.entity.GateStatus;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.StatusSource;
import com.railsafe.entity.VerificationStatus;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.live.dto.ClosingCountdownResponse;
import com.railsafe.live.dto.GatePredictionResponse;
import com.railsafe.live.dto.LiveTrainResponse;
import com.railsafe.live.dto.OpeningCountdownResponse;
import com.railsafe.live.dto.TrainTrackingResponse;
import com.railsafe.live.model.LiveDataMode;
import com.railsafe.live.model.LiveTrainStatus;
import com.railsafe.service.DemoDataClassifier;
import com.railsafe.service.GateMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Gate Prediction Service.
 *
 * Safety rules implemented here:
 *  - a gate is shown as OPEN/CLOSED only when its status is fresh and verified by
 *    staff or an authorised sensor; otherwise verifiedStatus is UNKNOWN;
 *  - countdowns are only produced from usable live data;
 *  - every closing/opening time is labelled as an estimate;
 *  - if data is stale or unavailable the countdown is withheld.
 */
@Service
public class GatePredictionService {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final GateMapper gateMapper;
    private final TrainTrackingService trainTrackingService;
    private final LiveProperties properties;

    public GatePredictionService(GateMapper gateMapper,
                                 TrainTrackingService trainTrackingService,
                                 LiveProperties properties) {
        this.gateMapper = gateMapper;
        this.trainTrackingService = trainTrackingService;
        this.properties = properties;
    }

    public GatePredictionResponse predict(RailwayGate gate) {
        if (DemoDataClassifier.isDemoGate(gate)) {
            throw new ResourceNotFoundException("Railway gate was not found.");
        }

        GateResponse gateResponse = gateMapper.toResponse(gate);
        TrainTrackingResponse tracking = trainTrackingService.trackingForGate(gate);
        Instant now = Instant.now();

        boolean fresh = !gateResponse.stale();
        boolean verifiedSource = gate.getVerificationStatus() == VerificationStatus.VERIFIED
                && gate.getStatusSource() != StatusSource.DEMO;
        boolean statusConfirmed = fresh && verifiedSource;

        GateStatus reportedStatus = gateResponse.displayStatus();
        GateStatus verifiedStatus = statusConfirmed ? reportedStatus : GateStatus.AWAITING_VERIFICATION;
        boolean dataUsable = tracking.available() && !tracking.stale();

        String statusConfidence;
        String statusMessage;
        if (statusConfirmed) {
            statusConfidence = gate.getStatusSource() == StatusSource.SENSOR
                    ? "CONFIRMED SENSOR" : "STAFF VERIFIED";
            statusMessage = "Status is fresh and verified by an authorised source.";
        } else if (!fresh) {
            statusConfidence = "STALE / UNKNOWN";
            statusMessage = "The last gate update is stale. The verified status is UNKNOWN.";
        } else if (gate.getVerificationStatus() == VerificationStatus.DEMO) {
            statusConfidence = "REPORTED / UNVERIFIED";
            statusMessage = "The gate status has not been verified by an authorised source. "
                    + "The verified status is UNKNOWN.";
        } else {
            statusConfidence = "REPORTED / UNVERIFIED";
            statusMessage = "The gate status has not been verified by staff or an authorised sensor. "
                    + "The verified status is UNKNOWN.";
        }

        LiveTrainResponse nextTrain = tracking.trains().stream()
                .filter(train -> train.etaAtGate() != null)
                .filter(train -> train.status() != LiveTrainStatus.PASSED
                        && train.status() != LiveTrainStatus.CROSSING)
                .filter(train -> train.etaAtGate().isAfter(now.minusSeconds(60)))
                .findFirst()
                .orElse(null);

        ClosingCountdownResponse closing = buildClosing(
                verifiedStatus, dataUsable, tracking, now);
        OpeningCountdownResponse opening = buildOpening(
                verifiedStatus, dataUsable, tracking, now);

        String consistencyWarning = buildConsistencyWarning(
                verifiedStatus, tracking, dataUsable);

        return new GatePredictionResponse(
                gate.getId(),
                gate.getGateCode(),
                gate.getName(),
                reportedStatus,
                verifiedStatus,
                statusConfirmed,
                statusConfidence,
                statusMessage,
                gateResponse.stale(),
                gate.getLastUpdatedAt(),
                tracking.available(),
                tracking.stale(),
                tracking.mode(),
                now,
                tracking.message(),
                nextTrain,
                closing,
                opening,
                tracking.trains(),
                consistencyWarning
        );
    }

    private ClosingCountdownResponse buildClosing(GateStatus verifiedStatus,
                                                  boolean dataUsable,
                                                  TrainTrackingResponse tracking,
                                                  Instant now) {
        if (verifiedStatus != GateStatus.OPEN) {
            return new ClosingCountdownResponse(false, null, 0,
                    "A closing countdown is only shown when the gate is verified OPEN.",
                    null, true);
        }
        if (!dataUsable) {
            return new ClosingCountdownResponse(false, null, 0,
                    "Live train data unavailable, so the closing countdown is withheld.",
                    null, true);
        }

        LiveTrainResponse approaching = tracking.trains().stream()
                .filter(train -> train.etaAtGate() != null)
                .filter(train -> train.status() == LiveTrainStatus.APPROACHING
                        || train.status() == LiveTrainStatus.NEAR_GATE)
                .findFirst()
                .orElse(null);

        if (approaching == null) {
            return new ClosingCountdownResponse(false, null, 0,
                    "Gate is verified OPEN. No approaching train is currently reported, so a closing countdown is unavailable.",
                    null, true);
        }

        Instant closeAt = approaching.etaAtGate().minusSeconds(Math.max(0, properties.getCloseLeadSeconds()));
        long seconds = Math.max(0, Duration.between(now, closeAt).getSeconds());
        String message = seconds == 0
                ? "Gate closure may be imminent: " + trainLabel(approaching)
                        + " is approaching. Obey the physical barrier and railway staff. ESTIMATE ONLY."
                : "Railway gate is OPEN. " + trainLabel(approaching)
                        + " is approaching. Estimated gate closure in approximately "
                        + minutes(seconds) + ". ESTIMATE ONLY - not a guarantee.";

        return new ClosingCountdownResponse(true, closeAt, seconds, message, approaching, true);
    }

    private OpeningCountdownResponse buildOpening(GateStatus verifiedStatus,
                                                  boolean dataUsable,
                                                  TrainTrackingResponse tracking,
                                                  Instant now) {
        if (verifiedStatus != GateStatus.CLOSED) {
            return new OpeningCountdownResponse(false, null, 0,
                    "An opening countdown is only shown when the gate is verified CLOSED.",
                    null, 0, true, "not applicable");
        }
        if (!dataUsable) {
            return new OpeningCountdownResponse(false, null, 0,
                    "WAITING TIME UNAVAILABLE - live train data is unavailable or stale.",
                    null, 0, true, "unavailable");
        }

        int waiting = (int) tracking.trains().stream()
                .filter(train -> train.status() == LiveTrainStatus.CROSSING
                        || train.status() == LiveTrainStatus.NEAR_GATE
                        || train.status() == LiveTrainStatus.APPROACHING)
                .count();

        LiveTrainResponse crossing = tracking.trains().stream()
                .filter(train -> train.status() == LiveTrainStatus.CROSSING)
                .findFirst()
                .orElse(tracking.trains().stream()
                        .filter(train -> train.status() == LiveTrainStatus.NEAR_GATE)
                        .filter(train -> train.etaAtGate() != null)
                        .findFirst()
                        .orElse(null));

        if (crossing == null) {
            return new OpeningCountdownResponse(false, null, 0,
                    "WAITING TIME UNAVAILABLE - the gate is reported CLOSED but no crossing train is present in the live feed.",
                    null, waiting, true, "no crossing train reported");
        }

        Instant openAt = crossingEndTime(crossing);
        String basis = "crossing window plus " + properties.getOpeningBufferSeconds()
                + " s safety buffer";
        if (openAt == null) {
            return new OpeningCountdownResponse(false, null, 0,
                    "WAITING TIME UNAVAILABLE - the provider did not supply a crossing window for this train.",
                    crossing, waiting, true, "no crossing window supplied");
        }
        openAt = openAt.plusSeconds(Math.max(0, properties.getOpeningBufferSeconds()));

        // Do not reopen the barrier between two trains when another service is due
        // within the configured gap: extend the expected reopening past the last one.
        int queuedBehind = 0;
        Instant reference = crossing.etaAtGate() != null ? crossing.etaAtGate() : now;
        for (LiveTrainResponse other : tracking.trains()) {
            if (other == crossing || other.etaAtGate() == null || !other.etaAtGate().isAfter(reference)) {
                continue;
            }
            Instant otherStart = other.crossingStartedAt() != null
                    ? other.crossingStartedAt()
                    : other.etaAtGate().minusSeconds(Math.max(0, properties.getCloseLeadSeconds()));
            if (otherStart.isAfter(openAt.plusSeconds(Math.max(0, properties.getReopenGapSeconds())))) {
                break;
            }
            Instant otherEnd = crossingEndTime(other);
            if (otherEnd == null) {
                continue;
            }
            Instant otherOpen = otherEnd.plusSeconds(Math.max(0, properties.getOpeningBufferSeconds()));
            if (otherOpen.isAfter(openAt)) {
                openAt = otherOpen;
                queuedBehind++;
            }
        }
        if (queuedBehind > 0) {
            basis = "last of " + (queuedBehind + 1) + " queued trains, plus "
                    + properties.getOpeningBufferSeconds() + " s safety buffer";
            waiting += queuedBehind;
        }

        long seconds = Math.max(0, Duration.between(now, openAt).getSeconds());
        String queueNote = queuedBehind > 0
                ? " Another train is due within the reopening gap, so the barrier is not expected to "
                        + "open between the two services."
                : "";
        String message = seconds == 0
                ? "The estimated reopening time has passed. Verify locally; do not assume the crossing is open. ESTIMATE ONLY."
                : "Gate is expected to open in approximately " + minutes(seconds)
                        + " (" + CLOCK.format(openAt) + ")." + queueNote + " ESTIMATE ONLY - not a guarantee.";

        return new OpeningCountdownResponse(true, openAt, seconds, message, crossing,
                waiting, true, basis);
    }

    private String buildConsistencyWarning(GateStatus verifiedStatus,
                                           TrainTrackingResponse tracking,
                                           boolean dataUsable) {
        if (!dataUsable) {
            return null;
        }
        boolean crossing = tracking.trains().stream()
                .anyMatch(train -> train.status() == LiveTrainStatus.CROSSING);
        if (verifiedStatus == GateStatus.OPEN && crossing) {
            return "The live feed shows a train crossing while the gate is verified OPEN. Treat the gate status as uncertain and obey physical signals.";
        }
        if (verifiedStatus == GateStatus.CLOSED && !crossing) {
            return "The gate is verified CLOSED, but no crossing train is present in the live feed. The feed may be incomplete.";
        }
        if (tracking.mode() == LiveDataMode.SCHEDULE) {
            return "Only scheduled train data is available. These are not live train positions.";
        }
        return null;
    }

    private Instant crossingEndTime(LiveTrainResponse train) {
        if (train.crossingEndsAt() != null) {
            return train.crossingEndsAt();
        }
        if (train.etaAtGate() != null) {
            return train.etaAtGate().plusSeconds(Math.max(60, properties.getCrossingDurationSeconds()));
        }
        return null;
    }

    private static String trainLabel(LiveTrainResponse train) {
        return train.trainName() + " (" + train.trainNumber() + ")";
    }

    private static String minutes(long seconds) {
        long value = Math.max(1, (seconds + 59) / 60);
        return value == 1 ? "1 minute" : value + " minutes";
    }
}
