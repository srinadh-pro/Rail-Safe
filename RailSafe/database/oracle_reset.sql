-- =============================================================
-- RailSafe - reset the Oracle demo data
-- =============================================================
-- Run as the RAILSAFE application user:
--
--   sqlplus railsafe/RailSafe_2026@//localhost:1521/orclpdb @oracle_reset.sql
--
-- Then restart the Spring Boot application. DataInitializer will
-- recreate the three demo accounts, five DEMO crossings, one saved
-- route, one pending report and two notifications.
--
-- This deletes data only; it does not drop the six tables.
-- =============================================================

DELETE FROM notifications;
DELETE FROM user_reports;
DELETE FROM saved_routes;
DELETE FROM gate_status_history;
DELETE FROM railway_gates;
DELETE FROM users;

COMMIT;

-- Identity values are not reset. That is harmless for the demo:
-- DataInitializer looks rows up by email and gate code, not by ID.
EXIT;
