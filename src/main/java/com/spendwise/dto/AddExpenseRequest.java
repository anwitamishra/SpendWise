package com.spendwise.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AddExpenseRequest {
    private String description;
    private BigDecimal totalAmount;
    private Long paidBy; // memberId
    private String splitType; // "EQUAL" or "CUSTOM"
    private LocalDate expenseDate;
    private String notes;
    private List<Long> participantIds = new ArrayList<>();
    private List<CustomSplitItem> customSplits = new ArrayList<>();

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Long getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(Long paidBy) {
        this.paidBy = paidBy;
    }

    public String getSplitType() {
        return splitType;
    }

    public void setSplitType(String splitType) {
        this.splitType = splitType;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<Long> getParticipantIds() {
        return participantIds;
    }

    public void setParticipantIds(List<Long> participantIds) {
        this.participantIds = participantIds;
    }

    public List<CustomSplitItem> getCustomSplits() {
        return customSplits;
    }

    public void setCustomSplits(List<CustomSplitItem> customSplits) {
        this.customSplits = customSplits;
    }
}
