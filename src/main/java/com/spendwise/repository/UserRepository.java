package com.spendwise.repository;

import com.spendwise.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<User> userRowMapper = (rs, rowNum) -> {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setDisplayName(rs.getString("display_name"));
        user.setGuestIdentifier(rs.getString("guest_identifier"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            user.setCreatedAt(ts.toLocalDateTime());
        }
        return user;
    };

    public User save(User user) {
        String sql = "INSERT INTO users (display_name, guest_identifier) VALUES (?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getDisplayName());
            ps.setString(2, user.getGuestIdentifier());
            return ps;
        }, keyHolder);

        Long generatedId = KeyHolderUtil.extractId(keyHolder);
        if (generatedId != null) {
            user.setId(generatedId);
        }
        return user;
    }

    public Optional<User> findById(Long id) {
        String sql = "SELECT id, display_name, guest_identifier, created_at FROM users WHERE id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByGuestIdentifier(String guestIdentifier) {
        String sql = "SELECT id, display_name, guest_identifier, created_at FROM users WHERE guest_identifier = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, guestIdentifier);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int updateDisplayName(Long id, String displayName) {
        String sql = "UPDATE users SET display_name = ? WHERE id = ?";
        return jdbcTemplate.update(sql, displayName, id);
    }
}
