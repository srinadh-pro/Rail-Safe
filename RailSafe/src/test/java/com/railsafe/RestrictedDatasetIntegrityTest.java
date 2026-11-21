package com.railsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the restricted, review-only expanded dataset without starting Spring. */
class RestrictedDatasetIntegrityTest {

    private static JsonNode root;

    @BeforeAll
    static void loadDataset() throws Exception {
        File file = new File("data/railsafe-expanded-data.json");
        assertTrue(file.isFile(), "Restricted dataset must exist at " + file.getAbsolutePath());
        root = new ObjectMapper().readTree(file);
    }

    @Test
    void datasetRemainsRestrictedSimulationData() {
        assertEquals("SIMULATION", root.path("dataMode").asText());
        assertEquals(153, root.path("gates").size());
        assertEquals(23, root.path("trains").size());
        assertEquals(153, root.path("sensors").size());
        assertEquals(12, root.path("predictions").size());
        assertEquals(12, root.path("countdowns").size());
    }

    @Test
    void gateAndTrainIdsAreUnique() {
        Set<String> gateIds = new HashSet<>();
        for (JsonNode gate : root.path("gates")) {
            assertTrue(gateIds.add(gate.path("gateId").asText()));
            assertEquals("SIMULATION", gate.path("dataMode").asText());
        }
        assertEquals(153, gateIds.size());

        Set<String> trainIds = new HashSet<>();
        Set<String> trainNumbers = new HashSet<>();
        for (JsonNode train : root.path("trains")) {
            assertTrue(trainIds.add(train.path("trainId").asText()));
            assertTrue(trainNumbers.add(train.path("trainNumber").asText()));
            assertNotNull(train.path("lastUpdated").asText(null));
            assertEquals("RESTRICTED_DATASET", train.path("dataSource").asText());
            assertEquals("SIMULATION", train.path("dataMode").asText());
        }
        assertEquals(23, trainIds.size());
        assertEquals(23, trainNumbers.size());
    }

    @Test
    void everyTrainReferencesAValidRestrictedGate() {
        Set<String> gateIds = new HashSet<>();
        root.path("gates").forEach(gate -> gateIds.add(gate.path("gateId").asText()));

        for (JsonNode train : root.path("trains")) {
            String gateId = train.path("gateId").asText();
            assertTrue(gateIds.contains(gateId), "Invalid gate reference: " + gateId);
            assertTrue(Set.of("NORTH", "SOUTH", "EAST", "WEST").contains(train.path("direction").asText()));
            assertTrue(Set.of("APPROACHING", "NEAR_GATE", "CROSSING", "PASSED")
                    .contains(train.path("status").asText()));
        }
    }

    @Test
    void everyRestrictedGateHasOneSensor() {
        Set<String> gateIds = new HashSet<>();
        root.path("gates").forEach(gate -> gateIds.add(gate.path("gateId").asText()));

        Set<String> sensorIds = new HashSet<>();
        Set<String> sensorGateIds = new HashSet<>();
        for (JsonNode sensor : root.path("sensors")) {
            assertTrue(sensorIds.add(sensor.path("sensorId").asText()));
            String gateId = sensor.path("gateId").asText();
            assertTrue(gateIds.contains(gateId), "Invalid sensor gate reference: " + gateId);
            assertTrue(sensorGateIds.add(gateId), "Duplicate sensor for gate: " + gateId);
            assertTrue(Set.of("ACTIVE", "INACTIVE", "MAINTENANCE")
                    .contains(sensor.path("sensorStatus").asText()));
        }

        assertEquals(153, sensorIds.size());
        assertEquals(gateIds, sensorGateIds);
    }

    @Test
    void countdownsCarryBackendTimestampsForAllThreeCountdowns() {
        boolean hasClosing = false;
        boolean hasOpening = false;

        for (JsonNode countdown : root.path("countdowns")) {
            assertNotNull(countdown.path("trainArrivalAt").asText(null));
            assertTrue(countdown.path("trainArrivalSeconds").asInt(-1) >= 0);
            assertTrue(countdown.has("gateClosingAt"));
            assertTrue(countdown.has("expectedGateOpeningAt"));
            if (!countdown.path("gateClosingAt").isNull()) {
                hasClosing = true;
            }
            if (!countdown.path("expectedGateOpeningAt").isNull()) {
                hasOpening = true;
            }
        }

        assertTrue(hasClosing, "At least one restricted countdown should include gate closing time");
        assertTrue(hasOpening, "At least one restricted countdown should include expected opening time");
    }
}
