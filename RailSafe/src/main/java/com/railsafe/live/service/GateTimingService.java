package com.railsafe.live.service;

import com.railsafe.config.LiveProperties;
import com.railsafe.entity.RailwayGate;
import com.railsafe.live.dto.GatePredictionResponse;
import com.railsafe.live.dto.GateTimingResponse;
import com.railsafe.live.dto.LiveTrainResponse;
import com.railsafe.live.model.LiveTrainStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Exposes the closing/opening countdowns as a small, stable API payload.
 *
 * Every value is derived from GatePredictionService: this class never invents a
 * time. The seconds fields are a convenience snapshot for clients that cannot tick
 * locally; the authoritative value is always the absolute target timestamp.
 */
@Service
public class GateTimingService {

    private final GatePredictionService predictionService;
    private final LiveProperties properties;

    public GateTimingService(GatePredictionService predictionService, LiveProperties properties) {
        this.predictionService = predictionService;
        this.properties = properties;
    }

    public GateTimingResponse timing(RailwayGate gate) {
        GatePredictionResponse prediction = predictionService.predict(gate);

        List<LiveTrainResponse> trains = prediction.relevantTrains() == null
                ? List.of()
                : prediction.relevantTrains();

        int approaching = (int) trains.stream()
                .filter(train -> train.status() == LiveTrainStatus.APPROACHING
                        || train.status() == LiveTrainStatus.NEAR_GATE)
                .count();

        Long closingSeconds = prediction.closing().available()
                ? Math.max(0L, prediction.closing().secondsUntilClose())
                : null;
        Long openingSeconds = prediction.opening().available()
                ? Math.max(0L, prediction.opening().secondsUntilOpen())
                : null;

        String disclaimer = "Estimates only. Never a guaranteed opening or closing time. "
                + "Always obey physical barriers, signals and railway staff.";

        return new GateTimingResponse(
                gate.getId(),
                gate.getGateCode(),
                gate.getName(),
                prediction.reportedStatus(),
                prediction.verifiedStatus(),
                prediction.statusConfidence(),
                false,
                false,
                prediction.generatedAt(),
                prediction.closing().available() ? prediction.closing().estimatedCloseAt() : null,
                closingSeconds,
                prediction.closing().message(),
                prediction.opening().available() ? prediction.opening().estimatedOpenAt() : null,
                openingSeconds,
                prediction.opening().message(),
                approaching,
                prediction.opening().trainsWaiting(),
                prediction.nextTrain(),
                trains,
                Math.max(0L, properties.getCloseLeadSeconds()),
                Math.max(60L, properties.getCrossingDurationSeconds()),
                Math.max(0L, properties.getOpeningBufferSeconds()),
                disclaimer
        );
    }
}
