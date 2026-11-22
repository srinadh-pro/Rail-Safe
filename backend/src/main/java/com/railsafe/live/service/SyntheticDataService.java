package com.railsafe.live.service;

import com.railsafe.config.LiveProperties;
import com.railsafe.entity.GateStatus;
import com.railsafe.entity.GateStatusHistory;
import com.railsafe.entity.Notification;
import com.railsafe.entity.NotificationType;
import com.railsafe.entity.RailwayGate;
import com.railsafe.entity.RouteAlternative;
import com.railsafe.entity.StatusSource;
import com.railsafe.entity.TrainMaster;
import com.railsafe.entity.TrainTrackingRecord;
import com.railsafe.entity.User;
import com.railsafe.entity.VerificationStatus;
import com.railsafe.live.model.LiveTrainStatus;
import com.railsafe.repository.GateStatusHistoryRepository;
import com.railsafe.repository.NotificationRepository;
import com.railsafe.repository.RailwayGateRepository;
import com.railsafe.repository.RouteAlternativeRepository;
import com.railsafe.repository.TrainMasterRepository;
import com.railsafe.repository.TrainTrackingRepository;
import com.railsafe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Seeds the synthetic demonstration dataset.
 *
 * Everything created here is clearly synthetic:
 *  - 15 extra crossings (20 in total with the five existing DEMO crossings);
 *  - 30 train catalogue rows (real Indian Railways names used as sample labels);
 *  - 50 gate/train tracking rows used by the simulation provider;
 *  - 30 gate-status history rows;
 *  - 20 synthetic alternate routes;
 *  - 50 notifications across the demo accounts.
 *
 * The seeder is idempotent: each row is guarded by a stable natural key or dedupe
 * key, so restarting the application never duplicates demo data. It never writes
 * to the authorised train_snapshots audit table.
 */
