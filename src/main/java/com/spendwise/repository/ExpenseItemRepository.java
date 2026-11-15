package com.spendwise.repository;

import com.spendwise.dto.ExpenseItemRequest;
import com.spendwise.model.ExpenseItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ExpenseItemRepository {
    private final JdbcTemplate jdbcTemplate;

    public ExpenseItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceItems(Long expenseId, List<ExpenseItemRequest> items) {
        jdbcTemplate.update("DELETE FROM group_expense_items WHERE expense_id = ?", expenseId);
        if (items == null) return;
        for (ExpenseItemRequest item : items) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO group_expense_items (expense_id, description, amount) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, expenseId);
                statement.setString(2, item.getDescription().trim());
                statement.setBigDecimal(3, item.getAmount());
                return statement;
            }, keyHolder);
            Long itemId = KeyHolderUtil.extractId(keyHolder);
            if (itemId == null) throw new IllegalStateException("Unable to save expense item");
            for (Long memberId : item.getMemberIds()) {
                jdbcTemplate.update("INSERT INTO group_expense_item_members (item_id, member_id) VALUES (?, ?)", itemId, memberId);
            }
        }
    }

    public List<ExpenseItem> findByExpenseId(Long expenseId) {
        List<ExpenseItem> items = jdbcTemplate.query(
                "SELECT id, expense_id, description, amount FROM group_expense_items WHERE expense_id = ? ORDER BY id",
                (rs, rowNum) -> {
                    ExpenseItem item = new ExpenseItem();
                    item.setId(rs.getLong("id"));
                    item.setExpenseId(rs.getLong("expense_id"));
                    item.setDescription(rs.getString("description"));
                    item.setAmount(rs.getBigDecimal("amount"));
                    return item;
                }, expenseId);
        for (ExpenseItem item : items) {
            List<Long> memberIds = jdbcTemplate.query("SELECT member_id FROM group_expense_item_members WHERE item_id = ? ORDER BY member_id",
                    (rs, rowNum) -> rs.getLong("member_id"), item.getId());
            item.setMemberIds(new ArrayList<>(memberIds));
        }
        return items;
    }
}
