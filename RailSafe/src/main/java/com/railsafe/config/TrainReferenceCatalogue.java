package com.railsafe.config;

import com.railsafe.entity.DataClassification;
import com.railsafe.entity.TrainMaster;
import com.railsafe.repository.TrainMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Checked Indian Railways reference information for 30 train services.
 *
 * Only stable identifiers, names, termini and published scheduled times are stored.
 * Current station, live position, speed and live ETA are deliberately not populated:
 * this catalogue is VERIFIED_REFERENCE, never live tracking.
 */
@Component
@Order(5)
public class TrainReferenceCatalogue implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TrainReferenceCatalogue.class);
    private static final Instant VERIFIED_AT = Instant.parse("2026-11-18T00:00:00Z");
    private static final String UNAVAILABLE = "Not Available";

    public record ReferenceTrain(
            String number,
            String name,
            String sourceStation,
            String destinationStation,
            String direction,
            String scheduledDeparture,
            String scheduledArrival,
            String referenceSource,
            String referenceUrl
    ) { }

    private static final String RAILYATRI = "https://www.railyatri.in/";
    private static final String WIKIPEDIA = "https://en.wikipedia.org/wiki/";

    /** 30 records; numbers and routes checked against public railway timetable sources. */
    public static final List<ReferenceTrain> TRAINS = List.of(
            new ReferenceTrain("12727", "Godavari Express", "Visakhapatnam Jn (VSKP)", "Hyderabad Deccan (HYB)", "WEST", "17:20", UNAVAILABLE, "RailYatri / Wikipedia", RAILYATRI + "trains/route-12728-godavari-sf-expres"),
            new ReferenceTrain("12728", "Godavari Express", "Hyderabad Deccan (HYB)", "Visakhapatnam Jn (VSKP)", "EAST", "17:05", "05:55", "RailYatri", RAILYATRI + "trains/route-12728-godavari-sf-expres"),
            new ReferenceTrain("12723", "Telangana Express", "Hyderabad Deccan (HYB)", "New Delhi (NDLS)", "NORTH", UNAVAILABLE, UNAVAILABLE, "Indian Railways timetable listings", WIKIPEDIA + "Telangana_Express"),
            new ReferenceTrain("12724", "Telangana Express", "New Delhi (NDLS)", "Hyderabad Deccan (HYB)", "SOUTH", UNAVAILABLE, UNAVAILABLE, "Indian Railways timetable listings", WIKIPEDIA + "Telangana_Express"),
            new ReferenceTrain("12759", "Charminar Express", "Tambaram (TBM)", "Hyderabad Deccan (HYB)", "NORTH", "17:30", "08:10", "Wikipedia / Indian Railways", WIKIPEDIA + "Charminar_Express"),
            new ReferenceTrain("12760", "Charminar Express", "Hyderabad Deccan (HYB)", "Tambaram (TBM)", "SOUTH", "18:00", "08:00", "Wikipedia / Indian Railways", WIKIPEDIA + "Charminar_Express"),
            new ReferenceTrain("12701", "Hussainsagar Express", "Mumbai CSMT (CSMT)", "Hyderabad Deccan (HYB)", "EAST", "21:50", "12:05", "Wikipedia / ConfirmTkt", WIKIPEDIA + "Hussainsagar_Express"),
            new ReferenceTrain("12702", "Hussainsagar Express", "Hyderabad Deccan (HYB)", "Mumbai CSMT (CSMT)", "WEST", "14:55", "04:55", "Wikipedia / RailYatri", WIKIPEDIA + "Hussainsagar_Express"),
            new ReferenceTrain("17015", "Visakha Express", "Bhubaneswar New (BBSN)", "Secunderabad Jn (SC)", "WEST", "08:00", "07:45", "ConfirmTkt / LastBerth", "https://www.confirmtkt.com/train-schedule/17015-Visakha-Express"),
            new ReferenceTrain("17016", "Visakha Express", "Secunderabad Jn (SC)", "Bhubaneswar New (BBSN)", "EAST", UNAVAILABLE, UNAVAILABLE, "Indian Railways timetable listings", WIKIPEDIA + "Visakha_Express"),
            new ReferenceTrain("12709", "Simhapuri Express", "Gudur Jn (GDR)", "Secunderabad Jn (SC)", "WEST", UNAVAILABLE, UNAVAILABLE, "eRail / RailYatri", RAILYATRI + "trains/route-12710-simhapuri-sf-expres"),
            new ReferenceTrain("12710", "Simhapuri Express", "Secunderabad Jn (SC)", "Gudur Jn (GDR)", "EAST", "22:05", "08:55", "eRail / RailYatri", "https://erail.in/train-enquiry/12710"),
            new ReferenceTrain("12713", "Satavahana Express", "Vijayawada Jn (BZA)", "Secunderabad Jn (SC)", "WEST", UNAVAILABLE, UNAVAILABLE, "RailYatri", RAILYATRI + "trains/route-12714-satavahana-sf-expres"),
            new ReferenceTrain("12714", "Satavahana Express", "Secunderabad Jn (SC)", "Vijayawada Jn (BZA)", "EAST", UNAVAILABLE, "22:15", "RailYatri", RAILYATRI + "trains/route-12714-satavahana-sf-expres"),
            new ReferenceTrain("12731", "Tirupati–Secunderabad Superfast Express", "Tirupati (TPTY)", "Secunderabad Jn (SC)", "WEST", "17:00", "07:24", "Wikipedia / RailYatri", WIKIPEDIA + "Tirupati%E2%80%93Secunderabad_Superfast_Express"),
            new ReferenceTrain("12732", "Secunderabad–Tirupati Superfast Express", "Secunderabad Jn (SC)", "Tirupati (TPTY)", "SOUTH", UNAVAILABLE, UNAVAILABLE, "Wikipedia / RailYatri", WIKIPEDIA + "Tirupati%E2%80%93Secunderabad_Superfast_Express"),
            new ReferenceTrain("12733", "Narayanadri Express", "Tirupati (TPTY)", "Lingampalli (LPI)", "WEST", "18:20", "06:45", "RailYatri / ConfirmTkt", RAILYATRI + "trains/route-12733-narayanadri-sf-expres"),
            new ReferenceTrain("12734", "Narayanadri Express", "Lingampalli (LPI)", "Tirupati (TPTY)", "SOUTH", "17:30", UNAVAILABLE, "RailYatri / ConfirmTkt", RAILYATRI + "trains/route-12734-narayanadri-sf-expres"),
            new ReferenceTrain("12739", "Garib Rath Express", "Visakhapatnam Jn (VSKP)", "Secunderabad Jn (SC)", "WEST", "20:35", "08:20", "Wikipedia / RailYatri", WIKIPEDIA + "Visakhapatnam%E2%80%93Secunderabad_Garib_Rath_Express"),
            new ReferenceTrain("12740", "Garib Rath Express", "Secunderabad Jn (SC)", "Visakhapatnam Jn (VSKP)", "EAST", "20:15", UNAVAILABLE, "Wikipedia / TravelKhana", WIKIPEDIA + "Visakhapatnam%E2%80%93Secunderabad_Garib_Rath_Express"),
            new ReferenceTrain("12747", "Palnadu Express", "Guntur Jn (GNT)", "Vikarabad Jn (VKB)", "WEST", "05:45", UNAVAILABLE, "Wikipedia / RailYatri", WIKIPEDIA + "Palnadu_Express"),
            new ReferenceTrain("12748", "Palnadu Express", "Vikarabad Jn (VKB)", "Guntur Jn (GNT)", "EAST", UNAVAILABLE, UNAVAILABLE, "Wikipedia / RailYatri", WIKIPEDIA + "Palnadu_Express"),
            new ReferenceTrain("12705", "Intercity Express", "Guntur Jn (GNT)", "Secunderabad Jn (SC)", "WEST", "15:35", "22:28", "Wikipedia / RailYatri", WIKIPEDIA + "Guntur%E2%80%93Secunderabad_Intercity_Express"),
            new ReferenceTrain("12706", "Intercity Express", "Secunderabad Jn (SC)", "Guntur Jn (GNT)", "EAST", "07:45", UNAVAILABLE, "Wikipedia / RailYatri", WIKIPEDIA + "Guntur%E2%80%93Secunderabad_Intercity_Express"),
            new ReferenceTrain("17057", "Devagiri Express", "Mumbai CSMT (CSMT)", "Lingampalli (LPI)", "EAST", "21:30", UNAVAILABLE, "eRail / RailYatri", "https://erail.in/train-enquiry/17057"),
            new ReferenceTrain("17058", "Devagiri Express", "Lingampalli (LPI)", "Mumbai CSMT (CSMT)", "WEST", "12:25", "07:10", "eRail / RailYatri", RAILYATRI + "trains/route-17058-devagiri-expres"),
            new ReferenceTrain("12761", "Tirupati–Karimnagar Superfast Express", "Tirupati (TPTY)", "Karimnagar (KRNT)", "NORTH", UNAVAILABLE, UNAVAILABLE, "India Rail Info", "https://indiarailinfo.com/train/21225"),
            new ReferenceTrain("12762", "Karimnagar–Tirupati Superfast Express", "Karimnagar (KRNT)", "Tirupati (TPTY)", "SOUTH", UNAVAILABLE, UNAVAILABLE, "India Rail Info", "https://indiarailinfo.com/train/21226"),
            new ReferenceTrain("12785", "Kacheguda–Ashokapuram Superfast Express", "Kacheguda (KCG)", "Ashokapuram (AP)", "SOUTH", "19:05", "09:55", "Wikipedia / RailYatri", WIKIPEDIA + "Kacheguda%E2%80%93Ashokapuram_Express"),
            new ReferenceTrain("12786", "Ashokapuram–Kacheguda Superfast Express", "Ashokapuram (AP)", "Kacheguda (KCG)", "NORTH", "14:55", "05:40", "Wikipedia / eRail", "https://erail.in/train-enquiry/12786")
    );

    private final TrainMasterRepository repository;

    public TrainReferenceCatalogue(TrainMasterRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        int updated = 0;
        for (ReferenceTrain reference : TRAINS) {
            TrainMaster master = repository.findByTrainNumberIgnoreCase(reference.number()).orElse(null);
            boolean isNew = master == null;
            if (isNew) {
                master = new TrainMaster();
                master.setTrainNumber(reference.number());
            }
            master.setTrainName(reference.name());
            master.setDirection(reference.direction());
            master.setZone("South Central Railway");
            master.setSynthetic(false);
            master.setDataClassification(DataClassification.VERIFIED_REFERENCE);
            master.setSourceStation(reference.sourceStation());
            master.setDestinationStation(reference.destinationStation());
            master.setCurrentStation(UNAVAILABLE);
            master.setNextStation(UNAVAILABLE);
            master.setScheduledDeparture(reference.scheduledDeparture());
            master.setScheduledArrival(reference.scheduledArrival());
            master.setEstimatedArrival(null);
            master.setRunningStatus("Scheduled");
            master.setSpeedKmph(null);
            master.setReferenceSource(reference.referenceSource());
            master.setReferenceUrl(reference.referenceUrl());
            master.setLastVerifiedAt(VERIFIED_AT);
            repository.save(master);
            if (isNew) created++; else updated++;
        }
        log.info("Train reference catalogue ready: {} records ({} created, {} updated).",
                TRAINS.size(), created, updated);
    }

    public static List<ReferenceTrain> records() {
        return TRAINS;
    }
}
