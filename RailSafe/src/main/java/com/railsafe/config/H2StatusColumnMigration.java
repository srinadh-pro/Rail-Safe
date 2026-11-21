package com.railsafe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Additive H2 migration for the expanded status vocabulary.
 *
 * Older H2 demo files used native ENUM columns limited to OPEN/CLOSED/UNKNOWN and
 * STAFF/SENSOR/DEMO. Those constraints reject AWAITING_VERIFICATION and REFERENCE.
 * This runner converts only those ENUM columns to VARCHAR; rows and values are
 * preserved. Fresh databases, Oracle and MySQL are unaffected.
 */
@Component
@Order(-99)
public class H2StatusColumnMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(H2StatusColumnMigration.class);

    private record ColumnSpec(String table, String column, int length) { }

    private static final List<ColumnSpec> COLUMNS = List.of(
            new ColumnSpec("railway_gates", "status", 30),
            new ColumnSpec("railway_gates", "status_source", 30),
            new ColumnSpec("gate_status_history", "old_status", 30),
            new ColumnSpec("gate_status_history", "new_status", 30),
            new ColumnSpec("gate_status_history", "source", 30),
            new ColumnSpec("notifications", "status_at_event", 30),
            new ColumnSpec("user_reports", "reported_status", 30)
    );

    private final JdbcTemplate jdbcTemplate;

    public H2StatusColumnMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !product.toUpperCase(Locale.ROOT).contains("H2")) {
                return;
            }

            int migrated = 0;
            for (ColumnSpec spec : COLUMNS) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)
                        """, spec.table(), spec.column());
                if (rows.isEmpty()) {
                    continue;
                }
                try {
                    // Converting an existing VARCHAR to the same VARCHAR is harmless, so
                    // always issue the statement; this also catches ENUM columns whose
                    // JDBC metadata reports VARCHAR.
                    jdbcTemplate.execute("ALTER TABLE " + spec.table()
                            + " ALTER COLUMN " + spec.column() + " VARCHAR(" + spec.length() + ")");
                    migrated++;
                } catch (Exception ex) {
                    log.debug("H2 column {}.{} already compatible or could not be widened: {}",
                            spec.table(), spec.column(), ex.getMessage());
                }
            }
            if (migrated > 0) {
                log.info("Migrated {} H2 status/source ENUM column(s) to VARCHAR. Existing rows were preserved.", migrated);
            }
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not widen legacy H2 status columns. Back up data/railsafe-demo.mv.db and restart.", ex);
        }
    }
}