@Component
@Order(2)
public class SyntheticDataService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SyntheticDataService.class);

    private static final String SYNTHETIC_NOTE =
            "SYNTHETIC DEMO DATA - not live Indian Railways information. Gate-to-train assignment is fictional.";

    private final RailwayGateRepository gateRepository;
    private final TrainMasterRepository trainRepository;
    private final TrainTrackingRepository trackingRepository;
    private final GateStatusHistoryRepository historyRepository;
    private final RouteAlternativeRepository routeRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final LiveProperties properties;

    public SyntheticDataService(RailwayGateRepository gateRepository,
                                TrainMasterRepository trainRepository,
                                TrainTrackingRepository trackingRepository,
                                GateStatusHistoryRepository historyRepository,
                                RouteAlternativeRepository routeRepository,
                                NotificationRepository notificationRepository,
                                UserRepository userRepository,
                                LiveProperties properties) {
        this.gateRepository = gateRepository;
        this.trainRepository = trainRepository;
        this.trackingRepository = trackingRepository;
        this.historyRepository = historyRepository;
        this.routeRepository = routeRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.isSyntheticDataEnabled()) {
            log.info("Synthetic demo dataset is disabled (railsafe.live.synthetic-data-enabled=false).");
            return;
        }

        List<RailwayGate> gates = ensureGates();
        List<TrainMaster> trains = ensureTrains();
        int trackingCreated = ensureTracking(gates, trains);
        int historyCreated = ensureHistory(gates);
        int routesCreated = ensureRoutes(gates);
        int notificationsCreated = ensureNotifications(gates, trains);

        log.info("================================================================");
        log.info(" Synthetic demo dataset ready (SIMULATION MODE - NOT LIVE DATA)");
        log.info("   crossings total  : {}", gateRepository.count());
        log.info("   train catalogue  : {} (new: {})", trainRepository.count(), trains.size());
        log.info("   tracking rows new: {}", trackingCreated);
        log.info("   history rows new : {}", historyCreated);
        log.info("   demo routes new  : {}", routesCreated);
        log.info("   notifications new: {}", notificationsCreated);
        log.info(" Every train position, ETA and countdown is simulated.");
        log.info("================================================================");
    }

    // ------------------------------------------------------------------
    // Crossings
    // ------------------------------------------------------------------

    private List<RailwayGate> ensureGates() {
        List<GateSpec> specs = gateSpecs();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < specs.size(); i++) {
            GateSpec spec = specs.get(i);
            if (gateRepository.existsByGateCodeIgnoreCase(spec.code())) {
                continue;
            }
            RailwayGate gate = new RailwayGate();
            gate.setGateCode(spec.code());
            gate.setName(spec.name());
            gate.setLocation(spec.location());
            gate.setLatitude(spec.latitude());
            gate.setLongitude(spec.longitude());
            gate.setStatus(spec.status());
            gate.setStatusSource(StatusSource.DEMO);
            gate.setVerificationStatus(VerificationStatus.DEMO);
            gate.setLastUpdatedAt(now.minusMinutes(3L * i));
            gate.setNotes(SYNTHETIC_NOTE);
            gateRepository.save(gate);
        }

        return gateRepository.findAllByOrderByNameAsc();
    }

    private List<GateSpec> gateSpecs() {
        return List.of(
                new GateSpec("RS-SYN-101", "Medak Railway Gate", "Medak, Medak district, Telangana (synthetic demo location)",
                        18.0458, 78.2630, GateStatus.OPEN),
                new GateSpec("RS-SYN-102", "Secunderabad Railway Gate", "Secunderabad, Hyderabad, Telangana (synthetic demo location)",
                        17.4399, 78.5015, GateStatus.CLOSED),
                new GateSpec("RS-SYN-103", "Hyderabad Railway Gate", "Hyderabad, Telangana (synthetic demo location)",
                        17.3850, 78.4867, GateStatus.OPEN),
                new GateSpec("RS-SYN-104", "Nizamabad Railway Gate", "Nizamabad, Telangana (synthetic demo location)",
                        18.6725, 78.0941, GateStatus.CLOSED),
                new GateSpec("RS-SYN-105", "Kazipet Railway Gate", "Kazipet, Warangal, Telangana (synthetic demo location)",
                        17.9912, 79.5051, GateStatus.OPEN),
                new GateSpec("RS-SYN-106", "Warangal Railway Gate", "Warangal, Telangana (synthetic demo location)",
                        17.9689, 79.5941, GateStatus.CLOSED),
                new GateSpec("RS-SYN-107", "Lingampally Railway Gate", "Lingampally, Hyderabad, Telangana (synthetic demo location)",
                        17.4934, 78.3170, GateStatus.OPEN),
                new GateSpec("RS-SYN-108", "Begumpet Railway Gate", "Begumpet, Hyderabad, Telangana (synthetic demo location)",
                        17.4436, 78.4675, GateStatus.CLOSED),
                new GateSpec("RS-SYN-109", "Kacheguda Railway Gate", "Kacheguda, Hyderabad, Telangana (synthetic demo location)",
                        17.3866, 78.4983, GateStatus.OPEN),
                new GateSpec("RS-SYN-110", "Malkajgiri Railway Gate", "Malkajgiri, Hyderabad, Telangana (synthetic demo location)",
                        17.4459, 78.5294, GateStatus.CLOSED),
                new GateSpec("RS-SYN-111", "Sanathnagar Railway Gate", "Sanathnagar, Hyderabad, Telangana (synthetic demo location)",
                        17.4561, 78.4407, GateStatus.OPEN),
                new GateSpec("RS-SYN-112", "Bolarum Railway Gate", "Bolarum, Hyderabad, Telangana (synthetic demo location)",
                        17.4723, 78.5101, GateStatus.CLOSED),
                new GateSpec("RS-SYN-113", "Bhongir Railway Gate", "Bhongir, Yadadri Bhuvanagiri, Telangana (synthetic demo location)",
                        17.5150, 78.8864, GateStatus.OPEN),
                new GateSpec("RS-SYN-114", "Jangaon Railway Gate", "Jangaon, Telangana (synthetic demo location)",
                        17.7263, 79.1520, GateStatus.CLOSED),
                new GateSpec("RS-SYN-115", "Nalgonda Railway Gate", "Nalgonda, Telangana (synthetic demo location)",
                        17.0542, 79.2671, GateStatus.OPEN)
        );
    }

    // ------------------------------------------------------------------
    // Train catalogue
    // ------------------------------------------------------------------

    private List<TrainMaster> ensureTrains() {
        for (TrainSpec spec : trainSpecs()) {
            if (trainRepository.existsByTrainNumberIgnoreCase(spec.number())) {
                continue;
            }
            TrainMaster train = new TrainMaster();
            train.setTrainNumber(spec.number());
            train.setTrainName(spec.name());
            train.setDirection(spec.direction());
            train.setZone(spec.zone());
            train.setSynthetic(true);
            trainRepository.save(train);
        }
        return trainRepository.findAllByOrderByTrainNumberAsc();
    }

    /**
     * The first ten services are the explicitly requested sample trains. The
     * remaining twenty are additional real service names used as synthetic labels.
     * None of them is claimed to serve any crossing in this application.
     */
    private List<TrainSpec> trainSpecs() {
        return List.of(
                new TrainSpec("12702", "Hussainsagar Express", "SOUTH", "SCR"),
                new TrainSpec("12701", "Hussainsagar Express", "NORTH", "SCR"),
                new TrainSpec("12760", "Charminar Express", "SOUTH", "SCR"),
                new TrainSpec("12759", "Charminar Express", "NORTH", "SCR"),
                new TrainSpec("12727", "Godavari Express", "EAST", "SCR"),
                new TrainSpec("12728", "Godavari Express", "WEST", "SCR"),
                new TrainSpec("17011", "Intercity Express", "NORTH", "SCR"),
                new TrainSpec("17012", "Intercity Express", "SOUTH", "SCR"),
                new TrainSpec("12713", "Satavahana Express", "EAST", "SCR"),
                new TrainSpec("12714", "Satavahana Express", "WEST", "SCR"),
                new TrainSpec("17015", "Visakha Express", "EAST", "ECoR"),
                new TrainSpec("17016", "Visakha Express", "WEST", "SCR"),
                new TrainSpec("12763", "Padmavathi Express", "SOUTH", "SCR"),
                new TrainSpec("12764", "Padmavathi Express", "NORTH", "SCR"),
                new TrainSpec("17201", "Golconda Express", "EAST", "SCR"),
                new TrainSpec("17202", "Golconda Express", "WEST", "SCR"),
                new TrainSpec("17063", "Ajanta Express", "WEST", "SCR"),
                new TrainSpec("17064", "Ajanta Express", "EAST", "SCR"),
                new TrainSpec("12737", "Gautami Express", "EAST", "SCR"),
                new TrainSpec("12738", "Gautami Express", "WEST", "SCR"),
                new TrainSpec("17639", "Kacheguda Akola Express", "NORTH", "SCR"),
                new TrainSpec("17640", "Akola Kacheguda Express", "SOUTH", "SCR"),
                new TrainSpec("17013", "Hyderabad Pune Express", "WEST", "SCR"),
                new TrainSpec("17014", "Pune Hyderabad Express", "EAST", "SCR"),
                new TrainSpec("12723", "Telangana Express", "NORTH", "SCR"),
                new TrainSpec("12724", "Telangana Express", "SOUTH", "SCR"),
                new TrainSpec("12603", "Hyderabad Chennai Express", "SOUTH", "SCR"),
                new TrainSpec("12604", "Chennai Hyderabad Express", "NORTH", "SCR"),
                new TrainSpec("17229", "Sabari Express", "SOUTH", "SCR"),
                new TrainSpec("17230", "Sabari Express", "NORTH", "SCR")
        );
    }

    // ------------------------------------------------------------------
    // Gate/train tracking assignments
    // ------------------------------------------------------------------

    private int ensureTracking(List<RailwayGate> gates, List<TrainMaster> trains) {
        if (gates.isEmpty() || trains.isEmpty()) {
            return 0;
        }

        // 10 crossings with 3 trains and 10 crossings with 2 trains = 50 records.
        int[] perGate = {3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
        long cycle = Math.max(600L, properties.getSimulationCycleSeconds());
        Instant now = Instant.now();
        int created = 0;

        for (int i = 0; i < gates.size(); i++) {
            RailwayGate gate = gates.get(i);
            int count = perGate[i % perGate.length];
            long base = 90L + ((i * 137L) % 1100L);
            // Some crossings deliberately receive two trains close together so the
            // dashboard can show that the barrier is not reopened between them.
            long spacing = (i % 3 == 0) ? 110L : 260L;

            for (int slot = 0; slot < count; slot++) {
                TrainMaster train = trains.get((i * 7 + slot * 11) % trains.size());
                String dedupeKey = "syn:track:" + gate.getGateCode() + ":" + train.getTrainNumber();
                if (trackingRepository.existsByDedupeKey(dedupeKey)) {
                    continue;
                }

                TrainTrackingRecord record = new TrainTrackingRecord();
                record.setGate(gate);
                record.setTrain(train);
                record.setTrainNumber(train.getTrainNumber());
                record.setDirection(directionFor(i, slot));
                record.setTrainStatus(LiveTrainStatus.APPROACHING);
                record.setDistanceKm(round(0.8 + ((i + slot) % 12) * 0.6, 1));
                record.setSpeedKmph(42.0 + ((i * 7 + slot * 13) % 40));
                record.setSlotOffsetSeconds((base + slot * spacing) % cycle);
                record.setSlotIndex(slot);
                record.setObservedAt(now);
                record.setSynthetic(true);
                record.setDedupeKey(dedupeKey);
                trackingRepository.save(record);
                created++;
            }
        }
        return created;
    }

    private static String directionFor(int gateIndex, int slot) {
        String[] directions = {"NORTH", "SOUTH", "EAST", "WEST"};
        return directions[(gateIndex + slot) % directions.length];
    }

    // ------------------------------------------------------------------
    // Gate status history
    // ------------------------------------------------------------------

    private int ensureHistory(List<RailwayGate> gates) {
        User actor = userRepository.findByEmailIgnoreCase("staff@railsafe.demo").orElse(null);
        LocalDateTime now = LocalDateTime.now();
        int created = 0;

        for (RailwayGate gate : gates) {
            if (historyRepository.countByGateId(gate.getId()) > 0) {
                continue;
            }

            GateStatusHistory closed = new GateStatusHistory();
            closed.setGate(gate);
            closed.setOldStatus(GateStatus.OPEN);
            closed.setNewStatus(GateStatus.CLOSED);
            closed.setSource(StatusSource.DEMO);
            closed.setChangedBy(actor);
            closed.setNote("Synthetic demo event: simulated barrier closed for a demo train. " + SYNTHETIC_NOTE);
            closed.setChangedAt(now.minusMinutes(35));
            historyRepository.save(closed);

            GateStatusHistory current = new GateStatusHistory();
            current.setGate(gate);
            current.setOldStatus(GateStatus.CLOSED);
            current.setNewStatus(gate.getStatus());
            current.setSource(StatusSource.DEMO);
            current.setChangedBy(actor);
            current.setNote("Synthetic demo event: simulated barrier " + gate.getStatus() + ". " + SYNTHETIC_NOTE);
            current.setChangedAt(gate.getLastUpdatedAt() == null ? now.minusMinutes(5) : gate.getLastUpdatedAt());
            historyRepository.save(current);

            created += 2;
        }
        return created;
    }

    // ------------------------------------------------------------------
    // Synthetic alternate routes
    // ------------------------------------------------------------------

    private int ensureRoutes(List<RailwayGate> gates) {
        // At least one synthetic alternative for every crossing, plus a second
        // option for the first ten crossings. 20 gates -> 30 route rows.
        Instant now = Instant.now();
        int created = 0;

        for (int i = 0; i < gates.size(); i++) {
            RailwayGate gate = gates.get(i);
            int options = i < 10 ? 2 : 1;

            for (int option = 0; option < options; option++) {
                String routeKey = "syn:route:" + gate.getGateCode() + ":" + (option + 1);
                if (routeRepository.existsByRouteKey(routeKey)) {
                    continue;
                }

                double sign = option == 0 ? 1.0 : -1.0;
                double destLat = round(gate.getLatitude() + sign * (0.035 + i * 0.002), 5);
                double destLng = round(gate.getLongitude() + sign * (0.055 + i * 0.002), 5);
                double distance = round(3.2 + i * 0.4 + option * 2.1, 1);
                double duration = round(distance / 26.0 * 60.0, 1);
                double extra = round(0.4 + i * 0.1 + option * 1.9, 1);

                RouteAlternative route = new RouteAlternative();
                route.setGate(gate);
                route.setRouteKey(routeKey);
                route.setRouteLabel(option == 0
                        ? "Synthetic diversion via " + gate.getName() + " bypass"
                        : "Synthetic ring-road diversion near " + gate.getName());
                route.setStartLat(gate.getLatitude());
                route.setStartLng(gate.getLongitude());
                route.setDestLat(destLat);
                route.setDestLng(destLng);
                route.setDistanceKm(distance);
                route.setDurationMinutes(duration);
                route.setExtraDistanceKm(extra);
                route.setRecommended(option == 0);
                route.setDistanceFromGateMeters(650.0 + i * 40.0 + option * 300.0);
                route.setNavigationUrl(navigationUrl(gate.getLatitude(), gate.getLongitude(), destLat, destLng));
                route.setGeometryJson(geometryJson(gate, destLat, destLng, sign, i));
                route.setGeneratedAt(now);
                route.setSynthetic(true);
                routeRepository.save(route);
                created++;
            }
        }
        return created;
    }

    private static String navigationUrl(double startLat, double startLng, double destLat, double destLng) {
        return String.format(Locale.ROOT,
                "https://www.google.com/maps/dir/?api=1&origin=%.5f,%.5f&destination=%.5f,%.5f&travelmode=driving",
                startLat, startLng, destLat, destLng);
    }

    private static String geometryJson(RailwayGate gate, double destLat, double destLng,
                                       double sign, int index) {
        double startLat = gate.getLatitude();
        double startLng = gate.getLongitude();
        double midOneLat = round(startLat + sign * (0.012 + index * 0.001), 5);
        double midOneLng = round(startLng + sign * (0.020 + index * 0.001), 5);
        double midTwoLat = round(startLat + sign * (0.028 + index * 0.001), 5);
        double midTwoLng = round(startLng + sign * (0.042 + index * 0.001), 5);

        return String.format(Locale.ROOT,
                "[[%.5f,%.5f],[%.5f,%.5f],[%.5f,%.5f],[%.5f,%.5f]]",
                startLat, startLng, midOneLat, midOneLng, midTwoLat, midTwoLng, destLat, destLng);
    }

    // ------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------

    private int ensureNotifications(List<RailwayGate> gates, List<TrainMaster> trains) {
        List<User> users = demoUsers();
        if (users.isEmpty() || gates.isEmpty() || trains.isEmpty()) {
            return 0;
        }

        NotificationType[] types = {
                NotificationType.TRAIN_APPROACHING,
                NotificationType.GATE_CLOSING_SOON,
                NotificationType.GATE_CLOSED,
                NotificationType.TRAIN_CROSSING,
                NotificationType.TRAIN_PASSED,
                NotificationType.GATE_OPENING_SOON,
                NotificationType.GATE_OPENED,
                NotificationType.ALTERNATIVE_ROUTE
        };

        LocalDateTime now = LocalDateTime.now();
        int created = 0;

        for (int n = 0; n < 50; n++) {
            String dedupeKey = "seed:syn:notif:" + n;
            if (notificationRepository.existsByDedupeKey(dedupeKey)) {
                continue;
            }

            RailwayGate gate = gates.get(n % gates.size());
            TrainMaster train = trains.get(n % trains.size());
            NotificationType type = types[n % types.length];

            Notification notification = new Notification();
            notification.setUser(users.get(n % users.size()));
            notification.setGate(gate);
            notification.setType(type);
            notification.setTitle(titleFor(type, gate));
            notification.setMessage(messageFor(type, gate, train));
            notification.setStatusAtEvent(statusFor(type));
            notification.setRead(n % 3 == 0);
            notification.setDedupeKey(dedupeKey);
            notification.setCreatedAt(now.minusMinutes(50L - n));
            notificationRepository.save(notification);
            created++;
        }
        return created;
    }

    private List<User> demoUsers() {
        List<User> users = new ArrayList<>();
        for (String email : Arrays.asList("admin@railsafe.demo", "staff@railsafe.demo", "user@railsafe.demo")) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(users::add);
        }
        if (users.isEmpty()) {
            users.addAll(userRepository.findAll());
        }
        return users;
    }

    private static String titleFor(NotificationType type, RailwayGate gate) {
        return switch (type) {
            case TRAIN_APPROACHING -> "Demo train approaching " + gate.getName();
            case GATE_CLOSING_SOON -> "Demo gate may close soon at " + gate.getName();
            case GATE_CLOSED -> "Demo gate reported CLOSED at " + gate.getName();
            case TRAIN_CROSSING -> "Demo train crossing " + gate.getName();
            case TRAIN_PASSED -> "Demo train passed " + gate.getName();
            case GATE_OPENING_SOON -> "Demo gate may reopen soon at " + gate.getName();
            case GATE_OPENED -> "Demo gate reported OPEN at " + gate.getName();
            case ALTERNATIVE_ROUTE -> "Demo alternative route near " + gate.getName();
            default -> "Demo RailSafe alert for " + gate.getName();
        };
    }

    private static String messageFor(NotificationType type, RailwayGate gate, TrainMaster train) {
        String trainLabel = train.getTrainName() + " (" + train.getTrainNumber() + ")";
        String base = "SIMULATION MODE - NOT LIVE RAILWAY DATA. ";
        return switch (type) {
            case TRAIN_APPROACHING -> base + trainLabel + " is simulated approaching "
                    + gate.getName() + " (" + gate.getGateCode() + "). Estimate only; obey physical barriers.";
            case GATE_CLOSING_SOON -> base + "The simulated barrier at " + gate.getName()
                    + " is due to close for " + trainLabel + ". Never enter a closed crossing.";
            case GATE_CLOSED -> base + gate.getName() + " is shown CLOSED in the synthetic demo dataset. "
                    + "This is not a live railway signal.";
            case TRAIN_CROSSING -> base + trainLabel + " is simulated crossing " + gate.getName()
                    + ". Do not rely on this alert for safety decisions.";
            case TRAIN_PASSED -> base + trainLabel + " is simulated as having passed "
                    + gate.getName() + ". Verify locally before moving.";
            case GATE_OPENING_SOON -> base + "The simulated barrier at " + gate.getName()
                    + " may reopen soon after " + trainLabel + " clears. Estimate only.";
            case GATE_OPENED -> base + gate.getName() + " is shown OPEN in the synthetic demo dataset. "
                    + "This is not a live railway signal and is not a guarantee that the crossing is safe.";
            case ALTERNATIVE_ROUTE -> base + "Synthetic demo alternative routes are available for "
                    + gate.getName() + ". They are not verified by a routing engine.";
            default -> base + "Synthetic alert for " + gate.getName() + ".";
        };
    }

    private static GateStatus statusFor(NotificationType type) {
        return switch (type) {
            case GATE_CLOSED, GATE_CLOSING_SOON, TRAIN_CROSSING -> GateStatus.CLOSED;
            case GATE_OPENED, GATE_OPENING_SOON, TRAIN_PASSED -> GateStatus.OPEN;
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private record GateSpec(String code,
                            String name,
                            String location,
                            double latitude,
                            double longitude,
                            GateStatus status) {
    }

    private record TrainSpec(String number,
                             String name,
                             String direction,
                             String zone) {
    }
}
