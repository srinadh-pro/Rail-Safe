package com.railsafe.controller;

import com.railsafe.entity.GateStatus;
import com.railsafe.entity.NotificationType;
import com.railsafe.entity.RailwayGate;
import com.railsafe.live.dto.AlternativeRouteRequest;
import com.railsafe.live.dto.AlternativeRouteResponse;
import com.railsafe.live.service.RouteCalculationService;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.GateService;
import com.railsafe.service.NotificationService;
import com.railsafe.service.RouteSearchHistoryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Public alternative-route calculation. OSRM supplies every distance and duration;
 * this controller only adds an optional in-app notification for a logged-in user.
 */
@RestController
@RequestMapping("/api/routes/alternatives")
public class AlternativeRouteController {

    private static final Logger log = LoggerFactory.getLogger(AlternativeRouteController.class);

    private final RouteCalculationService routeCalculationService;
    private final GateService gateService;
    private final NotificationService notificationService;
    private final RouteSearchHistoryService routeSearchHistoryService;

    public AlternativeRouteController(RouteCalculationService routeCalculationService,
                                      GateService gateService,
                                      NotificationService notificationService,
                                      RouteSearchHistoryService routeSearchHistoryService) {
        this.routeCalculationService = routeCalculationService;
        this.gateService = gateService;
        this.notificationService = notificationService;
        this.routeSearchHistoryService = routeSearchHistoryService;
    }

    @PostMapping
    public AlternativeRouteResponse calculate(@Valid @RequestBody AlternativeRouteRequest request,
                                              @AuthenticationPrincipal AppUserPrincipal principal) {
        AlternativeRouteResponse response = routeCalculationService.calculate(request);

        try {
            routeSearchHistoryService.record(principal == null ? null : principal.getId(), request, response);
        } catch (RuntimeException ex) {
            log.warn("Could not record route search history: {}", ex.getMessage());
        }

        if (principal != null && response.available() && !response.alternatives().isEmpty()) {
            notifyAlternativeAvailable(principal, request, response);
        }
        return response;
    }

    private void notifyAlternativeAvailable(AppUserPrincipal principal,
                                            AlternativeRouteRequest request,
                                            AlternativeRouteResponse response) {
        try {
            RailwayGate gate = gateService.requireGate(request.gateId());
            if (gate.getStatus() != GateStatus.CLOSED) {
                return;
            }
            String day = LocalDate.now(ZoneId.systemDefault()).toString();
            notificationService.notifyLiveEventForUser(
                    principal.getId(),
                    gate,
                    NotificationType.ALTERNATIVE_ROUTE,
                    "Alternative route available near " + gate.getName(),
                    "A verified alternative route avoiding the closed crossing was calculated: "
                            + response.alternatives().size() + " option(s). Fastest: "
                            + response.alternatives().get(0).distanceKm() + " km, "
                            + response.alternatives().get(0).durationMinutes() + " min. "
                            + "Use an official navigation link and obey all road and railway signals.",
                    "route:alternative:" + gate.getId() + ":" + day,
                    gate.getStatus()
            );
        } catch (RuntimeException ex) {
            // A notification failure must never break route calculation.
            log.warn("Could not create alternative-route notification: {}", ex.getMessage());
        }
    }
}
