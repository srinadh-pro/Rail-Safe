package com.railsafe.controller;

import com.railsafe.dto.SavedRouteRequest;
import com.railsafe.dto.SavedRouteResponse;
import com.railsafe.security.AppUserPrincipal;
import com.railsafe.service.SavedRouteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class SavedRouteController {

    private final SavedRouteService savedRouteService;

    public SavedRouteController(SavedRouteService savedRouteService) {
        this.savedRouteService = savedRouteService;
    }

    @GetMapping
    public List<SavedRouteResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return savedRouteService.list(principal.getId());
    }

    @PostMapping
    public ResponseEntity<SavedRouteResponse> create(@Valid @RequestBody SavedRouteRequest request,
                                                     @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedRouteService.create(request, principal.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal AppUserPrincipal principal) {
        savedRouteService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
