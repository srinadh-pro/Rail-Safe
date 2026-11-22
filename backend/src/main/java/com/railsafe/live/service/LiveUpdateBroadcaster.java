package com.railsafe.live.service;

import com.railsafe.live.event.GateStatusChangedEvent;
import com.railsafe.repository.StaffAssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fan-out for dashboard live updates.
 *
 * The stream only emits database-derived count snapshots and gate-change signals;
 * it never generates simulated status values. STAFF subscribers only receive
 * gate-change events for crossings actively assigned to them.
 */
@Service
public class LiveUpdateBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdateBroadcaster.class);

    private record Subscriber(SseEmitter emitter, Long staffUserId) { }

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final LiveCountService countService;
    private final StaffAssignmentRepository assignmentRepository;

    public LiveUpdateBroadcaster(LiveCountService countService,
                                 StaffAssignmentRepository assignmentRepository) {
        this.countService = countService;
        this.assignmentRepository = assignmentRepository;
    }

    public SseEmitter subscribe(Long staffUserId) {
        // Long-lived dashboard stream; one event per gate change plus a 30s heartbeat.
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber subscriber = new Subscriber(emitter, staffUserId);
        subscribers.add(subscriber);
        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> subscribers.remove(subscriber));
        emitter.onError(ex -> subscribers.remove(subscriber));

        send(subscriber, "counts", countService.counts(staffUserId));
        send(subscriber, "connected", Map.of("message", "Live gate updates connected."));
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGateStatusChanged(GateStatusChangedEvent event) {
        Map<String, Object> payload = Map.of(
                "gateId", event.gateId() == null ? "" : event.gateId(),
                "gateCode", event.gateCode() == null ? "" : event.gateCode(),
                "previousStatus", event.previousStatus() == null ? "" : event.previousStatus(),
                "newStatus", event.newStatus() == null ? "" : event.newStatus(),
                "changedAt", event.changedAt().toString()
        );
        for (Subscriber subscriber : subscribers) {
            if (canSeeGate(subscriber.staffUserId(), event.gateId())) {
                send(subscriber, "gate-status", payload);
            }
            // Counts are already scoped by the subscriber's staff id (or null).
            send(subscriber, "counts", countService.counts(subscriber.staffUserId()));
        }
    }

    @Scheduled(fixedDelayString = "30000")
    public void heartbeat() {
        for (Subscriber subscriber : subscribers) {
            send(subscriber, "heartbeat", Map.of("at", java.time.Instant.now().toString()));
        }
    }

    private boolean canSeeGate(Long staffUserId, Long gateId) {
        if (staffUserId == null || gateId == null) {
            return true;
        }
        return assignmentRepository.existsByUserIdAndGateIdAndActiveTrue(staffUserId, gateId);
    }

    private void send(Subscriber subscriber, String eventName, Object payload) {
        try {
            subscriber.emitter().send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException | IllegalStateException ex) {
            subscribers.remove(subscriber);
            log.debug("Removed closed SSE subscriber: {}", ex.getMessage());
        }
    }
}
