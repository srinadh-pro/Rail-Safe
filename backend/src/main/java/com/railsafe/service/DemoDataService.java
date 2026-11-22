package com.railsafe.service;

import com.railsafe.dto.DemoDataRecordResponse;
import com.railsafe.dto.SourceReviewSummaryResponse;
import com.railsafe.entity.GateStatusHistory;
import com.railsafe.entity.ImportedSourceRow;
import com.railsafe.entity.Notification;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.RouteAlternative;
import com.railsafe.entity.SavedRoute;
import com.railsafe.entity.TrainMaster;
import com.railsafe.entity.TrainTrackingRecord;
import com.railsafe.entity.User;
import com.railsafe.entity.UserReport;
import com.railsafe.repository.GateStatusHistoryRepository;
import com.railsafe.repository.ImportedSourceRowRepository;
import com.railsafe.repository.NotificationRepository;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.RouteAlternativeRepository;
import com.railsafe.repository.SavedRouteRepository;
import com.railsafe.repository.TrainMasterRepository;
import com.railsafe.repository.TrainTrackingRepository;
import com.railsafe.repository.UserReportRepository;
import com.railsafe.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only inventory of managed test rows for the SUPER_ADMIN review screen.
 *
 * Nothing here mutates or removes data. The inventory exists so an authorised
 * administrator can see exactly which records are excluded from normal views and
 * where those records are stored.
 */
@Service
public class DemoDataService {

    private final RailwayGateRepository gateRepository;
    private final TrainMasterRepository trainRepository;
    private final TrainTrackingRepository trackingRepository;
    private final RouteAlternativeRepository routeRepository;
    private final GateStatusHistoryRepository historyRepository;
    private final UserReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final SavedRouteRepository savedRouteRepository;
    private final UserRepository userRepository;
    private final ImportedSourceRowRepository importedSourceRowRepository;

    public DemoDataService(RailwayGateRepository gateRepository,
                           TrainMasterRepository trainRepository,
                           TrainTrackingRepository trackingRepository,
                           RouteAlternativeRepository routeRepository,
                           GateStatusHistoryRepository historyRepository,
                           UserReportRepository reportRepository,
                           NotificationRepository notificationRepository,
                           SavedRouteRepository savedRouteRepository,
                           UserRepository userRepository,
                           ImportedSourceRowRepository importedSourceRowRepository) {
        this.gateRepository = gateRepository;
        this.trainRepository = trainRepository;
        this.trackingRepository = trackingRepository;
        this.routeRepository = routeRepository;
        this.historyRepository = historyRepository;
        this.reportRepository = reportRepository;
        this.notificationRepository = notificationRepository;
        this.savedRouteRepository = savedRouteRepository;
        this.userRepository = userRepository;
        this.importedSourceRowRepository = importedSourceRowRepository;
    }

