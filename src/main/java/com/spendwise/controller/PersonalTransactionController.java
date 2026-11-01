package com.spendwise.controller;

import com.spendwise.dto.ApiResponse;
import com.spendwise.dto.PersonalTransactionRequest;
import com.spendwise.model.PersonalTransaction;
import com.spendwise.service.PersonalTransactionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class PersonalTransactionController {

    private final PersonalTransactionService transactionService;

    public PersonalTransactionController(PersonalTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PersonalTransaction>> createTransaction(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @RequestBody PersonalTransactionRequest request) {
        PersonalTransaction created = transactionService.createTransaction(guestIdentifier, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Transaction created successfully", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PersonalTransaction>>> getTransactions(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "date") String sortBy,
            @RequestParam(required = false, defaultValue = "DESC") String sortDirection) {

        List<PersonalTransaction> list = transactionService.getTransactions(
                guestIdentifier, type, category, startDate, endDate, search, sortBy, sortDirection);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PersonalTransaction>> getTransactionById(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long id) {
        PersonalTransaction pt = transactionService.getTransactionById(guestIdentifier, id);
        return ResponseEntity.ok(ApiResponse.ok(pt));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PersonalTransaction>> updateTransaction(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long id,
            @RequestBody PersonalTransactionRequest request) {
        PersonalTransaction updated = transactionService.updateTransaction(guestIdentifier, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Transaction updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTransaction(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long id) {
        transactionService.deleteTransaction(guestIdentifier, id);
        return ResponseEntity.ok(ApiResponse.ok("Transaction deleted successfully", null));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Map<String, Object> summary = transactionService.getSummary(guestIdentifier, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}
