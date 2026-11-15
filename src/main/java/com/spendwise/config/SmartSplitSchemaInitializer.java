package com.spendwise.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SmartSplitSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public SmartSplitSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Boolean hasStatus = jdbcTemplate.query("SELECT * FROM settlements WHERE 1 = 0", rs -> {
            var metadata = rs.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                if ("status".equals(metadata.getColumnLabel(i).toLowerCase(Locale.ROOT))) return true;
            }
            return false;
        });
        if (!Boolean.TRUE.equals(hasStatus)) {
            jdbcTemplate.execute("ALTER TABLE settlements ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'");
        }
        // Historical rows have a settlement date and must remain reflected in current balances.
        jdbcTemplate.update("UPDATE settlements SET status = 'COMPLETED' WHERE settled_date IS NOT NULL AND status IS NULL");
    }
}