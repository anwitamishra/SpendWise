package com.spendwise.controller;

import com.spendwise.dto.ApiResponse;
import com.spendwise.model.ReceiptUpload;
import com.spendwise.service.ReceiptUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/receipts")
public class GroupReceiptController {
    private final ReceiptUploadService receiptUploadService;

    public GroupReceiptController(ReceiptUploadService receiptUploadService) {
        this.receiptUploadService = receiptUploadService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReceiptUpload>>> list(
            @RequestHeader("X-Guest-Id") String guestIdentifier, @PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.ok(receiptUploadService.list(guestIdentifier, groupId)));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ReceiptUpload>> upload(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @PathVariable Long groupId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                "Receipt saved for future expense extraction", receiptUploadService.upload(guestIdentifier, groupId, file)));
    }
}
