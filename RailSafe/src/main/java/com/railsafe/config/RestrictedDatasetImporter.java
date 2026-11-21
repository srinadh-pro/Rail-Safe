package com.railsafe.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railsafe.entity.ImportedSourceRow;
import com.railsafe.repository.ImportedSourceRowRepository;
import com.railsafe.service.DemoDataClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Imports the retained external dataset into a dedicated restricted-source table.
 *
 * The import is deliberately isolated: no row written here is inserted into the
 * operational users, gates, trains, notifications or reports tables. Every row is
 * classified RESTRICTED and is visible only through the SUPER_ADMIN review API.
 * Existing rows are never changed or deleted.
 */
@Component
@Order(3)
public class RestrictedDatasetImporter implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RestrictedDatasetImporter.class);
    private static final String REVIEW_STATUS = "REVIEW";

    private final ImportedSourceRowRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${railsafe.restricted-dataset.import-enabled:true}")
    private boolean importEnabled;

    @Value("${railsafe.restricted-dataset.path:data/railsafe-expanded-data.json}")
    private String datasetPath;

    public RestrictedDatasetImporter(ImportedSourceRowRepository repository,
                                     ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!importEnabled) {
            log.info("Restricted dataset import is disabled.");
            return;
        }

        Path path = resolvePath(datasetPath);
        if (!Files.isRegularFile(path)) {
            log.warn("Restricted dataset file was not found at {}. Source review will show existing retained rows only.", path);
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(path.toFile());
        } catch (IOException e) {
            log.warn("Unable to read restricted dataset at {}: {}", path, e.getMessage());
            return;
        }

        if (root == null || !DemoDataClassifier.isRestrictedDatasetMode(text(root, "dataMode"))) {
            log.warn("Restricted dataset was skipped because its root dataMode is not SIMULATION.");
            return;
        }

        int[] refreshed = {0};
        int created = importRows(root, refreshed);
        log.info("Restricted dataset import retained {} new review rows and refreshed {} from {}.",
                created, refreshed[0], path);
    }

    private int importRows(JsonNode root, int[] refreshed) {
        int created = 0;
        created += importSingle(root.path("dashboard"), "dashboard", "Dashboard summary",
                "expanded-dashboard", "Expanded dataset summary", null, refreshed);

        created += importArray(root, "gates", "gates", "Crossing",
                node -> text(node, "gateId"),
                node -> text(node, "gateName"),
                RestrictedDatasetImporter::recordStatus, refreshed);

        created += importArray(root, "trains", "trains", "Train",
                node -> text(node, "trainNumber"),
                node -> text(node, "trainName"),
                RestrictedDatasetImporter::recordStatus, refreshed);

        created += importArray(root, "sensors", "sensors", "Sensor",
                node -> text(node, "sensorId"),
                node -> combine(node, " — ", "sensorType", "gateId"),
                RestrictedDatasetImporter::recordStatus, refreshed);

        created += importArray(root, "predictions", "predictions", "Prediction",
                node -> combine(node, ":", "gateId", "trainNumber"),
                node -> combine(node, " — ", "gateId", "trainNumber"),
                RestrictedDatasetImporter::recordStatus, refreshed);

        created += importArray(root, "countdowns", "countdowns", "Countdown",
                node -> text(node, "gateId"),
                node -> "Countdown for " + firstNonBlank(text(node, "gateId"), "crossing"),
                RestrictedDatasetImporter::recordStatus, refreshed);

        created += importArray(root, "notifications", "notifications", "Notification",
                node -> text(node, "notificationId"),
                node -> text(node, "message"),
                RestrictedDatasetImporter::recordStatus, refreshed);

        created += importArray(root, "alternativeRoutes", "alternativeRoutes", "Route",
                node -> text(node, "routeId"),
                node -> text(node, "routeName"),
                RestrictedDatasetImporter::recordStatus, refreshed);

        created += importArray(root, "users", "users", "Account",
                node -> text(node, "userId"),
                node -> text(node, "name"),
                RestrictedDatasetImporter::recordStatus, refreshed);

        created += importArray(root, "admins", "admins", "Administrator",
                node -> text(node, "adminId"),
                node -> text(node, "name"),
                RestrictedDatasetImporter::recordStatus, refreshed);

        created += importSingle(root.path("superAdmin"), "superAdmin", "Super Admin",
                text(root.path("superAdmin"), "adminId"),
                text(root.path("superAdmin"), "name"),
                null, refreshed);

        created += importSingle(root.path("system"), "system", "System",
                text(root.path("system"), "systemName"),
                text(root.path("system"), "systemName"),
                text(root.path("system"), "systemStatus"), refreshed);

        return created;
    }

    private int importArray(JsonNode root,
                            String fieldName,
                            String category,
                            String recordType,
                            Function<JsonNode, String> sourceKey,
                            Function<JsonNode, String> recordLabel,
                            Function<JsonNode, String> status,
                            int[] refreshed) {
        JsonNode array = root.get(fieldName);
        if (array == null || !array.isArray()) {
            return 0;
        }

        int created = 0;
        int index = 0;
        Map<String, Integer> occurrences = new HashMap<>();
        for (JsonNode node : array) {
            index++;
            String sourceValue = firstNonBlank(sourceKey.apply(node), "row-" + index);
            // Keep every source row even when the dataset repeats a natural key;
            // the duplicate gets a stable numeric suffix in its dedupe key.
            int occurrence = occurrences.merge(sourceValue, 1, Integer::sum);
            String dedupeValue = occurrence == 1 ? sourceValue : sourceValue + "-" + occurrence;
            created += retain(category, recordType, sourceValue, dedupeValue,
                    recordLabel.apply(node), status.apply(node), node, refreshed);
        }
        return created;
    }

    private int importSingle(JsonNode node,
                             String category,
                             String recordType,
                             String sourceKey,
                             String recordLabel,
                             String status,
                             int[] refreshed) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        String sourceValue = firstNonBlank(sourceKey, category);
        return retain(category, recordType, sourceValue, sourceValue,
                recordLabel, status, node, refreshed);
    }

    private int retain(String category,
                       String recordType,
                       String sourceKey,
                       String dedupeSourceKey,
                       String recordLabel,
                       String status,
                       JsonNode node,
                       int[] refreshed) {
        String dedupeKey = DemoDataClassifier.RESTRICTED_IMPORT_PREFIX + category + ":" + dedupeSourceKey;
        String label = firstNonBlank(recordLabel, sourceKey);
        String statusValue = firstNonBlank(status, REVIEW_STATUS);
        String detail = node.toString();

        Optional<ImportedSourceRow> existing = repository.findByDedupeKey(dedupeKey);
        if (existing.isPresent()) {
            ImportedSourceRow row = existing.get();
            // Refresh only retained review rows. Operational tables are never touched.
            if (!Objects.equals(row.getDetail(), detail)
                    || !Objects.equals(row.getRecordLabel(), label)
                    || !Objects.equals(row.getStatus(), statusValue)) {
                row.setCategory(category);
                row.setSourceKey(sourceKey);
                row.setRecordType(recordType);
                row.setRecordLabel(label);
                row.setStatus(statusValue);
                row.setClassification(DemoDataClassifier.RESTRICTED_CLASSIFICATION);
                row.setDetail(detail);
                repository.save(row);
                refreshed[0]++;
            }
            return 0;
        }

        ImportedSourceRow row = new ImportedSourceRow();
        row.setDedupeKey(dedupeKey);
        row.setCategory(category);
        row.setSourceKey(sourceKey);
        row.setRecordType(recordType);
        row.setRecordLabel(label);
        row.setStatus(statusValue);
        row.setClassification(DemoDataClassifier.RESTRICTED_CLASSIFICATION);
        row.setDetail(detail);
        repository.save(row);
        return 1;
    }

    private static String recordStatus(JsonNode node) {
        return firstNonBlank(
                text(node, "status"),
                text(node, "gateStatus"),
                text(node, "sensorStatus"),
                text(node, "predictionStatus"),
                text(node, "trainStatus"),
                text(node, "systemStatus"),
                REVIEW_STATUS
        );
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String combine(JsonNode node, String separator, String... fieldNames) {
        List<String> values = new ArrayList<>();
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (value != null) {
                values.add(value);
            }
        }
        return values.isEmpty() ? null : String.join(separator, values);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Path resolvePath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        return path.normalize();
    }
}
