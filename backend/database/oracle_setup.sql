-- =============================================================
-- RailSafe - one-time Oracle Database 19c+ setup
-- =============================================================
-- Run this script as SYSDBA from the CDB (ORCL):
--
--   sqlplus / as sysdba @oracle_setup.sql
--
-- It opens and persists the ORCLPDB pluggable database, creates the
-- RAILSAFE application user, and grants the privileges Hibernate needs.
--
-- WARNING: change the demo password before using this outside a local
-- hackathon environment.
-- =============================================================

ALTER SESSION SET CONTAINER = ORCLPDB;

ALTER PLUGGABLE DATABASE ORCLPDB OPEN;
ALTER PLUGGABLE DATABASE ORCLPDB SAVE STATE;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM dba_users WHERE username = 'RAILSAFE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE USER railsafe IDENTIFIED BY "RailSafe_2026" '
            || 'DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS';
        DBMS_OUTPUT.PUT_LINE('Created user RAILSAFE');
    ELSE
        EXECUTE IMMEDIATE 'ALTER USER railsafe IDENTIFIED BY "RailSafe_2026" ACCOUNT UNLOCK';
        DBMS_OUTPUT.PUT_LINE('Updated password for existing user RAILSAFE');
    END IF;
END;
/

GRANT CONNECT, RESOURCE TO railsafe;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE TRIGGER, CREATE VIEW TO railsafe;
ALTER USER railsafe QUOTA UNLIMITED ON USERS;

-- Optional: create the six tables and demo gates immediately.
-- The Spring Boot application can also create them on first run
-- (spring.jpa.hibernate.ddl-auto=update).
-- Connect as RAILSAFE and run @railsafe_schema.sql.

EXIT;
