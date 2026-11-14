package com.spendwise.repository;

import com.spendwise.model.GroupSettlement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class GroupSettlementRepository {
    private final JdbcTemplate jdbcTemplate;

    public GroupSettlementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GroupSettlement> findByGroupId(Long groupId) {
        return jdbcTemplate.query("SELECT s.id, s.group_id, s.from_member_id, fm.display_name AS from_name, " +
                        "s.to_member_id, tm.display_name AS to_name, s.amount, s.status, s.settled_date, s.notes " +
                        "FROM settlements s JOIN group_members fm ON fm.id = s.from_member_id " +
                        "JOIN group_members tm ON tm.id = s.to_member_id WHERE s.group_id = ? ORDER BY s.id DESC",
                (rs, rowNum) -> {
                    GroupSettlement settlement = new GroupSettlement();
                    settlement.setId(rs.getLong("id"));
                    settlement.setGroupId(rs.getLong("group_id"));
                    settlement.setFromMemberId(rs.getLong("from_member_id"));
                    settlement.setFromMemberName(rs.getString("from_name"));
                    settlement.setToMemberId(rs.getLong("to_member_id"));
                    settlement.setToMemberName(rs.getString("to_name"));
                    settlement.setAmount(rs.getBigDecimal("amount"));
                    settlement.setStatus(rs.getString("status"));
                    Date date = rs.getDate("settled_date");
                    if (date != null) settlement.setSettledDate(date.toLocalDate());
                    settlement.setNotes(rs.getString("notes"));
                    return settlement;
                }, groupId);
    }

    public Long createPending(Long groupId, Long fromMemberId, Long toMemberId, java.math.BigDecimal amount) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO settlements (group_id, from_member_id, to_member_id, amount, settled_date, status) " +
                            "VALUES (?, ?, ?, ?, ?, 'PENDING')", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, groupId);
            statement.setLong(2, fromMemberId);
            statement.setLong(3, toMemberId);
            statement.setBigDecimal(4, amount);
            // Legacy databases may still require this column for pending rows.
            statement.setDate(5, Date.valueOf(LocalDate.now()));
            return statement;
        }, keyHolder);
        return KeyHolderUtil.extractId(keyHolder);
    }

    public Optional<GroupSettlement> findByIdAndGroup(Long id, Long groupId) {
        return findByGroupId(groupId).stream().filter(settlement -> settlement.getId().equals(id)).findFirst();
    }

    public int complete(Long id, Long groupId) {
        return jdbcTemplate.update("UPDATE settlements SET status = 'COMPLETED', settled_date = ? " +
                "WHERE id = ? AND group_id = ? AND status = 'PENDING'", Date.valueOf(LocalDate.now()), id, groupId);
    }

    public int cancel(Long id, Long groupId) {
        return jdbcTemplate.update("DELETE FROM settlements WHERE id = ? AND group_id = ? AND status = 'PENDING'", id, groupId);
    }

    public int cancelPendingForGroup(Long groupId) {
        return jdbcTemplate.update("DELETE FROM settlements WHERE group_id = ? AND status = 'PENDING'", groupId);
    }

    public java.util.Map<Long, java.math.BigDecimal> getCompletedSettlementNet(Long groupId) {
        java.util.Map<Long, java.math.BigDecimal> net = new java.util.HashMap<>();
        jdbcTemplate.query("SELECT from_member_id, to_member_id, amount FROM settlements WHERE group_id = ? AND status = 'COMPLETED'",
                rs -> {
                    Long from = rs.getLong("from_member_id");
                    Long to = rs.getLong("to_member_id");
                    java.math.BigDecimal amount = rs.getBigDecimal("amount");
                    net.merge(from, amount, (left, right) -> left.add(right));
                    net.merge(to, amount.negate(), (left, right) -> left.add(right));
                }, groupId);
        return net;
    }
}
