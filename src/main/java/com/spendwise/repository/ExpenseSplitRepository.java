package com.spendwise.repository;

import com.spendwise.model.ExpenseSplit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ExpenseSplitRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExpenseSplitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final @NonNull RowMapper<ExpenseSplit> splitRowMapper = (rs, rowNum) -> {
        ExpenseSplit split = new ExpenseSplit();
        split.setId(rs.getLong("id"));
        split.setExpenseId(rs.getLong("expense_id"));
        split.setMemberId(rs.getLong("member_id"));
        split.setShareAmount(rs.getBigDecimal("share_amount"));
        try {
            split.setMemberName(rs.getString("member_name"));
        } catch (Exception ignored) {}
        return split;
    };

    public void saveAll(Long expenseId, List<ExpenseSplit> splits) {
        String sql = "INSERT INTO expense_splits (expense_id, member_id, share_amount) VALUES (?, ?, ?)";
        for (ExpenseSplit split : splits) {
            jdbcTemplate.update(sql, expenseId, split.getMemberId(), split.getShareAmount());
        }
    }

    public List<ExpenseSplit> findByExpenseId(Long expenseId) {
        String sql = "SELECT es.id, es.expense_id, es.member_id, es.share_amount, gm.display_name AS member_name " +
                "FROM expense_splits es " +
                "JOIN group_members gm ON gm.id = es.member_id " +
                "WHERE es.expense_id = ? " +
                "ORDER BY gm.display_name ASC";
        return jdbcTemplate.query(sql, splitRowMapper, expenseId);
    }

    public int deleteByExpenseId(Long expenseId) {
        String sql = "DELETE FROM expense_splits WHERE expense_id = ?";
        return jdbcTemplate.update(sql, expenseId);
    }

    public Map<Long, BigDecimal> getTotalPaidByMember(Long groupId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        jdbcTemplate.query("SELECT gec.member_id, SUM(gec.amount_paid) AS total_paid " +
                "FROM group_expense_contributions gec JOIN group_expenses ge ON ge.id = gec.expense_id " +
                "WHERE ge.group_id = ? GROUP BY gec.member_id",
                rs -> { map.put(rs.getLong("member_id"), rs.getBigDecimal("total_paid")); }, groupId);
        jdbcTemplate.query("SELECT ge.paid_by AS member_id, SUM(ge.total_amount) AS total_paid " +
                "FROM group_expenses ge WHERE ge.group_id = ? " +
                "AND NOT EXISTS (SELECT 1 FROM group_expense_contributions gec WHERE gec.expense_id = ge.id) " +
                "GROUP BY ge.paid_by",
                rs -> { map.merge(rs.getLong("member_id"), rs.getBigDecimal("total_paid"), (left, right) -> left.add(right)); }, groupId);
        return map;
    }

    public Map<Long, BigDecimal> getTotalOwedByMember(Long groupId) {
        String sql = "SELECT es.member_id, COALESCE(SUM(es.share_amount), 0) AS total_owed " +
                "FROM expense_splits es " +
                "JOIN group_expenses ge ON ge.id = es.expense_id " +
                "WHERE ge.group_id = ? GROUP BY es.member_id";

        Map<Long, BigDecimal> map = new HashMap<>();
        jdbcTemplate.query(sql, (rs) -> {
            long memberId = rs.getLong("member_id");
            BigDecimal total = rs.getBigDecimal("total_owed");
            map.put(memberId, total != null ? total : BigDecimal.ZERO);
        }, groupId);
        return map;
    }
}
