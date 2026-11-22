package com.railsafe.service;

import com.railsafe.entity.GateStatusHistory;
import com.railsafe.entity.ImportedSourceRow;
import com.railsafe.entity.Notification;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.RouteAlternative;
import com.railsafe.entity.SavedRoute;
import com.railsafe.entity.StatusSource;
import com.railsafe.entity.TrainMaster;
import com.railsafe.entity.TrainTrackingRecord;
import com.railsafe.entity.User;
import com.railsafe.entity.UserReport;
import com.railsafe.entity.VerificationStatus;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Single source of truth for deciding whether a database row belongs to the
 * demonstration/testing dataset.
 *
 * Demo rows are never deleted automatically and the schema is unchanged. Every
 * normal-facing service uses these checks to keep demo content out of the user
 * experience; the SUPER_ADMIN demo-data screen is the only place that reads them.
 */
public final class DemoDataClassifier {

    /** Stable prefixes used by the seeded demonstration crossings. */
    private static final String DEMO_GATE_PREFIX = "RS-DEMO-";
    private static final String SYNTHETIC_GATE_PREFIX = "RS-SYN-";

    /** Markers used by the restricted external source dataset. */
    public static final String RESTRICTED_CLASSIFICATION = "RESTRICTED";
    public static final String SIMULATION_DATA_MODE = "SIMULATION";
    public static final String RESTRICTED_IMPORT_PREFIX = "import:expanded:";

    /**
     * Word-boundary marker check. A value such as "Democracy Road" is not a demo
     * marker, while "DEMO DATA", "synthetic sample" and "fictional location" are.
     */
    private static final Pattern DEMO_TEXT = Pattern.compile(
            "(?i)(?:^|[^a-z0-9])(demo|synthetic|sample|dummy|mock|fictional|simulated)(?:[^a-z0-9]|$)");

    private DemoDataClassifier() {
    }

    public static boolean hasDemoText(String value) {
        return value != null && DEMO_TEXT.matcher(value).find();
    }

    /**
     * The expanded retained dataset is accepted only when its root explicitly
     * declares simulation mode. It is never treated as an authorised live feed.
     */
    public static boolean isRestrictedDatasetMode(String dataMode) {
        return dataMode != null && SIMULATION_DATA_MODE.equalsIgnoreCase(dataMode.trim());
    }

    /** Only restricted review rows from the external import are accepted here. */
    public static boolean isRestrictedSourceRow(ImportedSourceRow row) {
        if (row == null || row.getClassification() == null || row.getDedupeKey() == null) {
            return false;
        }
        return RESTRICTED_CLASSIFICATION.equalsIgnoreCase(row.getClassification().trim())
                && row.getDedupeKey().toLowerCase(Locale.ROOT).startsWith(RESTRICTED_IMPORT_PREFIX);
    }

    /** Prevents a normal create/update form from introducing another managed dataset row. */
    public static boolean isDemoCode(String gateCode) {
        if (gateCode == null) {
            return false;
        }
        String code = gateCode.trim().toUpperCase(Locale.ROOT);
        return code.startsWith(DEMO_GATE_PREFIX)
                || code.startsWith(SYNTHETIC_GATE_PREFIX)
                || hasDemoText(code);
    }

    public static boolean isDemoGate(RailwayGate gate) {
        if (gate == null) {
            return false;
        }
        if (gate.getStatusSource() == StatusSource.DEMO
                || gate.getVerificationStatus() == VerificationStatus.DEMO) {
            return true;
        }
        String code = gate.getGateCode() == null ? "" : gate.getGateCode().toUpperCase(Locale.ROOT);
        if (code.startsWith(DEMO_GATE_PREFIX) || code.startsWith(SYNTHETIC_GATE_PREFIX)) {
            return true;
        }
        return hasDemoText(gate.getNotes())
                || hasDemoText(gate.getName())
                || hasDemoText(gate.getLocation());
    }

    public static boolean isDemoTrain(TrainMaster train) {
        return train != null && (train.isSynthetic() || hasDemoText(train.getTrainName()));
    }

    public static boolean isDemoTracking(TrainTrackingRecord tracking) {
        return tracking != null && (tracking.isSynthetic()
                || isDemoGate(tracking.getGate())
                || isDemoTrain(tracking.getTrain())
                || hasDemoText(tracking.getDedupeKey()));
    }

    public static boolean isDemoRouteAlternative(RouteAlternative route) {
        return route != null && (route.isSynthetic()
                || isDemoGate(route.getGate())
                || hasDemoText(route.getRouteKey())
                || hasDemoText(route.getRouteLabel()));
    }

    public static boolean isDemoHistory(GateStatusHistory history) {
        return history != null && (history.getSource() == StatusSource.DEMO
                || isDemoGate(history.getGate())
                || hasDemoText(history.getNote()));
    }

    public static boolean isDemoReport(UserReport report) {
        return report != null && (isDemoGate(report.getGate())
                || hasDemoText(report.getComment())
                || hasDemoText(report.getReviewNote()));
    }

    public static boolean isDemoNotification(Notification notification) {
        if (notification == null) {
            return false;
        }
        String dedupeKey = notification.getDedupeKey();
        if (dedupeKey != null && dedupeKey.toLowerCase(Locale.ROOT).startsWith("seed:")) {
            return true;
        }
        return isDemoGate(notification.getGate())
                || hasDemoText(dedupeKey)
                || hasDemoText(notification.getTitle())
                || hasDemoText(notification.getMessage());
    }

    public static boolean isDemoSavedRoute(SavedRoute route, Set<Long> demoGateIds) {
        if (route == null) {
            return false;
        }
        if (hasDemoText(route.getName())
                || hasDemoText(route.getStartLabel())
                || hasDemoText(route.getDestLabel())) {
            return true;
        }
        return containsAnyId(route.getGateIds(), demoGateIds);
    }

    /**
     * Seed/demo login accounts use the reserved @railsafe.demo domain. A demo token
     * in the local part is also treated as testing data, but ordinary addresses are
     * never hidden.
     */
    public static boolean isDemoUser(User user) {
        if (user == null || user.getEmail() == null) {
            return false;
        }
        String email = user.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email.endsWith("@railsafe.demo")) {
            return true;
        }
        int at = email.indexOf('@');
        String local = at < 0 ? email : email.substring(0, at);
        return hasDemoText(local);
    }

    public static Set<Long> demoGateIds(Collection<RailwayGate> gates) {
        return gates.stream()
                .filter(DemoDataClassifier::isDemoGate)
                .map(RailwayGate::getId)
                .collect(Collectors.toSet());
    }

    private static boolean containsAnyId(String commaSeparatedIds, Set<Long> ids) {
        if (commaSeparatedIds == null || commaSeparatedIds.isBlank() || ids.isEmpty()) {
            return false;
        }
        for (String value : commaSeparatedIds.split(",")) {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                if (ids.contains(Long.valueOf(trimmed))) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed historical values; the persisted format is numeric IDs.
            }
        }
        return false;
    }
}
