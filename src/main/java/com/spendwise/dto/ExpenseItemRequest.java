package com.spendwise.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ExpenseItemRequest {
    private String description;
    private BigDecimal amount;
    private List<Long> memberIds = new ArrayList<>();

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public List<Long> getMemberIds() { return memberIds; }
    public void setMemberIds(List<Long> memberIds) { this.memberIds = memberIds; }
}