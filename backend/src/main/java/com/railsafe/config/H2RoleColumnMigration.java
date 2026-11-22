package com.railsafe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Locale;

/**
 * Safe, non-destructive migration for the optional H2 demo database.
 *
 * Older copies of the H2 file created users.role as a native ENUM limited to
 * ('ADMIN', 'STAFF', 'USER'). Adding Role.SUPER_ADMIN would otherwise fail before
 * the bootstrap could run. This runner converts only that column to VARCHAR(20);
 * it never drops the table, deletes rows or resets any account. Fresh databases,
 * Oracle and MySQL are unaffected.
 */
@Component
@Order(-100)
public class H2RoleColumnMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(H2RoleColumnMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public H2RoleColumnMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !product.toUpperCase(Locale.ROOT).contains("H2")) {
                return;
            }

            String columnType = null;
            try (ResultSet columns = connection.getMetaData().getColumns(null, null, "users", "role")) {
                if (columns.next()) {
                    columnType = columns.getString("TYPE_NAME");
                }
            }

            if (columnType == null || !columnType.toUpperCase(Locale.ROOT).startsWith("ENUM")) {
                return;
            }

            jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN role VARCHAR(20)");
            log.info("Migrated H2 users.role from native ENUM to VARCHAR(20). "
                    + "Existing accounts and passwords were preserved.");
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not migrate the H2 users.role column for SUPER_ADMIN. "
                            + "Back up data/railsafe-demo.mv.db and restart.", ex);
        }
    }
}