    @Transactional(readOnly = true)
    public List<DemoDataRecordResponse> list() {
        List<RailwayGate> gates = gateRepository.findAllByOrderByNameAsc();
        Set<Long> managedGateIds = DemoDataClassifier.demoGateIds(gates);
        List<DemoDataRecordResponse> rows = new ArrayList<>();

        gates.stream()
                .filter(DemoDataClassifier::isDemoGate)
                .forEach(gate -> rows.add(new DemoDataRecordResponse(
                        "Crossing",
                        gate.getId(),
                        gate.getGateCode(),
                        "railway_gates",
                        gate.getName(),
                        gate.getStatus() == null ? "UNKNOWN" : gate.getStatus().name(),
                        "Location: " + value(gate.getLocation())
                                + " | Source: " + value(gate.getStatusSource())
                                + " | Verification: " + value(gate.getVerificationStatus()),
                        false
                )));

        trainRepository.findAllByOrderByTrainNumberAsc().stream()
                .filter(DemoDataClassifier::isDemoTrain)
                .forEach(train -> rows.add(new DemoDataRecordResponse(
                        "Train",
                        train.getId(),
                        train.getTrainNumber(),
                        "train_master",
                        train.getTrainName(),
                        value(train.getDirection()),
                        "Zone: " + value(train.getZone()),
                        false
                )));

        trackingRepository.findAllWithGateAndTrain().stream()
                .filter(DemoDataClassifier::isDemoTracking)
                .forEach(record -> rows.add(new DemoDataRecordResponse(
                        "Gate assignment",
                        record.getId(),
                        record.getTrainNumber(),
                        "train_tracking",
                        gateLabel(record.getGate()) + " — " + value(record.getTrainNumber()),
                        value(record.getTrainStatus()),
                        "Dedupe key: " + value(record.getDedupeKey()),
                        false
                )));

        routeRepository.findAll().stream()
                .filter(DemoDataClassifier::isDemoRouteAlternative)
                .forEach(route -> rows.add(new DemoDataRecordResponse(
                        "Route",
                        route.getId(),
                        route.getRouteKey(),
                        "route_alternatives",
                        route.getRouteLabel(),
                        route.isSynthetic() ? "UNVERIFIED" : "REVIEW",
                        "Crossing: " + gateLabel(route.getGate())
                                + " | Distance: " + value(route.getDistanceKm()) + " km",
                        false
                )));

        historyRepository.findAll().stream()
                .filter(DemoDataClassifier::isDemoHistory)
                .forEach(history -> rows.add(new DemoDataRecordResponse(
                        "Status history",
                        history.getId(),
                        gateCode(history.getGate()),
                        "gate_status_history",
                        gateLabel(history.getGate()),
                        value(history.getNewStatus()),
                        "Source: " + value(history.getSource())
                                + " | Note: " + value(history.getNote()),
                        false
                )));

        reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(DemoDataClassifier::isDemoReport)
                .forEach(report -> rows.add(new DemoDataRecordResponse(
                        "Report",
                        report.getId(),
                        gateCode(report.getGate()),
                        "user_reports",
                        gateLabel(report.getGate()),
                        value(report.getStatus()),
                        "Reporter: " + value(report.getUser() == null ? null : report.getUser().getEmail())
                                + " | Comment: " + value(report.getComment()),
                        false
                )));

        notificationRepository.findAll().stream()
                .filter(DemoDataClassifier::isDemoNotification)
                .forEach(notification -> rows.add(new DemoDataRecordResponse(
                        "Notification",
                        notification.getId(),
                        notification.getDedupeKey(),
                        "notifications",
                        notification.getTitle(),
                        notification.isRead() ? "READ" : "UNREAD",
                        "Recipient: " + value(notification.getUser() == null ? null : notification.getUser().getEmail()),
                        false
                )));

        savedRouteRepository.findAll().stream()
                .filter(route -> DemoDataClassifier.isDemoSavedRoute(route, managedGateIds))
                .forEach(route -> rows.add(new DemoDataRecordResponse(
                        "Saved route",
                        route.getId(),
                        route.getName(),
                        "saved_routes",
                        value(route.getStartLabel()) + " → " + value(route.getDestLabel()),
                        "REVIEW",
                        "Owner: " + value(route.getUser() == null ? null : route.getUser().getEmail()),
                        false
                )));

        userRepository.findAll().stream()
                .filter(DemoDataClassifier::isDemoUser)
                .forEach(user -> rows.add(new DemoDataRecordResponse(
                        "Account",
                        user.getId(),
                        user.getEmail(),
                        "users",
                        user.getFullName(),
                        value(user.getRole()),
                        user.isEnabled() ? "Enabled" : "Disabled",
                        false
                )));

        importedSourceRowRepository.findAll().stream()
                .filter(DemoDataClassifier::isRestrictedSourceRow)
                .forEach(row -> rows.add(restrictedRow(row)));

        rows.sort(Comparator
                .comparing(DemoDataRecordResponse::type)
                .thenComparing(row -> row.code() == null ? "" : row.code())
                .thenComparing(DemoDataRecordResponse::id,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    /** Counts only restricted review rows. These numbers are never used by operational dashboards. */
    @Transactional(readOnly = true)
    public SourceReviewSummaryResponse sourceReviewSummary() {
        List<ImportedSourceRow> importedRows = importedSourceRowRepository.findAll().stream()
                .filter(DemoDataClassifier::isRestrictedSourceRow)
                .toList();

        Map<String, Integer> categoryCounts = countBy(
                importedRows.stream().map(ImportedSourceRow::getCategory).toList());
        Map<String, Integer> gateStatusCounts = countBy(importedRows.stream()
                .filter(row -> "gates".equals(row.getCategory()))
                .map(ImportedSourceRow::getStatus).toList());
        Map<String, Integer> sensorStatusCounts = countBy(importedRows.stream()
                .filter(row -> "sensors".equals(row.getCategory()))
                .map(ImportedSourceRow::getStatus).toList());

        Instant lastImportedAt = importedRows.stream()
                .map(ImportedSourceRow::getImportedAt)
                .filter(value -> value != null)
                .max(Instant::compareTo)
                .orElse(null);

        return new SourceReviewSummaryResponse(
                list().size(),
                importedRows.size(),
                categoryCounts.getOrDefault("gates", 0),
                categoryCounts.getOrDefault("trains", 0),
                categoryCounts.getOrDefault("sensors", 0),
                categoryCounts,
                gateStatusCounts,
                sensorStatusCounts,
                "SIMULATION",
                lastImportedAt
        );
    }

    private static Map<String, Integer> countBy(List<String> values) {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static DemoDataRecordResponse restrictedRow(ImportedSourceRow row) {
        return new DemoDataRecordResponse(
                value(row.getRecordType()),
                row.getId(),
                value(row.getSourceKey()),
                "imported_source_rows",
                value(row.getRecordLabel()),
                value(row.getStatus()),
                "Category: " + value(row.getCategory())
                        + " | Dedupe key: " + value(row.getDedupeKey())
                        + " | Classification: " + value(row.getClassification())
                        + "\nRecord detail: " + value(row.getDetail()),
                false
        );
    }

    private static String gateCode(RailwayGate gate) {
        return gate == null ? "—" : gate.getGateCode();
    }

    private static String gateLabel(RailwayGate gate) {
        if (gate == null) {
            return "—";
        }
        return gate.getGateCode() + " — " + gate.getName();
    }

    private static String value(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "—" : String.valueOf(value);
    }
}
