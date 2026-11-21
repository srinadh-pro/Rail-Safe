package com.railsafe.live.service;

import com.railsafe.config.LiveProperties;
import com.railsafe.entity.GateStatus;
import com.railsafe.entity.NotificationType;
import com.railsafe.entity.RailwayGate;
import com.railsafe.live.dto.ClosingCountdownResponse;
import com.railsafe.live.dto.GatePredictionResponse;
import com.railsafe.live.dto.LiveTrainResponse;
import com.railsafe.live.dto.OpeningCountdownResponse;
import com.railsafe.live.model.LiveDataMode;
import com.railsafe.live.model.LiveTrainStatus;
import com.railsafe.live.model.TrainFeed;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.service.DemoDataClassifier;
import com.railsafe.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Server-side alert evaluation.
 *
 * Every run reads the cached provider response once, stores an audit snapshot when
 * real provider data exists, then evaluates each gate. Alerts are only created when
 * the data is usable, and the database dedupe key prevents repeats. Simulation
 * alerts are off unless the operator explicitly enables them.
 */
@Component
public class LiveAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(LiveAlertScheduler.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final RailwayGateRepository gateRepository;
    private final TrainTrackingService trainTrackingService;
    private final GatePredictionService gatePredictionService;
    private final TrainSnapshotService snapshotService;
    private final NotificationService notificationService;
    private final LiveProperties properties;

    public LiveAlertScheduler(RailwayGateRepository gateRepository,
                              TrainTrackingService trainTrackingService,
                              GatePredictionService gatePredictionService,
                              TrainSnapshotService snapshotService,
                              NotificationService notificationService,
                              LiveProperties properties) {
        this.gateRepository = gateRepository;
        this.trainTrackingService = trainTrackingService;
        this.gatePredictionService = gatePredictionService;
        this.snapshotService = snapshotService;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${railsafe.live.alert-interval-millis:60000}",
            initialDelayString = "${railsafe.live.alert-initial-delay-millis:15000}")
    public void evaluate() {
        if (!properties.isAlertsEnabled()) {
            return;
        }

        try {
            TrainFeed feed = trainTrackingService.currentFeed();
            snapshotService.capture(feed);

            if (feed.mode() == LiveDataMode.SIMULATION || feed.mode() == LiveDataMode.UNAVAILABLE) {
                return;
            }

            for (RailwayGate gate : gateRepository.findAllByOrderByNameAsc()) {
                if (!DemoDataClassifier.isDemoGate(gate)) {
                    evaluateGate(gate, feed.mode());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Live alert evaluation failed: {}", ex.getMessage());
        }
    }

    private void evaluateGate(RailwayGate gate, LiveDataMode mode) {
        boolean thirdParty = mode == LiveDataMode.THIRD_PARTY;
        GatePredictionResponse prediction = gatePredictionService.predict(gate);
        if (prediction.verifiedStatus() == GateStatus.AWAITING_VERIFICATION
                || prediction.verifiedStatus() == GateStatus.UNKNOWN) {
            return;
        }
        if (!prediction.liveDataAvailable() || prediction.liveDataStale()) {
            return;
        }

        Instant now = Instant.now();
        String day = LocalDate.now(ZoneId.systemDefault()).toString();

        LiveTrainResponse next = prediction.nextTrain();
        if (next != null && next.etaAtGate() != null
                && (next.status() == LiveTrainStatus.APPROACHING
                        || next.status() == LiveTrainStatus.NEAR_GATE)) {
            long seconds = Duration.between(now, next.etaAtGate()).getSeconds();
            if (seconds >= 0 && seconds <= properties.getApproachAlertSeconds()) {
                notificationService.notifyLiveEvent(
                        gate,
                        NotificationType.TRAIN_APPROACHING,
                        "Train approaching " + gate.getName(),
                        next.trainName() + " (" + next.trainNumber() + ") is reported approaching "
                                + gate.getName() + ". Estimated arrival at the crossing: "
                                + CLOCK.format(next.etaAtGate()) + ". "
                                + dataNote(thirdParty)
                                + "Estimate only; obey the physical barrier and railway staff.",
                        "live:approach:" + gate.getId() + ":" + day + ":" + next.trainNumber(),
                        prediction.verifiedStatus()
                );
            }
        }

        ClosingCountdownResponse closing = prediction.closing();
        if (closing.available() && closing.secondsUntilClose() > 0
                && closing.secondsUntilClose() <= properties.getClosingSoonSeconds()
                && closing.train() != null) {
            notificationService.notifyLiveEvent(
                    gate,
                    NotificationType.GATE_CLOSING_SOON,
                    "Gate may close soon at " + gate.getName(),
                    "Estimated closure in about " + minutes(closing.secondsUntilClose())
                            + " for " + closing.train().trainName()
                            + " (" + closing.train().trainNumber() + "). "
                            + dataNote(thirdParty)
                            + "ESTIMATE ONLY - never a guarantee. Do not enter a closed crossing.",
                    "live:closing:" + gate.getId() + ":" + day + ":"
                            + closing.train().trainNumber(),
                    prediction.verifiedStatus()
            );
        }

        OpeningCountdownResponse opening = prediction.opening();
        if (opening.available() && opening.secondsUntilOpen() > 0
                && opening.secondsUntilOpen() <= properties.getOpeningSoonSeconds()
                && opening.train() != null) {
            notificationService.notifyLiveEvent(
                    gate,
                    NotificationType.GATE_OPENING_SOON,
                    "Gate may reopen soon at " + gate.getName(),
                    "Estimated reopening in about " + minutes(opening.secondsUntilOpen())
                            + " after the current train clears. "
                            + dataNote(thirdParty)
                            + "ESTIMATE ONLY. Verify locally before moving.",
                    "live:opening:" + gate.getId() + ":" + day + ":"
                            + opening.train().trainNumber(),
                    prediction.verifiedStatus()
            );
        }

        // Train crossing / passed events. The dedupe key keeps the scheduler from
        // repeating the same alert every minute while the train is in the window.
        long segment = 0L;
        if (prediction.relevantTrains() != null) {
            for (LiveTrainResponse train : prediction.relevantTrains()) {
                if (train.trainNumber() == null) {
                    continue;
                }
                if (train.status() == LiveTrainStatus.CROSSING) {
                    notificationService.notifyLiveEvent(
                            gate,
                            NotificationType.TRAIN_CROSSING,
                            "Train crossing " + gate.getName(),
                            train.trainName() + " (" + train.trainNumber() + ") is reported crossing "
                                    + gate.getName() + ". "
                                    + dataNote(thirdParty)
                                    + "Never enter a closed crossing; obey physical barriers.",
                            "live:crossing:" + gate.getId() + ":" + day + ":" + segment
                                    + ":" + train.trainNumber(),
                            GateStatus.CLOSED
                    );
                } else if (train.status() == LiveTrainStatus.PASSED) {
                    notificationService.notifyLiveEvent(
                            gate,
                            NotificationType.TRAIN_PASSED,
                            "Train passed " + gate.getName(),
                            train.trainName() + " (" + train.trainNumber() + ") has passed "
                                    + gate.getName() + ". "
                                    + dataNote(thirdParty)
                                    + "Do not assume the crossing is open; verify locally.",
                            "live:passed:" + gate.getId() + ":" + day + ":" + segment
                                    + ":" + train.trainNumber(),
                            GateStatus.OPEN
                    );
                }
            }
        }

    }

    private static String dataNote(boolean thirdParty) {
        if (thirdParty) {
            return "This is third-party data, not official Indian Railways information. ";
        }
        return "";
    }

    private static String minutes(long seconds) {
        long value = Math.max(1, (seconds + 59) / 60);
        return value == 1 ? "1 minute" : value + " minutes";
    }
}
