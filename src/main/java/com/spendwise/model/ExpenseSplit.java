package com.spendwise.model;

import java.math.BigDecimal;

public class ExpenseSplit {
    private Long id;
    private Long expenseId;
    private Long memberId;
    private String memberName;
    private BigDecimal shareAmount;

    public ExpenseSplit() {}

    public ExpenseSplit(Long id, Long expenseId, Long memberId, BigDecimal shareAmount) {
        this.id = id;
        this.expenseId = expenseId;
        this.memberId = memberId;
        this.shareAmount = shareAmount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(Long expenseId) {
        this.expenseId = expenseId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }

    public void setShareAmount(BigDecimal shareAmount) {
        this.shareAmount = shareAmount;
    }
}
