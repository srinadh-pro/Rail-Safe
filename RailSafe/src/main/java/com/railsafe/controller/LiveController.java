package com.railsafe.controller;

import com.railsafe.dto.GateResponse;
import com.railsafe.entity.Role;
import com.railsafe.live.dto.GateSensorStatusRequest;
import com.railsafe.live.dto.LiveCountsResponse;
import com.railsafe.live.dto.LiveDashboardResponse;
import com.railsafe.live.dto.LiveGateSummaryResponse;
import com.railsafe.live.dto.ProviderStatusResponse;
import com.railsafe.live.service.LiveCountService;
import com.railsafe.live.service.LiveDashboardService;
import com.railsafe.live.service.LiveUpdateBroadcaster;
import com.railsafe.live.service.TrainTrackingService;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.GateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Additive live-dashboard API. All GET endpoints require an authenticated session.
 * The optional sensor webhook is authenticated by the shared secret configured in
 * railsafe.live.sensor-api-key, not by a user session.
 */
@RestController
@RequestMapping("/api/live")
public class LiveController {

    private final LiveDashboardService dashboardService;
    private final TrainTrackingService trainTrackingService;
    private final GateService gateService;
    private final LiveCountService countService;
    private final LiveUpdateBroadcaster liveUpdateBroadcaster;

    public LiveController(LiveDashboardService dashboardService,
                          TrainTrackingService trainTrackingService,
                          GateService gateService,
                          LiveCountService countService,
                          LiveUpdateBroadcaster liveUpdateBroadcaster) {
        this.dashboardService = dashboardService;
        this.trainTrackingService = trainTrackingService;
        this.gateService = gateService;
        this.countService = countService;
        this.liveUpdateBroadcaster = liveUpdateBroadcaster;
    }

    /** Database-derived dashboard counts. Staff automatically receive assigned-gate scope. */
    @GetMapping("/counts")
    public LiveCountsResponse counts(@AuthenticationPrincipal AppUserPrincipal principal) {
        return countService.counts(principal);
    }

    /** Server-Sent Events stream for automatic dashboard refresh without polling. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AppUserPrincipal principal) {
        Long staffUserId = principal != null && principal.getRole() == Role.STAFF
                ? principal.getId() : null;
        return liveUpdateBroadcaster.subscribe(staffUserId);
    }

    @GetMapping("/gates")
    public List<LiveGateSummaryResponse> gates(@AuthenticationPrincipal AppUserPrincipal principal) {
        return dashboardService.listGates(principal);
    }

    @GetMapping("/dashboard")
    public LiveDashboardResponse dashboard(@RequestParam(required = false) Long gateId,
                                           @AuthenticationPrincipal AppUserPrincipal principal) {
        return dashboardService.dashboard(gateId, principal);
    }

    @GetMapping("/provider")
    public ProviderStatusResponse provider() {
        return trainTrackingService.providerStatus();
    }

    /**
     * Optional authorised gate-sensor webhook.
     * Example: POST /api/live/sensor/gates/1/status with X-API-Key.
     */
    @PostMapping("/sensor/gates/{id}/status")
    @ResponseStatus(HttpStatus.OK)
    public GateResponse sensorStatus(@PathVariable Long id,
                                     @RequestBody GateSensorStatusRequest request,
                                     @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        return gateService.ingestSensorStatus(id, request, apiKey);
    }

    @GetMapping("/safety")
    public Map<String, Object> safety() {
        return Map.of(
                "liveDataOnlyFromAuthorisedProvider", true,
                "simulationRequiresTwoOptInFlags", true,
                "simulationNeverPersistedAsLive", true,
                "countdownsAreEstimates", true
        );
    }
}
