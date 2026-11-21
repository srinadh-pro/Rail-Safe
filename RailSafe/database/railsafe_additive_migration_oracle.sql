-- =============================================================
-- RailSafe - Oracle 19c ADDITIVE migration: RBAC, reference layer
-- and provenance columns
-- =============================================================
-- Run as the RAILSAFE user, after the base schema exists
-- (railsafe_schema.sql) or after an existing/legacy installation.
--
-- SAFE TO RE-RUN. Every object is created only when missing
-- (USER_TABLES / USER_TAB_COLUMNS / USER_INDEXES checks) and every
-- seed uses INSERT-only MERGE. No table, column or existing row is
-- dropped, truncated or recreated. Prior migrations are untouched.
--
-- NEW TABLES
--   roles, permissions, role_permissions,
--   railway_crossings, train_schedules, train_positions,
--   gate_train_mapping, route_search_history, maintenance_requests,
--   railway_zones, railway_divisions, data_sources, staff_assignments
-- NEW COLUMNS
--   railway_gates (13), train_master (13)
-- PERMISSIVE WIDENING
--   GateStatus.AWAITING_VERIFICATION is 21 characters, so the existing
--   VARCHAR2(20) status columns are widened to VARCHAR2(30). This is a
--   MODIFY (grow-only) change; the existing columns have no CHECK
--   constraint, so nothing is dropped or rebuilt. New NotificationType
--   values (GATE_STATUS_UPDATED / GATE_MAINTENANCE) already fit in
--   notifications.type VARCHAR2(30).
-- =============================================================

SET SERVEROUTPUT ON;

-- -------------------------------------------------------------
-- 1. Grow-only widening of status columns (no CHECK constraints exist)
-- -------------------------------------------------------------
DECLARE
    PROCEDURE widen(p_table VARCHAR2, p_column VARCHAR2, p_new_len NUMBER) IS
        v_len NUMBER;
    BEGIN
        SELECT data_length INTO v_len
          FROM user_tab_columns
         WHERE table_name = UPPER(p_table)
           AND column_name = UPPER(p_column);

        IF v_len < p_new_len THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' MODIFY ('
                || p_column || ' VARCHAR2(' || p_new_len || '))';
            DBMS_OUTPUT.PUT_LINE('Widened ' || p_table || '.' || p_column
                || ' from VARCHAR2(' || v_len || ') to VARCHAR2(' || p_new_len || ').');
        END IF;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE('SKIP widen: ' || p_table || '.' || p_column || ' not found.');
    END;
BEGIN
    widen('railway_gates', 'status', 30);
    widen('railway_gates', 'verification_status', 30);
    widen('gate_status_history', 'old_status', 30);
    widen('gate_status_history', 'new_status', 30);
    widen('notifications', 'status_at_event', 30);
    widen('user_reports', 'reported_status', 30);
END;
/

