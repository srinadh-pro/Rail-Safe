package com.railsafe;

import com.railsafe.entity.GateStatus;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.StaffAssignment;
import com.railsafe.entity.StatusSource;
import com.railsafe.live.dto.LiveCountsResponse;
import com.railsafe.live.model.LiveDataMode;
import com.railsafe.live.model.TrainFeed;
import com.railsafe.live.service.LiveCountService;
import com.railsafe.live.service.TrainTrackingService;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.StaffAssignmentRepository;
import com.railsafe.service.GateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Ensures dashboard counts are deduplicated, non-negative and never labelled LIVE for simulation. */
@ExtendWith(MockitoExtension.class)
class LiveCountServiceTest {

    @Mock private RailwayGateRepository gateRepository;
    @Mock private StaffAssignmentRepository assignmentRepository;
    @Mock private GateMapper gateMapper;
    @Mock private TrainTrackingService trainTrackingService;
    @InjectMocks private LiveCountService service;

    @BeforeEach
    void stubMapper() {
        lenient().when(gateMapper.publicStatus(any(RailwayGate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, RailwayGate.class).getStatus());
    }

    @Test
    void deduplicatesGatesExcludesDemoAndReportsUnavailableLiveData() {
        RailwayGate open = gate(1L, GateStatus.OPEN, StatusSource.STAFF);
        RailwayGate closed = gate(2L, GateStatus.CLOSED, StatusSource.STAFF);
        RailwayGate closing = gate(3L, GateStatus.CLOSING, StatusSource.STAFF);
        RailwayGate opening = gate(4L, GateStatus.OPENING, StatusSource.STAFF);
        RailwayGate awaiting = gate(5L, GateStatus.AWAITING_VERIFICATION, StatusSource.STAFF);
        RailwayGate demo = gate(6L, GateStatus.CLOSED, StatusSource.DEMO);

        when(gateRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(open, open, closed, closing, opening, awaiting, demo));
        when(trainTrackingService.currentFeed()).thenReturn(
                TrainFeed.unavailable("Unavailable", "Live train data is unavailable."));

        LiveCountsResponse counts = service.counts((Long) null);

        assertEquals(5, counts.totalGates());
        assertEquals(1, counts.openGates());
        assertEquals(1, counts.closedGates());
        assertEquals(1, counts.closingGates());
        assertEquals(1, counts.openingGates());
        assertEquals(1, counts.awaitingVerificationGates());
        assertEquals(3, counts.activeCrossings());
        assertEquals(0, counts.approachingTrains());
        assertFalse(counts.liveDataAvailable());
        assertTrue(counts.message().contains("unavailable"));
        assertTrue(allCountsNonNegative(counts));
    }

    @Test
    void simulationFeedNeverEnablesTheLiveIndicator() {
        when(gateRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(trainTrackingService.currentFeed()).thenReturn(new TrainFeed(
                true, LiveDataMode.SIMULATION, "Simulation", Instant.now(), "acknowledged", List.of(), false));

        LiveCountsResponse counts = service.counts((Long) null);
        assertFalse(counts.liveDataAvailable());
        assertEquals("UNAVAILABLE", counts.dataMode());
    }

    @Test
    void handlesOneThousandOperationalGates() {
        List<RailwayGate> gates = IntStream.rangeClosed(1, 1000)
                .mapToObj(index -> gate((long) index,
                        GateStatus.values()[index % GateStatus.values().length], StatusSource.STAFF))
                .toList();
        when(gateRepository.findAllByOrderByNameAsc()).thenReturn(gates);
        when(trainTrackingService.currentFeed()).thenReturn(
                TrainFeed.unavailable("Unavailable", "Live train data is unavailable."));

        LiveCountsResponse counts = service.counts((Long) null);

        assertEquals(1000, counts.totalGates());
        assertTrue(allCountsNonNegative(counts));
        assertEquals(counts.closedGates() + counts.closingGates() + counts.openingGates(),
                counts.activeCrossings());
        assertEquals("ALL_GATES", counts.scope());
    }

    @Test
    void staffScopeOnlyCountsAssignedGates() {
        RailwayGate assigned = gate(1L, GateStatus.OPEN, StatusSource.STAFF);
        RailwayGate other = gate(2L, GateStatus.CLOSED, StatusSource.STAFF);
        when(gateRepository.findAllByOrderByNameAsc()).thenReturn(List.of(assigned, other));
        when(trainTrackingService.currentFeed()).thenReturn(
                TrainFeed.unavailable("Unavailable", "Live train data is unavailable."));

        StaffAssignment assignment = new StaffAssignment();
        assignment.setGate(assigned);
        when(assignmentRepository.findByUserIdAndActiveTrueOrderByGateNameAsc(42L))
                .thenReturn(List.of(assignment));

        LiveCountsResponse counts = service.counts(42L);
        assertEquals("ASSIGNED_GATES", counts.scope());
        assertEquals(1, counts.totalGates());
        assertEquals(1, counts.openGates());
        assertEquals(0, counts.closedGates());
    }

    private static RailwayGate gate(Long id, GateStatus status, StatusSource source) {
        RailwayGate gate = new RailwayGate();
        gate.setId(id);
        gate.setGateCode("RS-TEST-" + id);
        gate.setName("Gate " + id);
        gate.setLatitude(17.0);
        gate.setLongitude(78.0);
        gate.setLocation("Test");
        gate.setStatus(status);
        gate.setStatusSource(source);
        return gate;
    }

    private static boolean allCountsNonNegative(LiveCountsResponse counts) {
        return counts.totalGates() >= 0 && counts.openGates() >= 0 && counts.closedGates() >= 0
                && counts.closingGates() >= 0 && counts.openingGates() >= 0
                && counts.maintenanceGates() >= 0 && counts.awaitingVerificationGates() >= 0
                && counts.approachingTrains() >= 0 && counts.activeCrossings() >= 0;
    }
}
