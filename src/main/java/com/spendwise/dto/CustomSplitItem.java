package com.spendwise.dto;

import java.math.BigDecimal;

public class CustomSplitItem {
    private Long memberId;
    private BigDecimal shareAmount;

    public CustomSplitItem() {}

    public CustomSplitItem(Long memberId, BigDecimal shareAmount) {
        this.memberId = memberId;
        this.shareAmount = shareAmount;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }

    public void setShareAmount(BigDecimal shareAmount) {
        this.shareAmount = shareAmount;
    }
}
