package com.spendwise.model;

import java.math.BigDecimal;

public class MemberBalance {
    private Long memberId;
    private Long userId;
    private String displayName;
    private BigDecimal totalPaid;
    private BigDecimal totalOwed;
    private BigDecimal netBalance;
    private String status; // "RECEIVES", "OWES", "SETTLED"

    public MemberBalance() {}

    public MemberBalance(Long memberId, Long userId, String displayName, BigDecimal totalPaid, BigDecimal totalOwed, BigDecimal netBalance, String status) {
        this.memberId = memberId;
        this.userId = userId;
        this.displayName = displayName;
        this.totalPaid = totalPaid;
        this.totalOwed = totalOwed;
        this.netBalance = netBalance;
        this.status = status;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

    public void setTotalPaid(BigDecimal totalPaid) {
        this.totalPaid = totalPaid;
    }

    public BigDecimal getTotalOwed() {
        return totalOwed;
    }

    public void setTotalOwed(BigDecimal totalOwed) {
        this.totalOwed = totalOwed;
    }

    public BigDecimal getNetBalance() {
        return netBalance;
    }

    public void setNetBalance(BigDecimal netBalance) {
        this.netBalance = netBalance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
