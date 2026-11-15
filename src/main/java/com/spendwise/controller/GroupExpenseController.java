package com.spendwise.controller;

import com.spendwise.dto.AddExpenseRequest;
import com.spendwise.dto.ApiResponse;
import com.spendwise.model.GroupExpense;
import com.spendwise.service.GroupExpenseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
public class GroupExpenseController {

    private final GroupExpenseService groupExpenseService;

    public GroupExpenseController(GroupExpenseService groupExpenseService) {
        this.groupExpenseService = groupExpenseService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GroupExpense>> addExpense(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long groupId,
            @RequestBody AddExpenseRequest request) {
        GroupExpense expense = groupExpenseService.addExpense(guestIdentifier, groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Expense added successfully", expense));
    }

    @PostMapping("/smart-split")
    public ResponseEntity<ApiResponse<GroupExpense>> createSmartSplit(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long groupId,
            @RequestBody AddExpenseRequest request) {
        GroupExpense expense = groupExpenseService.addExpense(guestIdentifier, groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Smart Split expense created", expense));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupExpense>>> getExpenses(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long groupId) {
        List<GroupExpense> expenses = groupExpenseService.getGroupExpenses(guestIdentifier, groupId);
        return ResponseEntity.ok(ApiResponse.ok(expenses));
    }

    @GetMapping("/{expenseId}")
    public ResponseEntity<ApiResponse<GroupExpense>> getExpenseById(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long groupId,
            @PathVariable Long expenseId) {
        GroupExpense expense = groupExpenseService.getExpenseById(guestIdentifier, groupId, expenseId);
        return ResponseEntity.ok(ApiResponse.ok(expense));
    }

    @PutMapping("/{expenseId}")
    public ResponseEntity<ApiResponse<GroupExpense>> updateExpense(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long groupId,
            @PathVariable Long expenseId,
            @RequestBody AddExpenseRequest request) {
        GroupExpense updated = groupExpenseService.updateExpense(guestIdentifier, groupId, expenseId, request);
        return ResponseEntity.ok(ApiResponse.ok("Expense updated successfully", updated));
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long groupId,
            @PathVariable Long expenseId) {
        groupExpenseService.deleteExpense(guestIdentifier, groupId, expenseId);
        return ResponseEntity.ok(ApiResponse.ok("Expense deleted successfully", null));
    }
}
