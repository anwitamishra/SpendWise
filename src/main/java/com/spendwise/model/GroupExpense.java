package com.spendwise.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GroupExpense {
    private Long id;
    private Long groupId;
    private String description;
    private BigDecimal totalAmount;
    private Long paidBy;
    private String paidByName;
    private String splitType; // "EQUAL" or "CUSTOM"
    private LocalDate expenseDate;
    private String notes;
    private LocalDateTime createdAt;
    private List<ExpenseSplit> splits = new ArrayList<>();
    private List<ExpenseItem> items = new ArrayList<>();
    private List<ExpenseContribution> contributions = new ArrayList<>();

    public GroupExpense() {}

    public GroupExpense(Long id, Long groupId, String description, BigDecimal totalAmount, Long paidBy, String splitType, LocalDate expenseDate, String notes, LocalDateTime createdAt) {
        this.id = id;
        this.groupId = groupId;
        this.description = description;
        this.totalAmount = totalAmount;
        this.paidBy = paidBy;
        this.splitType = splitType;
        this.expenseDate = expenseDate;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

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

    public String getPaidByName() {
        return paidByName;
    }

    public void setPaidByName(String paidByName) {
        this.paidByName = paidByName;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ExpenseSplit> getSplits() {
        return splits;
    }

    public void setSplits(List<ExpenseSplit> splits) {
        this.splits = splits;
    }

    public List<ExpenseItem> getItems() { return items; }
    public void setItems(List<ExpenseItem> items) { this.items = items; }
    public List<ExpenseContribution> getContributions() { return contributions; }
    public void setContributions(List<ExpenseContribution> contributions) { this.contributions = contributions; }
}
