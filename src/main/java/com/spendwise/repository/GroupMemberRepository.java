package com.spendwise.repository;

import com.spendwise.model.GroupMember;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class GroupMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public GroupMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<GroupMember> memberRowMapper = (rs, rowNum) -> {
        GroupMember gm = new GroupMember();
        gm.setId(rs.getLong("id"));
        gm.setGroupId(rs.getLong("group_id"));
        gm.setUserId(rs.getLong("user_id"));
        gm.setDisplayName(rs.getString("display_name"));
        Timestamp ts = rs.getTimestamp("joined_at");
        if (ts != null) {
            gm.setJoinedAt(ts.toLocalDateTime());
        }
        return gm;
    };

    public GroupMember save(GroupMember member) {
        String sql = "INSERT INTO group_members (group_id, user_id, display_name) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, member.getGroupId());
            ps.setLong(2, member.getUserId());
            ps.setString(3, member.getDisplayName());
            return ps;
        }, keyHolder);

        Long generatedId = KeyHolderUtil.extractId(keyHolder);
        if (generatedId != null) {
            member.setId(generatedId);
        }
        return member;
    }

    public Optional<GroupMember> findById(Long id) {
        String sql = "SELECT id, group_id, user_id, display_name, joined_at FROM group_members WHERE id = ?";
        try {
            GroupMember gm = jdbcTemplate.queryForObject(sql, memberRowMapper, id);
            return Optional.ofNullable(gm);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<GroupMember> findByGroupId(Long groupId) {
        String sql = "SELECT id, group_id, user_id, display_name, joined_at FROM group_members WHERE group_id = ? ORDER BY joined_at ASC";
        return jdbcTemplate.query(sql, memberRowMapper, groupId);
    }

    public Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId) {
        String sql = "SELECT id, group_id, user_id, display_name, joined_at FROM group_members WHERE group_id = ? AND user_id = ?";
        try {
            GroupMember gm = jdbcTemplate.queryForObject(sql, memberRowMapper, groupId, userId);
            return Optional.ofNullable(gm);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int countByGroupId(Long groupId) {
        String sql = "SELECT COUNT(*) FROM group_members WHERE group_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, groupId);
        return count != null ? count : 0;
    }

    public boolean isMember(Long groupId, Long userId) {
        String sql = "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, groupId, userId);
        return count != null && count > 0;
    }
}
