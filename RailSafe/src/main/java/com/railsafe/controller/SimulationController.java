package com.railsafe.controller;

import com.railsafe.dto.SimulationSnapshotResponse;
import com.railsafe.service.SimulationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit demo simulation endpoint. Never use this as an operational data source. */
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/snapshot")
    public SimulationSnapshotResponse snapshot() {
        return simulationService.snapshot();
    }
}
