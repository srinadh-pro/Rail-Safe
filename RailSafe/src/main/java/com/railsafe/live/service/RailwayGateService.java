package com.railsafe.live.service;

import com.railsafe.entity.RailwayGate;
import com.railsafe.live.dto.AlternateRouteListResponse;
import com.railsafe.live.dto.GateTimingResponse;
import com.railsafe.service.GateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only facade that keeps the gate controller thin.
 *
 * It reuses the existing gate lookup (GateService.requireGate) so validation and
 * "not found" behaviour stay identical to the rest of the application.
 */
@Service
public class RailwayGateService {

    private final GateService gateService;
    private final GateTimingService gateTimingService;
    private final AlternateRouteService alternateRouteService;

    public RailwayGateService(GateService gateService,
                              GateTimingService gateTimingService,
                              AlternateRouteService alternateRouteService) {
        this.gateService = gateService;
        this.gateTimingService = gateTimingService;
        this.alternateRouteService = alternateRouteService;
    }

    @Transactional(readOnly = true)
    public GateTimingResponse timing(Long gateId) {
        RailwayGate gate = gateService.requireGate(gateId);
        return gateTimingService.timing(gate);
    }

    @Transactional(readOnly = true)
    public AlternateRouteListResponse alternateRoutes(Long gateId) {
        RailwayGate gate = gateService.requireGate(gateId);
        return alternateRouteService.forGate(gate);
    }
}
