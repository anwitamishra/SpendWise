package com.spendwise.repository;

import com.spendwise.model.ExpenseGroup;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class ExpenseGroupRepository {

    private final JdbcTemplate jdbcTemplate;

    public ExpenseGroupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ExpenseGroup> groupRowMapper = (rs, rowNum) -> {
        ExpenseGroup group = new ExpenseGroup();
        group.setId(rs.getLong("id"));
        group.setGroupName(rs.getString("group_name"));
        group.setDescription(rs.getString("description"));
        group.setInviteCode(rs.getString("invite_code"));
        group.setCreatedBy(rs.getLong("created_by"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            group.setCreatedAt(ts.toLocalDateTime());
        }
        return group;
    };

    public ExpenseGroup save(ExpenseGroup group) {
        String sql = "INSERT INTO expense_groups (group_name, description, invite_code, created_by) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, group.getGroupName());
            ps.setString(2, group.getDescription());
            ps.setString(3, group.getInviteCode());
            ps.setLong(4, group.getCreatedBy());
            return ps;
        }, keyHolder);

        Long generatedId = KeyHolderUtil.extractId(keyHolder);
        if (generatedId != null) {
            group.setId(generatedId);
        }
        return group;
    }

    public Optional<ExpenseGroup> findById(Long id) {
        String sql = "SELECT id, group_name, description, invite_code, created_by, created_at FROM expense_groups WHERE id = ?";
        try {
            ExpenseGroup group = jdbcTemplate.queryForObject(sql, groupRowMapper, id);
            return Optional.ofNullable(group);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ExpenseGroup> findByInviteCode(String inviteCode) {
        String sql = "SELECT id, group_name, description, invite_code, created_by, created_at FROM expense_groups WHERE invite_code = ?";
        try {
            ExpenseGroup group = jdbcTemplate.queryForObject(sql, groupRowMapper, inviteCode);
            return Optional.ofNullable(group);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsByInviteCode(String inviteCode) {
        String sql = "SELECT COUNT(*) FROM expense_groups WHERE invite_code = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, inviteCode);
        return count != null && count > 0;
    }

    public List<ExpenseGroup> findByUserId(Long userId) {
        String sql = "SELECT eg.id, eg.group_name, eg.description, eg.invite_code, eg.created_by, eg.created_at, " +
                "u.display_name AS creator_name, " +
                "(SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = eg.id) AS member_count, " +
                "(SELECT COALESCE(SUM(total_amount), 0) FROM group_expenses ge WHERE ge.group_id = eg.id) AS total_spent " +
                "FROM expense_groups eg " +
                "JOIN group_members my_gm ON my_gm.group_id = eg.id " +
                "JOIN users u ON u.id = eg.created_by " +
                "WHERE my_gm.user_id = ? " +
                "ORDER BY eg.created_at DESC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            ExpenseGroup g = groupRowMapper.mapRow(rs, rowNum);
            if (g != null) {
                g.setCreatorName(rs.getString("creator_name"));
                g.setMemberCount(rs.getInt("member_count"));
                g.setTotalSpent(rs.getBigDecimal("total_spent"));
            }
            return g;
        }, userId);
    }
}
