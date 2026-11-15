package com.spendwise.repository;

import com.spendwise.model.ReceiptUpload;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class ReceiptUploadRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReceiptUploadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(Long groupId, Long userId, String originalName, String storageKey, String contentType, long size) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO group_receipt_uploads (group_id, uploaded_by, original_filename, storage_key, content_type, file_size) " +
                            "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, groupId);
            statement.setLong(2, userId);
            statement.setString(3, originalName);
            statement.setString(4, storageKey);
            statement.setString(5, contentType);
            statement.setLong(6, size);
            return statement;
        }, keyHolder);
        return KeyHolderUtil.extractId(keyHolder);
    }

    public List<ReceiptUpload> findByGroupId(Long groupId) {
        return jdbcTemplate.query("SELECT id, group_id, original_filename, content_type, file_size, uploaded_at " +
                        "FROM group_receipt_uploads WHERE group_id = ? ORDER BY uploaded_at DESC",
                (rs, rowNum) -> {
                    ReceiptUpload upload = new ReceiptUpload();
                    upload.setId(rs.getLong("id"));
                    upload.setGroupId(rs.getLong("group_id"));
                    upload.setOriginalFilename(rs.getString("original_filename"));
                    upload.setContentType(rs.getString("content_type"));
                    upload.setFileSize(rs.getLong("file_size"));
                    Timestamp timestamp = rs.getTimestamp("uploaded_at");
                    if (timestamp != null) upload.setUploadedAt(timestamp.toLocalDateTime());
                    return upload;
                }, groupId);
    }
}