-- -------------------------------------------------------------
-- 2. New columns on railway_gates and train_master
-- -------------------------------------------------------------
DECLARE
    PROCEDURE add_col(p_table VARCHAR2, p_column VARCHAR2, p_definition VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM user_tab_columns
         WHERE table_name = UPPER(p_table)
           AND column_name = UPPER(p_column);

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_definition || ')';
            DBMS_OUTPUT.PUT_LINE('Added ' || p_table || '.' || p_column || '.');
        END IF;
    END;
BEGIN
    -- railway_gates (reference / provenance / timing metadata)
    add_col('railway_gates', 'railway_zone',       'railway_zone VARCHAR2(80)');
    add_col('railway_gates', 'division',           'division VARCHAR2(80)');
    add_col('railway_gates', 'station',            'station VARCHAR2(120)');
    add_col('railway_gates', 'district',           'district VARCHAR2(120)');
    add_col('railway_gates', 'state',              'state VARCHAR2(80)');
    add_col('railway_gates', 'gate_type',          'gate_type VARCHAR2(40)');
    add_col('railway_gates', 'associated_routes',  'associated_routes VARCHAR2(300)');
    add_col('railway_gates', 'data_classification',
            'data_classification VARCHAR2(25) DEFAULT ''VERIFIED_REFERENCE''');
    add_col('railway_gates', 'status_changed_at',  'status_changed_at TIMESTAMP(6)');
    add_col('railway_gates', 'expected_closing_at','expected_closing_at TIMESTAMP(6)');
    add_col('railway_gates', 'expected_opening_at','expected_opening_at TIMESTAMP(6)');
    add_col('railway_gates', 'actual_closing_at',  'actual_closing_at TIMESTAMP(6)');
    add_col('railway_gates', 'actual_opening_at',  'actual_opening_at TIMESTAMP(6)');

    -- train_master (verified reference catalogue enrichment)
    add_col('train_master', 'source_station',      'source_station VARCHAR2(120)');
    add_col('train_master', 'destination_station', 'destination_station VARCHAR2(120)');
    add_col('train_master', 'current_station',     'current_station VARCHAR2(120)');
    add_col('train_master', 'next_station',        'next_station VARCHAR2(120)');
    add_col('train_master', 'scheduled_departure', 'scheduled_departure VARCHAR2(20)');
    add_col('train_master', 'scheduled_arrival',   'scheduled_arrival VARCHAR2(20)');
    add_col('train_master', 'estimated_arrival',   'estimated_arrival VARCHAR2(20)');
    add_col('train_master', 'running_status',      'running_status VARCHAR2(40) DEFAULT ''Scheduled''');
    add_col('train_master', 'speed_kmph',          'speed_kmph FLOAT(126)');
    add_col('train_master', 'data_classification',
            'data_classification VARCHAR2(25) DEFAULT ''VERIFIED_REFERENCE''');
    add_col('train_master', 'reference_source',    'reference_source VARCHAR2(120)');
    add_col('train_master', 'reference_url',       'reference_url VARCHAR2(500)');
    add_col('train_master', 'last_verified_at',    'last_verified_at TIMESTAMP(6)');
END;
/

-- -------------------------------------------------------------
-- 3. New tables (created only when missing) and their indexes
-- -------------------------------------------------------------
DECLARE
    PROCEDURE create_table_if_missing(p_table VARCHAR2, p_ddl VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM user_tables WHERE table_name = UPPER(p_table);
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE('Created table ' || p_table || '.');
        END IF;
    END;

    PROCEDURE create_index_if_missing(p_index VARCHAR2, p_ddl VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM user_indexes WHERE index_name = UPPER(p_index);
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE('Created index ' || p_index || '.');
        END IF;
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE != -1408 THEN RAISE; END IF; -- index already exists
    END;
BEGIN
    -- 3.1 roles -------------------------------------------------
    create_table_if_missing('roles', q'[
        CREATE TABLE roles (
            name        VARCHAR2(40)  NOT NULL,
            description VARCHAR2(255),
            built_in    NUMBER(1)     DEFAULT 1 NOT NULL,
            CONSTRAINT pk_roles PRIMARY KEY (name),
            CONSTRAINT ck_roles_built_in CHECK (built_in IN (0, 1))
        )]');

    -- 3.2 permissions -------------------------------------------
    create_table_if_missing('permissions', q'[
        CREATE TABLE permissions (
            id          NUMBER(19, 0) GENERATED BY DEFAULT AS IDENTITY,
            code        VARCHAR2(80)  NOT NULL,
            description VARCHAR2(255),
            category    VARCHAR2(80),
            CONSTRAINT pk_permissions PRIMARY KEY (id),
            CONSTRAINT uk_permission_code UNIQUE (code)
        )]');
    create_index_if_missing('idx_permissions_category',
        'CREATE INDEX idx_permissions_category ON permissions (category)');

    -- 3.3 role_permissions --------------------------------------
    create_table_if_missing('role_permissions', q'[
        CREATE TABLE role_permissions (
            role_name     VARCHAR2(40)  NOT NULL,
            permission_id NUMBER(19, 0) NOT NULL,
            CONSTRAINT pk_role_permissions PRIMARY KEY (role_name, permission_id),
            CONSTRAINT fk_rp_role FOREIGN KEY (role_name)
                REFERENCES roles (name) ON DELETE CASCADE,
            CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id)
                REFERENCES permissions (id) ON DELETE CASCADE
        )]');

    -- 3.4 railway_crossings -------------------------------------
    create_table_if_missing('railway_crossings', q'[
        CREATE TABLE railway_crossings (
            id                  NUMBER(19, 0) GENERATED BY DEFAULT AS IDENTITY,
            crossing_code       VARCHAR2(30)  NOT NULL,
            name                VARCHAR2(120),
            gate_id             NUMBER(19, 0),
            latitude            FLOAT(126),
            longitude           FLOAT(126),
            station             VARCHAR2(120),
            district            VARCHAR2(120),
            state               VARCHAR2(80),
            railway_zone        VARCHAR2(80),
            division            VARCHAR2(80),
            status              VARCHAR2(30),
            data_classification VARCHAR2(25),
            source_key          VARCHAR2(120),
            notes               VARCHAR2(500),
            created_at          TIMESTAMP(6),
            updated_at          TIMESTAMP(6),
            CONSTRAINT pk_railway_crossings PRIMARY KEY (id),
            CONSTRAINT uk_crossing_code UNIQUE (crossing_code),
            CONSTRAINT fk_crossing_gate FOREIGN KEY (gate_id)
                REFERENCES railway_gates (id) ON DELETE SET NULL
        )]');
    create_index_if_missing('idx_crossing_gate',
        'CREATE INDEX idx_crossing_gate ON railway_crossings (gate_id)');
    create_index_if_missing('idx_crossing_zone',
        'CREATE INDEX idx_crossing_zone ON railway_crossings (railway_zone, division)');

    -- 3.5 train_schedules ---------------------------------------
    create_table_if_missing('train_schedules', q'[
        CREATE TABLE train_schedules (
            id                  NUMBER(19, 0) GENERATED BY DEFAULT AS IDENTITY,
            train_number        VARCHAR2(20)  NOT NULL,
            station_name        VARCHAR2(120) NOT NULL,
            station_code        VARCHAR2(20),
            sequence            NUMBER(10, 0),
            arrival_time        VARCHAR2(20),
            departure_time      VARCHAR2(20),
            day_offset          NUMBER(10, 0) DEFAULT 0,
            data_classification VARCHAR2(25),
            reference_source    VARCHAR2(120),
            reference_url       VARCHAR2(500),
            created_at          TIMESTAMP(6),
            CONSTRAINT pk_train_schedules PRIMARY KEY (id)
        )]');
    create_index_if_missing('idx_train_schedule_number',
        'CREATE INDEX idx_train_schedule_number ON train_schedules (train_number)');

    -- 3.6 train_positions ---------------------------------------
    create_table_if_missing('train_positions', q'[
        CREATE TABLE train_positions (
            id                  NUMBER(19, 0) GENERATED BY DEFAULT AS IDENTITY,
            train_number        VARCHAR2(20)  NOT NULL,
            latitude            FLOAT(126),
            longitude           FLOAT(126),
            speed_kmph          FLOAT(126),
            observed_at         TIMESTAMP(6),
            provider            VARCHAR2(120),
            data_classification VARCHAR2(25) NOT NULL,
            synthetic           NUMBER(1) DEFAULT 1 NOT NULL,
            created_at          TIMESTAMP(6),
            CONSTRAINT pk_train_positions PRIMARY KEY (id),
            CONSTRAINT ck_train_positions_synthetic CHECK (synthetic IN (0, 1))
        )]');
    create_index_if_missing('idx_train_position_number',
        'CREATE INDEX idx_train_position_number ON train_positions (train_number)');
    create_index_if_missing('idx_train_position_observed',
        'CREATE INDEX idx_train_position_observed ON train_positions (observed_at)');
    create_index_if_missing('idx_train_position_number_observed',
        'CREATE INDEX idx_train_position_number_observed ON train_positions (train_number, observed_at)');

    -- 3.7 gate_train_mapping ------------------------------------
    create_table_if_missing('gate_train_mapping', q'[
        CREATE TABLE gate_train_mapping (
            id                  NUMBER(19, 0) GENERATED BY DEFAULT AS IDENTITY,
            gate_id             NUMBER(19, 0) NOT NULL,
            train_number        VARCHAR2(20)  NOT NULL,
            direction           VARCHAR2(20),
            scheduled_time      VARCHAR2(20),
            active              NUMBER(1) DEFAULT 1 NOT NULL,
            data_classification VARCHAR2(25),
            created_at          TIMESTAMP(6),
            CONSTRAINT pk_gate_train_mapping PRIMARY KEY (id),
            CONSTRAINT ck_gtm_active CHECK (active IN (0, 1)),
            CONSTRAINT fk_gtm_gate FOREIGN KEY (gate_id)
                REFERENCES railway_gates (id) ON DELETE CASCADE
        )]');
    create_index_if_missing('idx_gate_train_gate',
        'CREATE INDEX idx_gate_train_gate ON gate_train_mapping (gate_id)');
    create_index_if_missing('idx_gate_train_number',
        'CREATE INDEX idx_gate_train_number ON gate_train_mapping (train_number)');
    create_index_if_missing('idx_gate_train_gate_number',
        'CREATE INDEX idx_gate_train_gate_number ON gate_train_mapping (gate_id, train_number)');

    -- 3.8 route_search_history ----------------------------------
    create_table_if_missing('route_search_history', q'[
        CREATE TABLE route_search_history (
            id                  NUMBER(19, 0) GENERATED BY DEFAULT AS IDENTITY,
            user_id             NUMBER(19, 0),
            start_lat           FLOAT(126),
            start_lng           FLOAT(126),
            end_lat             FLOAT(126),
            end_lng             FLOAT(126),
            start_label         VARCHAR2(160),
            end_label           VARCHAR2(160),
            gate_ids            VARCHAR2(500),
            distance_km         FLOAT(126),
            duration_minutes    FLOAT(126),
            route_count         NUMBER(10, 0),
            created_at          TIMESTAMP(6) NOT NULL,
            data_classification VARCHAR2(25),
            CONSTRAINT pk_route_search_history PRIMARY KEY (id),
            CONSTRAINT fk_rsh_user FOREIGN KEY (user_id)
                REFERENCES users (id) ON DELETE SET NULL
        )]');
    create_index_if_missing('idx_rsh_user',
        'CREATE INDEX idx_rsh_user ON route_search_history (user_id)');
    create_index_if_missing('idx_rsh_created',
        'CREATE INDEX idx_rsh_created ON route_search_history (created_at)');

    -- 3.9 maintenance_requests ----------------------------------
    create_table_if_missing('maintenance_requests', q'[
        CREATE TABLE maintenance_requests (
            id              NUMBER(19, 0) GENERATED BY DEFAULT AS IDENTITY,
            gate_id         NUMBER(19, 0) NOT NULL,
            requester_id    NUMBER(19, 0),
            assigned_to_id  NUMBER(19, 0),
            issue_type      VARCHAR2(60)   NOT NULL,
            description     VARCHAR2(1000) NOT NULL,
            priority        VARCHAR2(20),
            status          VARCHAR2(30) DEFAULT 'OPEN' NOT NULL,
            resolution_note VARCHAR2(1000),
            requested_at    TIMESTAMP(6) NOT NULL,
            updated_at      TIMESTAMP(6),
            resolved_at     TIMESTAMP(6),
            CONSTRAINT pk_maintenance_requests PRIMARY KEY (id),
            CONSTRAINT fk_mr_gate FOREIGN KEY (gate_id)
                REFERENCES railway_gates (id) ON DELETE CASCADE,
            CONSTRAINT fk_mr_requester FOREIGN KEY (requester_id)
                REFERENCES users (id) ON DELETE SET NULL,
            CONSTRAINT fk_mr_assignee FOREIGN KEY (assigned_to_id)
                REFERENCES users (id) ON DELETE SET NULL
        )]');
    create_index_if_missing('idx_maintenance_gate',
        'CREATE INDEX idx_maintenance_gate ON maintenance_requests (gate_id)');
    create_index_if_missing('idx_maintenance_status',
        'CREATE INDEX idx_maintenance_status ON maintenance_requests (status)');
    create_index_if_missing('idx_maintenance_gate_status',
        'CREATE INDEX idx_maintenance_gate_status ON maintenance_requests (gate_id, status)');

    -- 3.10 railway_zones ----------------------------------------
    create_table_if_missing('railway_zones', q'[
        CREATE TABLE railway_zones (
            code         VARCHAR2(20)  NOT NULL,
            name         VARCHAR2(120) NOT NULL,
            headquarters VARCHAR2(120),
            active       NUMBER(1) DEFAULT 1,
            CONSTRAINT pk_railway_zones PRIMARY KEY (code),
            CONSTRAINT ck_railway_zones_active CHECK (active IN (0, 1))
        )]');

    -- 3.11 railway_divisions ------------------------------------
    create_table_if_missing('railway_divisions', q'[
        CREATE TABLE railway_divisions (
            code      VARCHAR2(20)  NOT NULL,
            name      VARCHAR2(120) NOT NULL,
            zone_code VARCHAR2(20)  NOT NULL,
            active    NUMBER(1) DEFAULT 1,
            CONSTRAINT pk_railway_divisions PRIMARY KEY (code),
            CONSTRAINT ck_railway_divisions_active CHECK (active IN (0, 1)),
            CONSTRAINT fk_division_zone FOREIGN KEY (zone_code)
                REFERENCES railway_zones (code)
        )]');

    -- 3.12 data_sources -----------------------------------------
    create_table_if_missing('data_sources', q'[
        CREATE TABLE data_sources (
            id                  NUMBER(19, 0) GENERATED BY DEFAULT AS IDENTITY,
            name                VARCHAR2(120) NOT NULL,
            source_type         VARCHAR2(40)  NOT NULL,
            url                 VARCHAR2(500),
            data_mode           VARCHAR2(30)  NOT NULL,
            data_classification VARCHAR2(25)  NOT NULL,
            enabled             NUMBER(1) DEFAULT 1 NOT NULL,
            last_sync_at        TIMESTAMP(6),
            description         VARCHAR2(500),
            created_at          TIMESTAMP(6),
            CONSTRAINT pk_data_sources PRIMARY KEY (id),
            CONSTRAINT uk_data_source_name UNIQUE (name),
            CONSTRAINT ck_data_sources_enabled CHECK (enabled IN (0, 1))
        )]');
    create_index_if_missing('idx_data_sources_mode',
        'CREATE INDEX idx_data_sources_mode ON data_sources (data_mode)');

    -- 3.13 staff_assignments ------------------------------------
    create_table_if_missing('staff_assignments', q'[
        CREATE TABLE staff_assignments (
            id              NUMBER(19, 0) GENERATED BY DEFAULT AS IDENTITY,
            user_id         NUMBER(19, 0) NOT NULL,
            gate_id         NUMBER(19, 0) NOT NULL,
            active          NUMBER(1) DEFAULT 1 NOT NULL,
            assigned_at     TIMESTAMP(6) NOT NULL,
            unassigned_at   TIMESTAMP(6),
            assigned_by_id  NUMBER(19, 0),
            CONSTRAINT pk_staff_assignments PRIMARY KEY (id),
            CONSTRAINT uk_staff_gate UNIQUE (user_id, gate_id),
            CONSTRAINT fk_staff_assignment_user FOREIGN KEY (user_id)
                REFERENCES users (id) ON DELETE CASCADE,
            CONSTRAINT fk_staff_assignment_gate FOREIGN KEY (gate_id)
                REFERENCES railway_gates (id) ON DELETE CASCADE,
            CONSTRAINT fk_staff_assignment_by FOREIGN KEY (assigned_by_id)
                REFERENCES users (id),
            CONSTRAINT ck_staff_assignment_active CHECK (active IN (0, 1))
        )]');
    create_index_if_missing('idx_staff_assignment_user',
        'CREATE INDEX idx_staff_assignment_user ON staff_assignments (user_id)');
    create_index_if_missing('idx_staff_assignment_gate',
        'CREATE INDEX idx_staff_assignment_gate ON staff_assignments (gate_id)');
END;
/

-- -------------------------------------------------------------
-- 4. Reference seeds (INSERT-only MERGE; existing rows untouched)
--    Values below mirror ReferenceDataInitializer so the application
--    finds its reference rows instead of creating duplicates.
-- -------------------------------------------------------------

-- 4.1 roles
MERGE INTO roles r
USING (
    SELECT 'CUSTOMER' AS name,
           'Authenticated commuter: views permitted gates, trains, alerts and routes.' AS description FROM dual
    UNION ALL SELECT 'USER',
           'Legacy commuter role retained for existing accounts.' FROM dual
    UNION ALL SELECT 'STAFF',
           'Authorised railway staff: assigned gate updates and report review.' FROM dual
    UNION ALL SELECT 'ADMIN',
           'Operations administrator: all gates, staff accounts and reports.' FROM dual
    UNION ALL SELECT 'SUPER_ADMIN',
           'Highest access: roles, permissions, audit, restricted source review and system configuration.' FROM dual
) src
ON (r.name = src.name)
WHEN NOT MATCHED THEN
    INSERT (name, description, built_in) VALUES (src.name, src.description, 1);

-- 4.2 permissions
MERGE INTO permissions p
USING (
    SELECT 'gate.view' AS code, 'View permitted gate information' AS description, 'gate' AS category FROM dual
    UNION ALL SELECT 'gate.update',    'Update an assigned or managed gate status', 'gate' FROM dual
    UNION ALL SELECT 'gate.manage',    'Create, edit and remove operational gates', 'gate' FROM dual
    UNION ALL SELECT 'train.view',     'View verified train reference and authorised train information', 'train' FROM dual
    UNION ALL SELECT 'route.search',   'Calculate verified alternative routes', 'route' FROM dual
    UNION ALL SELECT 'report.view',    'View commuter reports', 'report' FROM dual
    UNION ALL SELECT 'report.moderate','Approve or reject commuter reports', 'report' FROM dual
    UNION ALL SELECT 'staff.manage',   'Create, update and assign STAFF accounts', 'staff' FROM dual
    UNION ALL SELECT 'admin.manage',   'Manage operational administrators', 'admin' FROM dual
    UNION ALL SELECT 'superadmin.access', 'Access the Super Admin area', 'superadmin' FROM dual
    UNION ALL SELECT 'source.review',  'Review restricted external source rows', 'source' FROM dual
    UNION ALL SELECT 'audit.view',     'View security and operational audit logs', 'audit' FROM dual
    UNION ALL SELECT 'system.configure','View system health, configuration and data sources', 'system' FROM dual
) src
ON (p.code = src.code)
WHEN NOT MATCHED THEN
    INSERT (code, description, category) VALUES (src.code, src.description, src.category);

-- 4.3 role_permissions
-- CUSTOMER / USER -> gate.view, train.view, route.search
-- STAFF           -> + gate.update, report.view, report.moderate
-- ADMIN           -> staff level + gate.manage + staff.manage + admin.manage
-- SUPER_ADMIN     -> every permission (no exceptions)
MERGE INTO role_permissions rp
USING (
    SELECT 'CUSTOMER' AS role_name, p.id AS permission_id FROM permissions p
        WHERE p.code IN ('gate.view', 'train.view', 'route.search')
    UNION ALL
    SELECT 'USER', p.id FROM permissions p
        WHERE p.code IN ('gate.view', 'train.view', 'route.search')
    UNION ALL
    SELECT 'STAFF', p.id FROM permissions p
        WHERE p.code IN ('gate.view', 'gate.update', 'train.view', 'route.search',
                         'report.view', 'report.moderate')
    UNION ALL
    SELECT 'ADMIN', p.id FROM permissions p
        WHERE p.code IN ('gate.view', 'gate.update', 'gate.manage', 'train.view',
                         'route.search', 'report.view', 'report.moderate',
                         'staff.manage', 'admin.manage')
    UNION ALL
    SELECT 'SUPER_ADMIN', p.id FROM permissions p
) src
ON (rp.role_name = src.role_name AND rp.permission_id = src.permission_id)
WHEN NOT MATCHED THEN
    INSERT (role_name, permission_id) VALUES (src.role_name, src.permission_id);

-- 4.4 railway_zones
MERGE INTO railway_zones z
USING (
    SELECT 'SCR' AS code, 'South Central Railway' AS name, 'Secunderabad' AS headquarters FROM dual
    UNION ALL SELECT 'NWR', 'North Western Railway', 'Jaipur' FROM dual
    UNION ALL SELECT 'SWR', 'South Western Railway', 'Hubballi' FROM dual
    UNION ALL SELECT 'ECoR', 'East Coast Railway', 'Bhubaneswar' FROM dual
    UNION ALL SELECT 'CR', 'Central Railway', 'Mumbai' FROM dual
    UNION ALL SELECT 'WR', 'Western Railway', 'Mumbai' FROM dual
) src
ON (z.code = src.code)
WHEN NOT MATCHED THEN
    INSERT (code, name, headquarters, active) VALUES (src.code, src.name, src.headquarters, 1);

-- 4.5 railway_divisions (all five required SCR divisions)
MERGE INTO railway_divisions d
USING (
    SELECT 'HYB' AS code, 'Hyderabad' AS name, 'SCR' AS zone_code FROM dual
    UNION ALL SELECT 'SC',  'Secunderabad', 'SCR' FROM dual
    UNION ALL SELECT 'BZA', 'Vijayawada',   'SCR' FROM dual
    UNION ALL SELECT 'GNT', 'Guntur',       'SCR' FROM dual
    UNION ALL SELECT 'GTA', 'Guntakal',     'SCR' FROM dual
) src
ON (d.code = src.code)
WHEN NOT MATCHED THEN
    INSERT (code, name, zone_code, active) VALUES (src.code, src.name, src.zone_code, 1);

-- 4.6 data_sources
-- Names/modes/classifications match ReferenceDataInitializer.
-- "OSM" is OpenStreetMap; "Sensor Webhook" is Gate Sensor Webhook.
-- The restricted dataset is explicitly SIMULATION/SIMULATED - there is no
-- fake "official Indian Railways live feed" row here.
MERGE INTO data_sources ds
USING (
    SELECT 'OpenStreetMap' AS name, 'MAP' AS source_type,
           'https://www.openstreetmap.org' AS url, 'REAL_API' AS data_mode,
           'VERIFIED_REFERENCE' AS data_classification, 1 AS enabled,
           'Map tiles and place reference data used by the route map.' AS description FROM dual
    UNION ALL SELECT 'OSRM', 'ROUTING', 'https://router.project-osrm.org', 'REAL_API',
           'VERIFIED_REFERENCE', 1,
           'Calculates route geometry, distance and duration. No route is invented when unavailable.' FROM dual
    UNION ALL SELECT 'Train Reference Catalogue', 'REFERENCE', 'https://www.railyatri.in/', 'REFERENCE',
           'VERIFIED_REFERENCE', 1,
           '30 checked train names, numbers, routes and scheduled times. No live tracking claim.' FROM dual
    UNION ALL SELECT 'Restricted Source Dataset', 'IMPORT', 'data/railsafe-expanded-data.json', 'SIMULATION',
           'SIMULATED', 1,
           'Retained for Super Admin source review only; never promoted to operational data.' FROM dual
    UNION ALL SELECT 'Gate Sensor Webhook', 'SENSOR', '/api/live/sensor', 'OPERATIONAL',
           'VERIFIED_LIVE', 0,
           'Optional authenticated gate-status sensor. Disabled until a shared secret is configured.' FROM dual
    UNION ALL SELECT 'Simulation Provider', 'SIMULATION', 'internal', 'SIMULATION',
           'SIMULATED', 1,
           'Acknowledged demonstration feed. Never presented as genuine live railway data.' FROM dual
) src
ON (ds.name = src.name)
WHEN NOT MATCHED THEN
    INSERT (name, source_type, url, data_mode, data_classification, enabled, description, created_at)
    VALUES (src.name, src.source_type, src.url, src.data_mode, src.data_classification,
            src.enabled, src.description, SYSTIMESTAMP);

COMMIT;

BEGIN
    DBMS_OUTPUT.PUT_LINE('RailSafe additive Oracle migration completed.');
END;
/
