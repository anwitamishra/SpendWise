package com.spendwise.repository;

import com.spendwise.dto.ExpenseContributionRequest;
import com.spendwise.model.ExpenseContribution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ExpenseContributionRepository {
    private final JdbcTemplate jdbcTemplate;

    public ExpenseContributionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceForExpense(Long expenseId, List<ExpenseContributionRequest> contributions) {
        jdbcTemplate.update("DELETE FROM group_expense_contributions WHERE expense_id = ?", expenseId);
        if (contributions == null) return;
        for (ExpenseContributionRequest contribution : contributions) {
            jdbcTemplate.update("INSERT INTO group_expense_contributions (expense_id, member_id, amount_paid) VALUES (?, ?, ?)",
                    expenseId, contribution.getMemberId(), contribution.getAmountPaid());
        }
    }

    public List<ExpenseContribution> findByExpenseId(Long expenseId) {
        return jdbcTemplate.query("SELECT gec.member_id, gm.display_name, gec.amount_paid " +
                        "FROM group_expense_contributions gec JOIN group_members gm ON gm.id = gec.member_id " +
                        "WHERE gec.expense_id = ? ORDER BY gm.display_name",
                (rs, rowNum) -> {
                    ExpenseContribution contribution = new ExpenseContribution();
                    contribution.setMemberId(rs.getLong("member_id"));
                    contribution.setMemberName(rs.getString("display_name"));
                    contribution.setAmountPaid(rs.getBigDecimal("amount_paid"));
                    return contribution;
                }, expenseId);
    }
}
