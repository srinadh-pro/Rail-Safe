package com.railsafe.service;

import com.railsafe.dto.SavedRouteRequest;
import com.railsafe.dto.SavedRouteResponse;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.SavedRoute;
import com.railsafe.entity.User;
import com.railsafe.exception.BadRequestException;
import com.railsafe.exception.ResourceNotFoundException;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.SavedRouteRepository;
import com.railsafe.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SavedRouteService {

    private final SavedRouteRepository routeRepository;
    private final UserRepository userRepository;
    private final RailwayGateRepository gateRepository;

    public SavedRouteService(SavedRouteRepository routeRepository,
                             UserRepository userRepository,
                             RailwayGateRepository gateRepository) {
        this.routeRepository = routeRepository;
        this.userRepository = userRepository;
        this.gateRepository = gateRepository;
    }

    @Transactional(readOnly = true)
    public List<SavedRouteResponse> list(Long userId) {
        Set<Long> demoGateIds = DemoDataClassifier.demoGateIds(gateRepository.findAll());
        return routeRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(route -> !DemoDataClassifier.isDemoSavedRoute(route, demoGateIds))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SavedRouteResponse create(SavedRouteRequest request, Long userId) {
        validateCoordinates(request.startLat(), request.startLng(), "start");
        validateCoordinates(request.destLat(), request.destLng(), "destination");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found."));

        List<Long> requestedIds = request.gateIds() == null ? List.of() : request.gateIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Only keep gate IDs that still exist; this avoids broken links after a gate is deleted.
        List<Long> validIds = requestedIds.isEmpty()
                ? List.of()
                : gateRepository.findAllById(requestedIds).stream()
                .filter(gate -> !DemoDataClassifier.isDemoGate(gate))
                .map(RailwayGate::getId)
                .toList();

        SavedRoute route = new SavedRoute();
        route.setUser(user);
        route.setName(request.name().trim());
        route.setStartLabel(request.startLabel().trim());
        route.setStartLat(request.startLat());
        route.setStartLng(request.startLng());
        route.setDestLabel(request.destLabel().trim());
        route.setDestLat(request.destLat());
        route.setDestLng(request.destLng());
        route.setGateIds(validIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        routeRepository.save(route);

        return toResponse(route);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Set<Long> demoGateIds = DemoDataClassifier.demoGateIds(gateRepository.findAll());
        SavedRoute route = routeRepository.findByIdAndUserId(id, userId)
                .filter(found -> !DemoDataClassifier.isDemoSavedRoute(found, demoGateIds))
                .orElseThrow(() -> new ResourceNotFoundException("Saved route was not found."));
        routeRepository.delete(route);
    }

    private SavedRouteResponse toResponse(SavedRoute route) {
        List<Long> gateIds = route.getGateIds() == null || route.getGateIds().isBlank()
                ? List.of()
                : Arrays.stream(route.getGateIds().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::valueOf)
                .toList();

        return new SavedRouteResponse(
                route.getId(),
                route.getName(),
                route.getStartLabel(),
                route.getStartLat(),
                route.getStartLng(),
                route.getDestLabel(),
                route.getDestLat(),
                route.getDestLng(),
                gateIds,
                route.getCreatedAt()
        );
    }

    private static void validateCoordinates(Double lat, Double lng, String label) {
        if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BadRequestException("Invalid " + label + " coordinates.");
        }
    }
}
