package com.spendwise.controller;

import com.spendwise.dto.ApiResponse;
import com.spendwise.dto.CreateGroupRequest;
import com.spendwise.dto.JoinGroupRequest;
import com.spendwise.model.ExpenseGroup;
import com.spendwise.model.GroupMember;
import com.spendwise.model.MemberBalance;
import com.spendwise.model.SettlementSuggestion;
import com.spendwise.model.GroupSettlement;
import com.spendwise.service.BalanceCalculationService;
import com.spendwise.service.ExpenseGroupService;
import com.spendwise.service.GroupSettlementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class ExpenseGroupController {

    private final ExpenseGroupService groupService;
    private final BalanceCalculationService balanceService;
    private final GroupSettlementService settlementService;

    public ExpenseGroupController(ExpenseGroupService groupService, BalanceCalculationService balanceService,
                                  GroupSettlementService settlementService) {
        this.groupService = groupService;
        this.balanceService = balanceService;
        this.settlementService = settlementService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseGroup>> createGroup(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @RequestBody CreateGroupRequest request) {
        ExpenseGroup group = groupService.createGroup(guestIdentifier, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Group created successfully", group));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseGroup>>> getUserGroups(
            @RequestHeader("X-Guest-Id") String guestIdentifier) {
        List<ExpenseGroup> groups = groupService.getGroupsForUser(guestIdentifier);
        return ResponseEntity.ok(ApiResponse.ok(groups));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseGroup>> getGroupById(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long id) {
        ExpenseGroup group = groupService.getGroupById(guestIdentifier, id);
        return ResponseEntity.ok(ApiResponse.ok(group));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<GroupMember>>> getGroupMembers(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long id) {
        // Verification happens via getGroupById
        groupService.getGroupById(guestIdentifier, id);
        List<GroupMember> members = groupService.getGroupMembers(id);
        return ResponseEntity.ok(ApiResponse.ok(members));
    }

    @GetMapping("/join/{inviteCode}")
    public ResponseEntity<ApiResponse<ExpenseGroup>> previewGroup(@PathVariable String inviteCode) {
        ExpenseGroup group = groupService.previewGroupByInviteCode(inviteCode);
        return ResponseEntity.ok(ApiResponse.ok("Group invite preview", group));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<ExpenseGroup>> joinGroup(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @RequestBody JoinGroupRequest request) {
        ExpenseGroup group = groupService.joinGroup(guestIdentifier, request.getInviteCode());
        return ResponseEntity.ok(ApiResponse.ok("Joined group successfully", group));
    }

    @GetMapping("/{id}/balances")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGroupBalances(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long id) {
        groupService.getGroupById(guestIdentifier, id); // Verify membership
        List<MemberBalance> balances = balanceService.calculateBalances(id);
        List<SettlementSuggestion> settlements = balanceService.calculateSettlementSuggestions(id);

        Map<String, Object> response = new HashMap<>();
        response.put("balances", balances);
        response.put("settlements", settlements);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}/settlements")
    public ResponseEntity<ApiResponse<List<GroupSettlement>>> getSettlements(
            @RequestHeader("X-Guest-Id") String guestIdentifier, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(settlementService.list(guestIdentifier, id)));
    }

    @PostMapping("/{id}/settlements/suggestions")
    public ResponseEntity<ApiResponse<List<GroupSettlement>>> createSuggestedSettlements(
            @RequestHeader("X-Guest-Id") String guestIdentifier, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Pending settlement plan updated", settlementService.createPendingPlan(guestIdentifier, id)));
    }

    @PostMapping("/{id}/settlements/{settlementId}/complete")
    public ResponseEntity<ApiResponse<GroupSettlement>> completeSettlement(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long id, @PathVariable Long settlementId) {
        return ResponseEntity.ok(ApiResponse.ok("Settlement marked complete", settlementService.complete(guestIdentifier, id, settlementId)));
    }

    @DeleteMapping("/{id}/settlements/{settlementId}")
    public ResponseEntity<ApiResponse<Void>> cancelSettlement(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long id, @PathVariable Long settlementId) {
        settlementService.cancel(guestIdentifier, id, settlementId);
        return ResponseEntity.ok(ApiResponse.ok("Pending settlement cancelled", null));
    }
}
