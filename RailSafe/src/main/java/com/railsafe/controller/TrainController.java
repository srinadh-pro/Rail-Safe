package com.railsafe.controller;

import com.railsafe.live.dto.TrainDetailResponse;
import com.railsafe.live.dto.TrainReferenceResponse;
import com.railsafe.live.service.TrainCatalogService;
import com.railsafe.live.service.TrainReferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Authenticated read-only train catalogue endpoint.
 *
 * Only authorised train records are returned. Managed test feeds are normalised to
 * unavailable so no test position or ETA can be presented as live railway data.
 */
@RestController
@RequestMapping("/api/trains")
public class TrainController {

    private final TrainCatalogService trainCatalogService;
    private final TrainReferenceService trainReferenceService;

    public TrainController(TrainCatalogService trainCatalogService,
                           TrainReferenceService trainReferenceService) {
        this.trainCatalogService = trainCatalogService;
        this.trainReferenceService = trainReferenceService;
    }

    /** Verified reference catalogue: 30 trains, no simulated live positions. */
    @GetMapping
    public List<TrainReferenceResponse> catalogue() {
        return trainReferenceService.list();
    }

    @GetMapping("/{trainNumber}")
    public TrainDetailResponse detail(@PathVariable String trainNumber) {
        return trainCatalogService.detail(trainNumber);
    }
}
