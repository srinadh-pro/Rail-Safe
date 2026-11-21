package com.railsafe.service;

import com.railsafe.dto.GateResponse;
import com.railsafe.dto.StatusHistoryResponse;
import com.railsafe.live.dto.LiveCountsResponse;
import com.railsafe.live.service.LiveCountService;
import com.railsafe.repository.StaffAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Staff-only view limited to actively assigned gates. */
@Service
public class StaffDashboardService {

    private final StaffAssignmentRepository assignmentRepository;
    private final GateMapper gateMapper;
    private final LiveCountService countService;
    private final GateService gateService;

    public StaffDashboardService(StaffAssignmentRepository assignmentRepository,
                                 GateMapper gateMapper,
                                 LiveCountService countService,
                                 GateService gateService) {
        this.assignmentRepository = assignmentRepository;
        this.gateMapper = gateMapper;
        this.countService = countService;
        this.gateService = gateService;
    }

    @Transactional(readOnly = true)
    public List<GateResponse> gates(Long staffUserId) {
        return assignmentRepository.findByUserIdAndActiveTrueOrderByGateNameAsc(staffUserId).stream()
                .map(assignment -> gateMapper.toResponse(assignment.getGate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public LiveCountsResponse counts(Long staffUserId) {
        return countService.counts(staffUserId);
    }

    /** A STAFF member can see only status changes they personally recorded. */
    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> activity(Long staffUserId) {
        return gateService.historyForStaff(staffUserId);
    }
}
