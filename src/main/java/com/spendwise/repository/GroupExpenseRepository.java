package com.spendwise.repository;

import com.spendwise.model.GroupExpense;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class GroupExpenseRepository {

    private final JdbcTemplate jdbcTemplate;

    public GroupExpenseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<GroupExpense> expenseRowMapper = (rs, rowNum) -> {
        GroupExpense exp = new GroupExpense();
        exp.setId(rs.getLong("id"));
        exp.setGroupId(rs.getLong("group_id"));
        exp.setDescription(rs.getString("description"));
        exp.setTotalAmount(rs.getBigDecimal("total_amount"));
        exp.setPaidBy(rs.getLong("paid_by"));
        exp.setSplitType(rs.getString("split_type"));
        Date date = rs.getDate("expense_date");
        if (date != null) {
            exp.setExpenseDate(date.toLocalDate());
        }
        exp.setNotes(rs.getString("notes"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            exp.setCreatedAt(ts.toLocalDateTime());
        }
        // If query joined group_members
        try {
            exp.setPaidByName(rs.getString("paid_by_name"));
        } catch (Exception ignored) {}

        return exp;
    };

    public GroupExpense save(GroupExpense expense) {
        String sql = "INSERT INTO group_expenses (group_id, description, total_amount, paid_by, split_type, expense_date, notes) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, expense.getGroupId());
            ps.setString(2, expense.getDescription());
            ps.setBigDecimal(3, expense.getTotalAmount());
            ps.setLong(4, expense.getPaidBy());
            ps.setString(5, expense.getSplitType());
            ps.setDate(6, Date.valueOf(expense.getExpenseDate()));
            ps.setString(7, expense.getNotes());
            return ps;
        }, keyHolder);

        Long generatedId = KeyHolderUtil.extractId(keyHolder);
        if (generatedId != null) {
            expense.setId(generatedId);
        }
        return expense;
    }

    public Optional<GroupExpense> findById(Long id) {
        String sql = "SELECT ge.id, ge.group_id, ge.description, ge.total_amount, ge.paid_by, ge.split_type, " +
                "ge.expense_date, ge.notes, ge.created_at, gm.display_name AS paid_by_name " +
                "FROM group_expenses ge " +
                "JOIN group_members gm ON gm.id = ge.paid_by " +
                "WHERE ge.id = ?";
        try {
            GroupExpense exp = jdbcTemplate.queryForObject(sql, expenseRowMapper, id);
            return Optional.ofNullable(exp);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<GroupExpense> findByGroupId(Long groupId) {
        String sql = "SELECT ge.id, ge.group_id, ge.description, ge.total_amount, ge.paid_by, ge.split_type, " +
                "ge.expense_date, ge.notes, ge.created_at, gm.display_name AS paid_by_name " +
                "FROM group_expenses ge " +
                "JOIN group_members gm ON gm.id = ge.paid_by " +
                "WHERE ge.group_id = ? " +
                "ORDER BY ge.expense_date DESC, ge.id DESC";
        return jdbcTemplate.query(sql, expenseRowMapper, groupId);
    }

    public int update(GroupExpense expense) {
        String sql = "UPDATE group_expenses SET description = ?, total_amount = ?, paid_by = ?, split_type = ?, " +
                "expense_date = ?, notes = ? WHERE id = ? AND group_id = ?";
        return jdbcTemplate.update(sql,
                expense.getDescription(),
                expense.getTotalAmount(),
                expense.getPaidBy(),
                expense.getSplitType(),
                Date.valueOf(expense.getExpenseDate()),
                expense.getNotes(),
                expense.getId(),
                expense.getGroupId()
        );
    }

    public int delete(Long id) {
        String sql = "DELETE FROM group_expenses WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    public BigDecimal getTotalSpent(Long groupId) {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM group_expenses WHERE group_id = ?";
        BigDecimal total = jdbcTemplate.queryForObject(sql, BigDecimal.class, groupId);
        return total != null ? total : BigDecimal.ZERO;
    }
}
