package com.railsafe.live.service;

import com.railsafe.config.LiveProperties;
import com.railsafe.entity.TrainSnapshot;
import com.railsafe.live.model.LiveDataMode;
import com.railsafe.live.model.TrainFeed;
import com.railsafe.live.model.TrainFeedTrain;
import com.railsafe.repository.TrainSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Captures authorised provider responses for audit and troubleshooting.
 *
 * Safety rules:
 *  - simulation feeds are never persisted as if they were live railway data;
 *  - an unavailable feed never overwrites or fabricates a snapshot;
 *  - old snapshots are purged so the demo database stays small.
 */
@Service
public class TrainSnapshotService {

    private final TrainSnapshotRepository repository;
    private final LiveProperties properties;

    private volatile String lastCapturedKey;

    public TrainSnapshotService(TrainSnapshotRepository repository, LiveProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public int capture(TrainFeed feed) {
        if (feed == null || !feed.available() || feed.mode() == LiveDataMode.SIMULATION
                || feed.trains() == null || feed.trains().isEmpty()) {
            return 0;
        }

        String key = feed.provider() + ":" + feed.updatedAt() + ":" + feed.trains().size();
        if (key.equals(lastCapturedKey)) {
            return 0;
        }
        lastCapturedKey = key;

        Instant now = Instant.now();
        List<TrainSnapshot> snapshots = new ArrayList<>();
        for (TrainFeedTrain train : feed.trains()) {
            TrainSnapshot snapshot = new TrainSnapshot();
            snapshot.setProvider(feed.provider());
            snapshot.setDataMode(feed.mode().name());
            snapshot.setTrainNumber(train.trainNumber());
            snapshot.setTrainName(train.trainName());
            snapshot.setDirection(train.direction() == null ? null : train.direction().name());
            snapshot.setLatitude(train.latitude());
            snapshot.setLongitude(train.longitude());
            snapshot.setSpeedKmph(train.speedKmph());
            snapshot.setGateCode(train.gateCode());
            snapshot.setEtaAtGate(train.etaAtGate());
            snapshot.setScheduledArrival(train.scheduledArrival());
            snapshot.setTrainStatus(train.reportedStatus() == null ? null : train.reportedStatus().name());
            snapshot.setObservedAt(train.observedAt());
            snapshot.setRecordedAt(now);
            snapshot.setFeedStale(feed.stale());
            snapshots.add(snapshot);
        }
        repository.saveAll(snapshots);

        long retentionHours = Math.max(1, properties.getSnapshotRetentionHours());
        repository.deleteOlderThan(now.minusSeconds(retentionHours * 3600));
        return snapshots.size();
    }
}
