package com.railsafe.controller;

import com.railsafe.dto.GateDetailResponse;
import com.railsafe.dto.GateOverrideRequest;
import com.railsafe.dto.GateRequest;
import com.railsafe.dto.GateResponse;
import com.railsafe.dto.GateVerificationRequest;
import com.railsafe.dto.StatusHistoryResponse;
import com.railsafe.dto.StatusUpdateRequest;
import com.railsafe.entity.GateStatus;
import com.railsafe.live.dto.AlternateRouteListResponse;
import com.railsafe.live.dto.GatePredictionResponse;
import com.railsafe.live.dto.GateTimingResponse;
import com.railsafe.live.dto.GateTrainResponse;
import com.railsafe.live.dto.TrainTrackingResponse;
import com.railsafe.live.service.RailwayGateService;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.GateDetailService;
import com.railsafe.service.GateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gates")
@Validated
public class GateController {

    private final GateService gateService;
    private final RailwayGateService railwayGateService;
    private final GateDetailService gateDetailService;

    public GateController(GateService gateService,
                          RailwayGateService railwayGateService,
                          GateDetailService gateDetailService) {
        this.gateService = gateService;
        this.railwayGateService = railwayGateService;
        this.gateDetailService = gateDetailService;
    }

    /** Read: list gates. STAFF callers only ever receive their assigned crossings. */
    @GetMapping
    public List<GateResponse> list(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) GateStatus status,
                                   @AuthenticationPrincipal AppUserPrincipal principal) {
        return gateService.list(search, status, principal);
    }

    /** Read: nearby gates, sorted by distance. STAFF results are assignment-scoped. */
    @GetMapping("/nearby")
    public List<GateResponse> nearby(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @RequestParam(defaultValue = "5") @DecimalMin("0.1") @DecimalMax("50.0") double radiusKm,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return gateService.nearby(lat, lng, radiusKm, principal);
    }

    @GetMapping("/{id}")
    public GateResponse get(@PathVariable Long id,
                            @AuthenticationPrincipal AppUserPrincipal principal) {
        return gateService.get(id, principal);
    }

    /** Full gate detail with persisted timestamps and safe status labels. */
    @GetMapping("/{id}/detail")
    public GateDetailResponse detail(@PathVariable Long id,
                                     @AuthenticationPrincipal AppUserPrincipal principal) {
        gateService.ensureReadAccess(principal, id);
        return gateDetailService.detail(id);
    }

    /** Same data as GET /{id}, provided as a separate status endpoint for API consumers. */
    @GetMapping("/{id}/status")
    public GateResponse status(@PathVariable Long id,
                               @AuthenticationPrincipal AppUserPrincipal principal) {
        return gateService.get(id, principal);
    }

    /**
     * Flat train list for this gate. Returns an empty array when the provider is
     * unavailable or no train is within the configured window.
     */
    @GetMapping("/{id}/trains")
    public List<GateTrainResponse> trains(@PathVariable Long id,
                                          @AuthenticationPrincipal AppUserPrincipal principal) {
        gateService.ensureReadAccess(principal, id);
        return gateService.trains(id);
    }

    /** Verified status, closing/opening countdowns and next train for this gate. */
    @GetMapping("/{id}/prediction")
    public GatePredictionResponse prediction(@PathVariable Long id,
                                              @AuthenticationPrincipal AppUserPrincipal principal) {
        gateService.ensureReadAccess(principal, id);
        return gateService.prediction(id);
    }

    /**
     * Compact closing/opening countdown payload. The absolute target timestamps are
     * authoritative; the seconds fields are a snapshot at generatedAt.
     */
    @GetMapping("/{id}/timing")
    public GateTimingResponse timing(@PathVariable Long id,
                                     @AuthenticationPrincipal AppUserPrincipal principal) {
        gateService.ensureReadAccess(principal, id);
        return railwayGateService.timing(id);
    }

    /**
     * Verified alternate routes for this crossing. Restricted rows are excluded from
     * normal responses; the verified OSRM endpoint remains POST /api/routes/alternatives.
     */
    @GetMapping("/{id}/alternate-routes")
    public AlternateRouteListResponse alternateRoutes(@PathVariable Long id,
                                                      @AuthenticationPrincipal AppUserPrincipal principal) {
        gateService.ensureReadAccess(principal, id);
        return railwayGateService.alternateRoutes(id);
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(@PathVariable Long id,
                                               @AuthenticationPrincipal AppUserPrincipal principal) {
        return gateService.history(id, principal);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<GateResponse> create(@Valid @RequestBody GateRequest request,
                                               @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gateService.create(request, principal));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public GateResponse update(@PathVariable Long id, @Valid @RequestBody GateRequest request) {
        return gateService.update(id, request);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','SUPER_ADMIN')")
    public GateResponse updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody StatusUpdateRequest request,
                                     @AuthenticationPrincipal AppUserPrincipal principal) {
        return gateService.updateStatus(id, request, principal);
    }

    /**
     * Explicit manual verification / status edit. STAFF are restricted to their
     * assigned gates by the service; ADMIN and SUPER_ADMIN may verify any gate.
     */
    @PutMapping("/{id}/verification")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','SUPER_ADMIN')")
    public GateResponse verify(@PathVariable Long id,
                               @Valid @RequestBody GateVerificationRequest request,
                               @AuthenticationPrincipal AppUserPrincipal principal) {
        return gateService.verifyGate(id, request, principal);
    }

    /**
     * Supervisory override. SUPER_ADMIN only; the reason is written to the gate
     * history and the security audit log.
     */
    @PutMapping("/{id}/override")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public GateResponse override(@PathVariable Long id,
                                 @Valid @RequestBody GateOverrideRequest request,
                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        return gateService.overrideGateStatus(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        gateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
