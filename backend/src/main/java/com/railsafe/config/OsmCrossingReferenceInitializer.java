package com.railsafe.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railsafe.entity.DataClassification;
import com.railsafe.entity.GateStatus;
import com.railsafe.entity.RailwayCrossing;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.StaffAssignment;
import com.railsafe.entity.StatusSource;
import com.railsafe.entity.User;
import com.railsafe.entity.VerificationStatus;
import com.railsafe.repository.RailwayCrossingRepository;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.StaffAssignmentRepository;
import com.railsafe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

/**
 * Seeds static, verified railway crossing reference records from OpenStreetMap.
 *
 * <p>Only the location is verified. The current gate state is deliberately
 * {@link GateStatus#AWAITING_VERIFICATION}: no position, ETA or open/closed state is
 * inferred from OSM. Restricted simulation review rows are never read by this class.</p>
 */
@Component
@Order(4)
public class OsmCrossingReferenceInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OsmCrossingReferenceInitializer.class);
    private static final String RESOURCE = "reference/osm-crossings.json";

    private record OsmCrossing(
            String code,
            String name,
            double latitude,
            double longitude,
            String station,
            String district,
            String state,
            String osmType,
            String osmUrl
    ) { }

    private final RailwayGateRepository gateRepository;
    private final RailwayCrossingRepository crossingRepository;
    private final UserRepository userRepository;
    private final StaffAssignmentRepository assignmentRepository;
    private final ObjectMapper objectMapper;

    public OsmCrossingReferenceInitializer(RailwayGateRepository gateRepository,
                                           RailwayCrossingRepository crossingRepository,
                                           UserRepository userRepository,
                                           StaffAssignmentRepository assignmentRepository,
                                           ObjectMapper objectMapper) {
        this.gateRepository = gateRepository;
        this.crossingRepository = crossingRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        List<OsmCrossing> crossings;
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            crossings = objectMapper.readValue(input, new TypeReference<>() { });
        }

        int gatesCreated = 0;
        int referenceRowsCreated = 0;
        RailwayGate firstGate = null;

        for (OsmCrossing crossing : crossings) {
            RailwayGate gate = gateRepository.findByGateCodeIgnoreCase(crossing.code()).orElse(null);
            if (gate == null) {
                gate = new RailwayGate();
                gate.setGateCode(crossing.code());
                gate.setName(crossing.name());
                gate.setLatitude(crossing.latitude());
                gate.setLongitude(crossing.longitude());
                gate.setLocation(location(crossing));
                gate.setStatus(GateStatus.AWAITING_VERIFICATION);
                gate.setStatusSource(StatusSource.REFERENCE);
                gate.setVerificationStatus(VerificationStatus.UNVERIFIED);
                gate.setDataClassification(DataClassification.VERIFIED_REFERENCE);
                gate.setStation(crossing.station());
                gate.setDistrict(crossing.district());
                gate.setState(crossing.state());
                gate.setNotes("Static OpenStreetMap railway crossing reference ("
                        + crossing.osmType() + "). Current status is not verified. Source: "
                        + crossing.osmUrl());
                gate = gateRepository.save(gate);
                gatesCreated++;
            }
            if (firstGate == null) {
                firstGate = gate;
            }

            if (crossingRepository.findByCrossingCodeIgnoreCase(crossing.code()).isEmpty()) {
                RailwayCrossing reference = new RailwayCrossing();
                reference.setCrossingCode(crossing.code());
                reference.setName(crossing.name());
                reference.setGateId(gate.getId());
                reference.setLatitude(crossing.latitude());
                reference.setLongitude(crossing.longitude());
                reference.setStation(crossing.station());
                reference.setDistrict(crossing.district());
                reference.setState(crossing.state());
                reference.setStatus(GateStatus.AWAITING_VERIFICATION.name());
                reference.setDataClassification(DataClassification.VERIFIED_REFERENCE);
                reference.setSourceKey("osm:node:" + crossing.code().replace("OSM-", ""));
                reference.setNotes("Verified OSM location only; no live status or position is asserted. "
                        + crossing.osmUrl());
                crossingRepository.save(reference);
                referenceRowsCreated++;
            }
        }

        int assignmentsCreated = assignDemoStaff(firstGate);
        log.info("OSM crossing references loaded: {} gates created, {} reference rows created, "
                        + "{} staff assignments created ({} source records).",
                gatesCreated, referenceRowsCreated, assignmentsCreated, crossings.size());
    }

    /** Gives the demo STAFF account a small working scope without creating any fake status. */
    private int assignDemoStaff(RailwayGate firstGate) {
        if (firstGate == null) {
            return 0;
        }
        User staff = userRepository.findByEmailIgnoreCase("staff@railsafe.demo").orElse(null);
        if (staff == null || !assignmentRepository.findByUserIdAndActiveTrueOrderByGateNameAsc(staff.getId()).isEmpty()) {
            return 0;
        }
        User admin = userRepository.findByEmailIgnoreCase("admin@railsafe.demo").orElse(null);
        int created = 0;
        for (OsmCrossing crossing : firstFive()) {
            RailwayGate gate = gateRepository.findByGateCodeIgnoreCase(crossing.code()).orElse(null);
            if (gate == null || assignmentRepository.existsByUserIdAndGateIdAndActiveTrue(staff.getId(), gate.getId())) {
                continue;
            }
            StaffAssignment assignment = assignmentRepository.findByUserIdAndGateId(staff.getId(), gate.getId())
                    .orElseGet(StaffAssignment::new);
            assignment.setUser(staff);
            assignment.setGate(gate);
            assignment.setActive(true);
            assignment.setUnassignedAt(null);
            assignment.setAssignedBy(admin);
            assignmentRepository.save(assignment);
            created++;
        }
        return created;
    }

    private List<OsmCrossing> firstFive() {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            List<OsmCrossing> all = objectMapper.readValue(input, new TypeReference<>() { });
            return all.stream().limit(5).toList();
        } catch (Exception ex) {
            log.warn("Could not read crossing references for staff assignment: {}", ex.getMessage());
            return List.of();
        }
    }

    private static String location(OsmCrossing crossing) {
        return java.util.stream.Stream.of(crossing.station(), crossing.district(), crossing.state())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("Location not available");
    }
}
