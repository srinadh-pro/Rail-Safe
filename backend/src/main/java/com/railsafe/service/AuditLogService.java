package com.railsafe.service;

import com.railsafe.dto.AuditLogResponse;
import com.railsafe.entity.AuditLog;
import com.railsafe.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Central service for recording and reading sensitive administrative actions. */
@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Records an action. Never pass passwords, tokens or full request bodies as details. */
    @Transactional
    public void log(String actor, String action, String targetType, String targetId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActor(safe(actor, "UNKNOWN"));
        entry.setAction(safe(action, "UNKNOWN_ACTION"));
        entry.setTargetType(trim(targetType, 60));
        entry.setTargetId(trim(targetId, 80));
        entry.setDetails(trim(details, 1000));
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> recent() {
        return repository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(entry -> new AuditLogResponse(
                        entry.getId(),
                        entry.getActor(),
                        entry.getAction(),
                        entry.getTargetType(),
                        entry.getTargetId(),
                        entry.getDetails(),
                        entry.getCreatedAt()))
                .toList();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : trim(value, 160);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
