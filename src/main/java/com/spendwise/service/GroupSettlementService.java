package com.spendwise.service;

import com.spendwise.exception.BadRequestException;
import com.spendwise.exception.ResourceNotFoundException;
import com.spendwise.model.GroupMember;
import com.spendwise.model.GroupSettlement;
import com.spendwise.model.SettlementSuggestion;
import com.spendwise.repository.GroupMemberRepository;
import com.spendwise.repository.GroupSettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupSettlementService {
    private final GroupSettlementRepository settlementRepository;
    private final GroupMemberRepository memberRepository;
    private final ExpenseGroupService groupService;
    private final BalanceCalculationService balanceService;
    private final UserService userService;

    public GroupSettlementService(GroupSettlementRepository settlementRepository, GroupMemberRepository memberRepository,
                                  ExpenseGroupService groupService, BalanceCalculationService balanceService,
                                  UserService userService) {
        this.settlementRepository = settlementRepository;
        this.memberRepository = memberRepository;
        this.groupService = groupService;
        this.balanceService = balanceService;
        this.userService = userService;
    }

    public List<GroupSettlement> list(String guestIdentifier, Long groupId) {
        groupService.getGroupById(guestIdentifier, groupId);
        return settlementRepository.findByGroupId(groupId);
    }

    @Transactional
    public List<GroupSettlement> createPendingPlan(String guestIdentifier, Long groupId) {
        groupService.getGroupById(guestIdentifier, groupId);
        List<SettlementSuggestion> plan = balanceService.calculateSettlementSuggestions(groupId);
        // Pending rows represent the current proposal, so replacing them prevents stale payment instructions.
        settlementRepository.cancelPendingForGroup(groupId);
        for (SettlementSuggestion suggestion : plan) {
            settlementRepository.createPending(groupId, suggestion.getFromMemberId(), suggestion.getToMemberId(), suggestion.getAmount());
        }
        return settlementRepository.findByGroupId(groupId);
    }

    @Transactional
    public GroupSettlement complete(String guestIdentifier, Long groupId, Long settlementId) {
        var user = userService.getUserByGuestIdentifier(guestIdentifier);
        groupService.getGroupById(guestIdentifier, groupId);
        GroupSettlement settlement = settlementRepository.findByIdAndGroup(settlementId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found"));
        GroupMember currentMember = memberRepository.findByGroupIdAndUserId(groupId, user.getId())
                .orElseThrow(() -> new BadRequestException("You are not a member of this group"));
        if (!currentMember.getId().equals(settlement.getFromMemberId()) && !currentMember.getId().equals(settlement.getToMemberId())) {
            throw new BadRequestException("Only the sender or recipient can confirm this settlement");
        }
        if (!"PENDING".equals(settlement.getStatus())) throw new BadRequestException("Settlement is already completed");
        settlementRepository.complete(settlementId, groupId);
        return settlementRepository.findByIdAndGroup(settlementId, groupId).orElseThrow();
    }

    @Transactional
    public void cancel(String guestIdentifier, Long groupId, Long settlementId) {
        groupService.getGroupById(guestIdentifier, groupId);
        if (settlementRepository.cancel(settlementId, groupId) == 0) {
            throw new BadRequestException("Pending settlement not found");
        }
    }
}
