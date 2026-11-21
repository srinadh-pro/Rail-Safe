package com.railsafe;

import com.railsafe.config.TrainReferenceCatalogue;
import com.railsafe.config.TrainReferenceCatalogue.ReferenceTrain;
import com.railsafe.entity.DataClassification;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the verified static train catalogue and its separation from simulation data. */
class TrainReferenceCatalogueTest {

    @Test
    void containsThirtyUniqueVerifiedReferenceTrains() {
        List<ReferenceTrain> trains = TrainReferenceCatalogue.records();
        assertEquals(30, trains.size());

        Set<String> numbers = trains.stream().map(ReferenceTrain::number).collect(Collectors.toSet());
        assertEquals(30, numbers.size(), "Train numbers must be unique");

        for (ReferenceTrain train : trains) {
            assertNotNull(train.name());
            assertFalse(train.name().isBlank());
            assertNotNull(train.sourceStation());
            assertNotNull(train.destinationStation());
            assertNotNull(train.referenceSource());
            assertTrue(train.referenceUrl().startsWith("https://"), train.referenceUrl());
            assertEquals(DataClassification.VERIFIED_REFERENCE.name(), "VERIFIED_REFERENCE");
        }
    }

    @Test
    void usesCorrectedVerifiedServiceNames() {
        Set<String> names = TrainReferenceCatalogue.records().stream()
                .map(ReferenceTrain::name).collect(Collectors.toSet());
        assertTrue(names.contains("Tirupati–Secunderabad Superfast Express"));
        assertTrue(names.contains("Tirupati–Karimnagar Superfast Express"));
        assertTrue(names.contains("Kacheguda–Ashokapuram Superfast Express"));
    }

    @Test
    void doesNotIncludeRestrictedSimulationTrainNumbers() {
        Set<String> numbers = TrainReferenceCatalogue.records().stream()
                .map(ReferenceTrain::number).collect(Collectors.toSet());
        for (int number = 27004; number <= 27023; number++) {
            assertFalse(numbers.contains(String.valueOf(number)),
                    "Restricted simulation number leaked into verified catalogue: " + number);
        }
    }
}
