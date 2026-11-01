package com.spendwise.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardSummary {
    private BigDecimal totalIncome = BigDecimal.ZERO;
    private BigDecimal totalExpenses = BigDecimal.ZERO;
    private BigDecimal netBalance = BigDecimal.ZERO;
    private List<PersonalTransaction> recentTransactions = new ArrayList<>();
    private Map<String, BigDecimal> categoryExpenses = new HashMap<>();
    private List<MonthlyPoint> monthlyOverview = new ArrayList<>();
    private List<GroupSnippet> activeGroups = new ArrayList<>();

    public DashboardSummary() {}

    public static class MonthlyPoint {
        private String month; // e.g. "Oct 2026"
        private BigDecimal income;
        private BigDecimal expense;

        public MonthlyPoint() {}

        public MonthlyPoint(String month, BigDecimal income, BigDecimal expense) {
            this.month = month;
            this.income = income;
            this.expense = expense;
        }

        public String getMonth() { return month; }
        public void setMonth(String month) { this.month = month; }
        public BigDecimal getIncome() { return income; }
        public void setIncome(BigDecimal income) { this.income = income; }
        public BigDecimal getExpense() { return expense; }
        public void setExpense(BigDecimal expense) { this.expense = expense; }
    }

    public static class GroupSnippet {
        private Long groupId;
        private String groupName;
        private String inviteCode;
        private int memberCount;
        private BigDecimal userBalance; // positive = receives, negative = owes

        public GroupSnippet() {}

        public GroupSnippet(Long groupId, String groupName, String inviteCode, int memberCount, BigDecimal userBalance) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.inviteCode = inviteCode;
            this.memberCount = memberCount;
            this.userBalance = userBalance;
        }

        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public String getGroupName() { return groupName; }
        public void setGroupName(String groupName) { this.groupName = groupName; }
        public String getInviteCode() { return inviteCode; }
        public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
        public int getMemberCount() { return memberCount; }
        public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
        public BigDecimal getUserBalance() { return userBalance; }
        public void setUserBalance(BigDecimal userBalance) { this.userBalance = userBalance; }
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome;
    }

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public void setTotalExpenses(BigDecimal totalExpenses) {
        this.totalExpenses = totalExpenses;
    }

    public BigDecimal getNetBalance() {
        return netBalance;
    }

    public void setNetBalance(BigDecimal netBalance) {
        this.netBalance = netBalance;
    }

    public List<PersonalTransaction> getRecentTransactions() {
        return recentTransactions;
    }

    public void setRecentTransactions(List<PersonalTransaction> recentTransactions) {
        this.recentTransactions = recentTransactions;
    }

    public Map<String, BigDecimal> getCategoryExpenses() {
        return categoryExpenses;
    }

    public void setCategoryExpenses(Map<String, BigDecimal> categoryExpenses) {
        this.categoryExpenses = categoryExpenses;
    }

    public List<MonthlyPoint> getMonthlyOverview() {
        return monthlyOverview;
    }

    public void setMonthlyOverview(List<MonthlyPoint> monthlyOverview) {
        this.monthlyOverview = monthlyOverview;
    }

    public List<GroupSnippet> getActiveGroups() {
        return activeGroups;
    }

    public void setActiveGroups(List<GroupSnippet> activeGroups) {
        this.activeGroups = activeGroups;
    }
}
