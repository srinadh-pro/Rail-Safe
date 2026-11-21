package com.railsafe;

import com.railsafe.dto.GateResponse;
import com.railsafe.entity.GateStatus;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.StatusSource;
import com.railsafe.entity.VerificationStatus;
import com.railsafe.service.GateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies safe public status mapping and stale-data handling. */
class GateMapperTest {

    private GateMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GateMapper();
        ReflectionTestUtils.setField(mapper, "staleMinutes", 120L);
    }

    @Test
    void neverExposesRawUnknownToTheUi() {
        RailwayGate gate = gate(GateStatus.UNKNOWN, LocalDateTime.now());
        GateResponse response = mapper.toResponse(gate);
        assertEquals(GateStatus.AWAITING_VERIFICATION, response.status());
        assertEquals(GateStatus.AWAITING_VERIFICATION, response.displayStatus());
    }

    @Test
    void mapsUnverifiedAndStaleRecordsToAwaitingVerification() {
        RailwayGate unverified = gate(GateStatus.CLOSED, LocalDateTime.now());
        unverified.setVerificationStatus(VerificationStatus.UNVERIFIED);
        assertEquals(GateStatus.AWAITING_VERIFICATION, mapper.toResponse(unverified).displayStatus());

        RailwayGate stale = gate(GateStatus.OPEN, LocalDateTime.now().minusMinutes(121));
        GateResponse response = mapper.toResponse(stale);
        assertTrue(response.stale());
        assertEquals(GateStatus.AWAITING_VERIFICATION, response.displayStatus());
    }

    @Test
    void showsFreshVerifiedOperationalStatus() {
        RailwayGate gate = gate(GateStatus.CLOSED, LocalDateTime.now().minusMinutes(2));
        GateResponse response = mapper.toResponse(gate);
        assertFalse(response.stale());
        assertEquals(GateStatus.CLOSED, response.displayStatus());
    }

    private static RailwayGate gate(GateStatus status, LocalDateTime updatedAt) {
        RailwayGate gate = new RailwayGate();
        gate.setId(1L);
        gate.setGateCode("RS-TEST-001");
        gate.setName("Test crossing");
        gate.setLatitude(17.0);
        gate.setLongitude(78.0);
        gate.setLocation("Test location");
        gate.setStatus(status);
        gate.setStatusSource(StatusSource.STAFF);
        gate.setVerificationStatus(VerificationStatus.VERIFIED);
        gate.setLastUpdatedAt(updatedAt);
        return gate;
    }
}
