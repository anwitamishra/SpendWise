package com.spendwise.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PersonalTransaction {
    private Long id;
    private Long userId;
    private String title;
    private BigDecimal amount;
    private String transactionType; // "INCOME" or "EXPENSE"
    private String category;
    private String description;
    private LocalDate transactionDate;
    private LocalDateTime createdAt;

    public PersonalTransaction() {}

    public PersonalTransaction(Long id, Long userId, String title, BigDecimal amount, String transactionType, String category, String description, LocalDate transactionDate, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.amount = amount;
        this.transactionType = transactionType;
        this.category = category;
        this.description = description;
        this.transactionDate = transactionDate;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
