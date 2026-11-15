package com.spendwise.dto;

import java.math.BigDecimal;

public class ExpenseContributionRequest {
    private Long memberId;
    private BigDecimal amountPaid;

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }
}
