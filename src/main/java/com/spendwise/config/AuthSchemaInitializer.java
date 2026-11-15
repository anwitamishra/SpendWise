package com.spendwise.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSetMetaData;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Adds account columns when an existing guest-only database is upgraded. */
@Component
public class AuthSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public AuthSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> columns = jdbcTemplate.query("SELECT * FROM users WHERE 1 = 0", rs -> {
            Set<String> names = new HashSet<>();
            ResultSetMetaData metadata = rs.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                names.add(metadata.getColumnLabel(i).toLowerCase(Locale.ROOT));
            }
            return names;
        });
        addColumnIfMissing(columns, "username", "VARCHAR(30)");
        addColumnIfMissing(columns, "email", "VARCHAR(254)");
        addColumnIfMissing(columns, "password_hash", "VARCHAR(100)");
        addColumnIfMissing(columns, "reset_token_hash", "VARCHAR(64)");
        addColumnIfMissing(columns, "reset_token_expires_at", "TIMESTAMP NULL");

        Boolean indexExists = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            try (var indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "users", true, false)) {
                while (indexes.next()) {
                    if ("email".equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) return true;
                }
            }
            return false;
        });
        if (!Boolean.TRUE.equals(indexExists)) {
            jdbcTemplate.execute("CREATE UNIQUE INDEX ux_users_email ON users(email)");
        }
        createUniqueIndexIfMissing("username", "ux_users_username");
    }

    private void addColumnIfMissing(Set<String> columns, String name, String definition) {
        if (!columns.contains(name)) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN " + name + " " + definition);
        }
    }

    private void createUniqueIndexIfMissing(String columnName, String indexName) {
        Boolean indexExists = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            try (var indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "users", true, false)) {
                while (indexes.next()) {
                    if (columnName.equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) return true;
                }
            }
            return false;
        });
        if (!Boolean.TRUE.equals(indexExists)) {
            jdbcTemplate.execute("CREATE UNIQUE INDEX " + indexName + " ON users(" + columnName + ")");
        }
    }
}