-- =============================================================
-- RailSafe - MySQL 8 ADDITIVE migration: RBAC, reference layer
-- and provenance columns
-- =============================================================
-- Run against the `railsafe` database, after the base schema exists
-- (railsafe_schema_mysql.sql) or after an existing/legacy installation.
--
-- SAFE TO RE-RUN. CREATE TABLE IF NOT EXISTS plus information_schema
-- driven ADD COLUMN / MODIFY COLUMN statements mean no table, column
-- or existing row is dropped, truncated or recreated. All seeds are
-- INSERT-only (WHERE NOT EXISTS). Prior migrations are untouched.
--
-- NEW TABLES
--   roles, permissions, role_permissions, railway_crossings,
--   train_schedules, train_positions, gate_train_mapping,
--   route_search_history, maintenance_requests, railway_zones,
--   railway_divisions, data_sources, staff_assignments
-- NEW COLUMNS
--   railway_gates (13), train_master (13)
-- PERMISSIVE WIDENING
--   GateStatus.AWAITING_VERIFICATION is 21 characters, so the existing
--   VARCHAR(20) status columns are widened to VARCHAR(30). The existing
--   status columns have no CHECK/enum constraint, so nothing is
--   recreated. New NotificationType values fit in type VARCHAR(30).
-- =============================================================

SET SESSION group_concat_max_len = 8192;

-- -------------------------------------------------------------
-- 1. Grow-only widening of status columns
-- -------------------------------------------------------------
SET @railsafe_sql = (
    SELECT GROUP_CONCAT(CONCAT('MODIFY COLUMN ', d.col_def) SEPARATOR ', ')
      FROM (
            SELECT 'railway_gates' AS t, 'status' AS c,
                   '`status` VARCHAR(30) NOT NULL DEFAULT ''UNKNOWN''' AS col_def
            UNION ALL SELECT 'railway_gates', 'verification_status',
                   '`verification_status` VARCHAR(30) NOT NULL DEFAULT ''UNVERIFIED'''
           ) d
      JOIN information_schema.COLUMNS ic
        ON ic.TABLE_SCHEMA = DATABASE()
       AND ic.TABLE_NAME = d.t
       AND ic.COLUMN_NAME = d.c
       AND ic.CHARACTER_MAXIMUM_LENGTH < 30
     WHERE d.t = 'railway_gates'
);
SET @railsafe_sql = IF(@railsafe_sql IS NULL, 'SELECT 1',
                       CONCAT('ALTER TABLE `railway_gates` ', @railsafe_sql));
PREPARE railsafe_stmt FROM @railsafe_sql;
EXECUTE railsafe_stmt;
DEALLOCATE PREPARE railsafe_stmt;

SET @railsafe_sql = (
    SELECT GROUP_CONCAT(CONCAT('MODIFY COLUMN ', d.col_def) SEPARATOR ', ')
      FROM (
            SELECT 'gate_status_history' AS t, 'old_status' AS c,
                   '`old_status` VARCHAR(30) NULL' AS col_def
            UNION ALL SELECT 'gate_status_history', 'new_status',
                   '`new_status` VARCHAR(30) NOT NULL'
           ) d
      JOIN information_schema.COLUMNS ic
        ON ic.TABLE_SCHEMA = DATABASE()
       AND ic.TABLE_NAME = d.t
       AND ic.COLUMN_NAME = d.c
       AND ic.CHARACTER_MAXIMUM_LENGTH < 30
     WHERE d.t = 'gate_status_history'
);
SET @railsafe_sql = IF(@railsafe_sql IS NULL, 'SELECT 1',
                       CONCAT('ALTER TABLE `gate_status_history` ', @railsafe_sql));
PREPARE railsafe_stmt FROM @railsafe_sql;
EXECUTE railsafe_stmt;
DEALLOCATE PREPARE railsafe_stmt;

SET @railsafe_sql = (
    SELECT GROUP_CONCAT(CONCAT('MODIFY COLUMN ', d.col_def) SEPARATOR ', ')
      FROM (
            SELECT 'notifications' AS t, 'status_at_event' AS c,
                   '`status_at_event` VARCHAR(30) NULL' AS col_def
           ) d
      JOIN information_schema.COLUMNS ic
        ON ic.TABLE_SCHEMA = DATABASE()
       AND ic.TABLE_NAME = d.t
       AND ic.COLUMN_NAME = d.c
       AND ic.CHARACTER_MAXIMUM_LENGTH < 30
);
SET @railsafe_sql = IF(@railsafe_sql IS NULL, 'SELECT 1',
                       CONCAT('ALTER TABLE `notifications` ', @railsafe_sql));
PREPARE railsafe_stmt FROM @railsafe_sql;
EXECUTE railsafe_stmt;
DEALLOCATE PREPARE railsafe_stmt;

SET @railsafe_sql = (
    SELECT GROUP_CONCAT(CONCAT('MODIFY COLUMN ', d.col_def) SEPARATOR ', ')
      FROM (
            SELECT 'user_reports' AS t, 'reported_status' AS c,
                   '`reported_status` VARCHAR(30) NOT NULL' AS col_def
           ) d
      JOIN information_schema.COLUMNS ic
        ON ic.TABLE_SCHEMA = DATABASE()
       AND ic.TABLE_NAME = d.t
       AND ic.COLUMN_NAME = d.c
       AND ic.CHARACTER_MAXIMUM_LENGTH < 30
);
SET @railsafe_sql = IF(@railsafe_sql IS NULL, 'SELECT 1',
                       CONCAT('ALTER TABLE `user_reports` ', @railsafe_sql));
PREPARE railsafe_stmt FROM @railsafe_sql;
EXECUTE railsafe_stmt;
DEALLOCATE PREPARE railsafe_stmt;

-- -------------------------------------------------------------
-- 2. New columns on railway_gates
-- -------------------------------------------------------------
SET @railsafe_sql = (
    SELECT GROUP_CONCAT(CONCAT('ADD COLUMN ', d.col_def) SEPARATOR ', ')
      FROM (
            SELECT 'railway_zone' AS c, '`railway_zone` VARCHAR(80) NULL' AS col_def
            UNION ALL SELECT 'division',             '`division` VARCHAR(80) NULL'
            UNION ALL SELECT 'station',              '`station` VARCHAR(120) NULL'
            UNION ALL SELECT 'district',             '`district` VARCHAR(120) NULL'
            UNION ALL SELECT 'state',                '`state` VARCHAR(80) NULL'
            UNION ALL SELECT 'gate_type',            '`gate_type` VARCHAR(40) NULL'
            UNION ALL SELECT 'associated_routes',    '`associated_routes` VARCHAR(300) NULL'
            UNION ALL SELECT 'data_classification',  '`data_classification` VARCHAR(25) NULL DEFAULT ''VERIFIED_REFERENCE'''
            UNION ALL SELECT 'status_changed_at',    '`status_changed_at` DATETIME(6) NULL'
            UNION ALL SELECT 'expected_closing_at',  '`expected_closing_at` DATETIME(6) NULL'
            UNION ALL SELECT 'expected_opening_at',  '`expected_opening_at` DATETIME(6) NULL'
            UNION ALL SELECT 'actual_closing_at',    '`actual_closing_at` DATETIME(6) NULL'
            UNION ALL SELECT 'actual_opening_at',    '`actual_opening_at` DATETIME(6) NULL'
           ) d
      LEFT JOIN information_schema.COLUMNS ic
        ON ic.TABLE_SCHEMA = DATABASE()
       AND ic.TABLE_NAME = 'railway_gates'
       AND ic.COLUMN_NAME = d.c
     WHERE ic.COLUMN_NAME IS NULL
);
SET @railsafe_sql = IF(@railsafe_sql IS NULL, 'SELECT 1',
                       CONCAT('ALTER TABLE `railway_gates` ', @railsafe_sql));
PREPARE railsafe_stmt FROM @railsafe_sql;
EXECUTE railsafe_stmt;
DEALLOCATE PREPARE railsafe_stmt;

-- -------------------------------------------------------------
-- 3. New columns on train_master
-- -------------------------------------------------------------
SET @railsafe_sql = (
    SELECT GROUP_CONCAT(CONCAT('ADD COLUMN ', d.col_def) SEPARATOR ', ')
      FROM (
            SELECT 'source_station' AS c,       '`source_station` VARCHAR(120) NULL' AS col_def
            UNION ALL SELECT 'destination_station', '`destination_station` VARCHAR(120) NULL'
            UNION ALL SELECT 'current_station',     '`current_station` VARCHAR(120) NULL'
            UNION ALL SELECT 'next_station',        '`next_station` VARCHAR(120) NULL'
            UNION ALL SELECT 'scheduled_departure', '`scheduled_departure` VARCHAR(20) NULL'
            UNION ALL SELECT 'scheduled_arrival',   '`scheduled_arrival` VARCHAR(20) NULL'
            UNION ALL SELECT 'estimated_arrival',   '`estimated_arrival` VARCHAR(20) NULL'
            UNION ALL SELECT 'running_status',      '`running_status` VARCHAR(40) NULL DEFAULT ''Scheduled'''
            UNION ALL SELECT 'speed_kmph',          '`speed_kmph` DOUBLE NULL'
            UNION ALL SELECT 'data_classification', '`data_classification` VARCHAR(25) NULL DEFAULT ''VERIFIED_REFERENCE'''
            UNION ALL SELECT 'reference_source',    '`reference_source` VARCHAR(120) NULL'
            UNION ALL SELECT 'reference_url',       '`reference_url` VARCHAR(500) NULL'
            UNION ALL SELECT 'last_verified_at',    '`last_verified_at` DATETIME(6) NULL'
           ) d
      LEFT JOIN information_schema.COLUMNS ic
        ON ic.TABLE_SCHEMA = DATABASE()
       AND ic.TABLE_NAME = 'train_master'
       AND ic.COLUMN_NAME = d.c
     WHERE ic.COLUMN_NAME IS NULL
);
SET @railsafe_sql = IF(@railsafe_sql IS NULL, 'SELECT 1',
                       CONCAT('ALTER TABLE `train_master` ', @railsafe_sql));
PREPARE railsafe_stmt FROM @railsafe_sql;
EXECUTE railsafe_stmt;
DEALLOCATE PREPARE railsafe_stmt;

-- -------------------------------------------------------------
-- 4. New tables (all created only when missing)
-- -------------------------------------------------------------

-- 4.1 roles
CREATE TABLE IF NOT EXISTS roles (
    name        VARCHAR(40)  NOT NULL,
    description VARCHAR(255) NULL,
    built_in    TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.2 permissions
CREATE TABLE IF NOT EXISTS permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(80)  NOT NULL,
    description VARCHAR(255) NULL,
    category    VARCHAR(80)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (code),
    KEY idx_permissions_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.3 role_permissions
CREATE TABLE IF NOT EXISTS role_permissions (
    role_name     VARCHAR(40) NOT NULL,
    permission_id BIGINT      NOT NULL,
    PRIMARY KEY (role_name, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_name)
        REFERENCES roles (name) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id)
        REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.4 railway_crossings
CREATE TABLE IF NOT EXISTS railway_crossings (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    crossing_code       VARCHAR(30)   NOT NULL,
    name                VARCHAR(120)  NULL,
    gate_id             BIGINT        NULL,
    latitude            DOUBLE        NULL,
    longitude           DOUBLE        NULL,
    station             VARCHAR(120)  NULL,
    district            VARCHAR(120)  NULL,
    state               VARCHAR(80)   NULL,
    railway_zone        VARCHAR(80)   NULL,
    division            VARCHAR(80)   NULL,
    status              VARCHAR(30)   NULL,
    data_classification VARCHAR(25)   NULL,
    source_key          VARCHAR(120)  NULL,
    notes               VARCHAR(500)  NULL,
    created_at          DATETIME(6)   NULL,
    updated_at          DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crossing_code (crossing_code),
    KEY idx_crossing_gate (gate_id),
    KEY idx_crossing_zone (railway_zone, division),
    CONSTRAINT fk_crossing_gate FOREIGN KEY (gate_id)
        REFERENCES railway_gates (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.5 train_schedules
CREATE TABLE IF NOT EXISTS train_schedules (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    train_number        VARCHAR(20)  NOT NULL,
    station_name        VARCHAR(120) NOT NULL,
    station_code        VARCHAR(20)  NULL,
    `sequence`          INT          NULL,
    arrival_time        VARCHAR(20)  NULL,
    departure_time      VARCHAR(20)  NULL,
    day_offset          INT          NULL DEFAULT 0,
    data_classification VARCHAR(25)  NULL,
    reference_source    VARCHAR(120) NULL,
    reference_url       VARCHAR(500) NULL,
    created_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_train_schedule_number (train_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.6 train_positions
CREATE TABLE IF NOT EXISTS train_positions (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    train_number        VARCHAR(20)  NOT NULL,
    latitude            DOUBLE       NULL,
    longitude           DOUBLE       NULL,
    speed_kmph          DOUBLE       NULL,
    observed_at         DATETIME(6)  NULL,
    provider            VARCHAR(120) NULL,
    data_classification VARCHAR(25)  NOT NULL,
    synthetic           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_train_position_number (train_number),
    KEY idx_train_position_observed (observed_at),
    KEY idx_train_position_number_observed (train_number, observed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.7 gate_train_mapping
CREATE TABLE IF NOT EXISTS gate_train_mapping (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    gate_id             BIGINT       NOT NULL,
    train_number        VARCHAR(20)  NOT NULL,
    direction           VARCHAR(20)  NULL,
    scheduled_time      VARCHAR(20)  NULL,
    active              TINYINT(1)   NOT NULL DEFAULT 1,
    data_classification VARCHAR(25)  NULL,
    created_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_gate_train_gate (gate_id),
    KEY idx_gate_train_number (train_number),
    KEY idx_gate_train_gate_number (gate_id, train_number),
    CONSTRAINT fk_gtm_gate FOREIGN KEY (gate_id)
        REFERENCES railway_gates (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.8 route_search_history
CREATE TABLE IF NOT EXISTS route_search_history (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NULL,
    start_lat           DOUBLE       NULL,
    start_lng           DOUBLE       NULL,
    end_lat             DOUBLE       NULL,
    end_lng             DOUBLE       NULL,
    start_label         VARCHAR(160) NULL,
    end_label           VARCHAR(160) NULL,
    gate_ids            VARCHAR(500) NULL,
    distance_km         DOUBLE       NULL,
    duration_minutes    DOUBLE       NULL,
    route_count         INT          NULL,
    created_at          DATETIME(6)  NOT NULL,
    data_classification VARCHAR(25)  NULL,
    PRIMARY KEY (id),
    KEY idx_rsh_user (user_id),
    KEY idx_rsh_created (created_at),
    CONSTRAINT fk_rsh_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.9 maintenance_requests
CREATE TABLE IF NOT EXISTS maintenance_requests (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    gate_id         BIGINT        NOT NULL,
    requester_id    BIGINT        NULL,
    assigned_to_id  BIGINT        NULL,
    issue_type      VARCHAR(60)   NOT NULL,
    description     VARCHAR(1000) NOT NULL,
    priority        VARCHAR(20)   NULL,
    status          VARCHAR(30)   NOT NULL DEFAULT 'OPEN',
    resolution_note VARCHAR(1000) NULL,
    requested_at    DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NULL,
    resolved_at     DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_maintenance_gate (gate_id),
    KEY idx_maintenance_status (status),
    KEY idx_maintenance_gate_status (gate_id, status),
    CONSTRAINT fk_mr_gate FOREIGN KEY (gate_id)
        REFERENCES railway_gates (id) ON DELETE CASCADE,
    CONSTRAINT fk_mr_requester FOREIGN KEY (requester_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_mr_assignee FOREIGN KEY (assigned_to_id)
        REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.10 railway_zones
CREATE TABLE IF NOT EXISTS railway_zones (
    code         VARCHAR(20)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    headquarters VARCHAR(120) NULL,
    active       TINYINT(1)   NULL DEFAULT 1,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.11 railway_divisions
CREATE TABLE IF NOT EXISTS railway_divisions (
    code      VARCHAR(20)  NOT NULL,
    name      VARCHAR(120) NOT NULL,
    zone_code VARCHAR(20)  NOT NULL,
    active    TINYINT(1)   NULL DEFAULT 1,
    PRIMARY KEY (code),
    CONSTRAINT fk_division_zone FOREIGN KEY (zone_code)
        REFERENCES railway_zones (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.12 data_sources
CREATE TABLE IF NOT EXISTS data_sources (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    name                VARCHAR(120) NOT NULL,
    source_type         VARCHAR(40)  NOT NULL,
    url                 VARCHAR(500) NULL,
    data_mode           VARCHAR(30)  NOT NULL,
    data_classification VARCHAR(25)  NOT NULL,
    enabled             TINYINT(1)   NOT NULL DEFAULT 1,
    last_sync_at        DATETIME(6)  NULL,
    description         VARCHAR(500) NULL,
    created_at          DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_data_source_name (name),
    KEY idx_data_sources_mode (data_mode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4.13 staff_assignments
CREATE TABLE IF NOT EXISTS staff_assignments (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    gate_id         BIGINT       NOT NULL,
    active          TINYINT(1)   NOT NULL DEFAULT 1,
    assigned_at     DATETIME(6)  NOT NULL,
    unassigned_at   DATETIME(6)  NULL,
    assigned_by_id  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_staff_gate (user_id, gate_id),
    KEY idx_staff_assignment_user (user_id),
    KEY idx_staff_assignment_gate (gate_id),
    CONSTRAINT fk_staff_assignment_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_assignment_gate FOREIGN KEY (gate_id)
        REFERENCES railway_gates (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_assignment_by FOREIGN KEY (assigned_by_id)
        REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------------------------------------------
-- 5. Reference seeds (INSERT only; existing rows untouched)
--    Values mirror ReferenceDataInitializer so the application
--    finds its reference rows instead of creating duplicates.
-- -------------------------------------------------------------

-- 5.1 roles
INSERT INTO roles (name, description, built_in)
SELECT s.name, s.description, 1
  FROM (
        SELECT 'CUSTOMER' AS name,
               'Authenticated commuter: views permitted gates, trains, alerts and routes.' AS description
        UNION ALL SELECT 'USER',      'Legacy commuter role retained for existing accounts.'
        UNION ALL SELECT 'STAFF',     'Authorised railway staff: assigned gate updates and report review.'
        UNION ALL SELECT 'ADMIN',     'Operations administrator: all gates, staff accounts and reports.'
        UNION ALL SELECT 'SUPER_ADMIN','Highest access: roles, permissions, audit, restricted source review and system configuration.'
       ) s
 WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.name = s.name);

-- 5.2 permissions
INSERT INTO permissions (code, description, category)
SELECT s.code, s.description, s.category
  FROM (
        SELECT 'gate.view' AS code, 'View permitted gate information' AS description, 'gate' AS category
        UNION ALL SELECT 'gate.update',     'Update an assigned or managed gate status', 'gate'
        UNION ALL SELECT 'gate.manage',     'Create, edit and remove operational gates', 'gate'
        UNION ALL SELECT 'train.view',      'View verified train reference and authorised train information', 'train'
        UNION ALL SELECT 'route.search',    'Calculate verified alternative routes', 'route'
        UNION ALL SELECT 'report.view',     'View commuter reports', 'report'
        UNION ALL SELECT 'report.moderate', 'Approve or reject commuter reports', 'report'
        UNION ALL SELECT 'staff.manage',    'Create, update and assign STAFF accounts', 'staff'
        UNION ALL SELECT 'admin.manage',    'Manage operational administrators', 'admin'
        UNION ALL SELECT 'superadmin.access','Access the Super Admin area', 'superadmin'
        UNION ALL SELECT 'source.review',   'Review restricted external source rows', 'source'
        UNION ALL SELECT 'audit.view',      'View security and operational audit logs', 'audit'
        UNION ALL SELECT 'system.configure','View system health, configuration and data sources', 'system'
       ) s
 WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = s.code);

-- 5.3 role_permissions
-- CUSTOMER / USER -> gate.view, train.view, route.search
-- STAFF           -> + gate.update, report.view, report.moderate
-- ADMIN           -> staff level + gate.manage + staff.manage + admin.manage
-- SUPER_ADMIN     -> every permission (no exceptions)
INSERT INTO role_permissions (role_name, permission_id)
SELECT g.role_name, p.id
  FROM (
        SELECT 'CUSTOMER' AS role_name, 'gate.view' AS code
        UNION ALL SELECT 'CUSTOMER', 'train.view'
        UNION ALL SELECT 'CUSTOMER', 'route.search'
        UNION ALL SELECT 'USER',      'gate.view'
        UNION ALL SELECT 'USER',      'train.view'
        UNION ALL SELECT 'USER',      'route.search'
        UNION ALL SELECT 'STAFF',     'gate.view'
        UNION ALL SELECT 'STAFF',     'gate.update'
        UNION ALL SELECT 'STAFF',     'train.view'
        UNION ALL SELECT 'STAFF',     'route.search'
        UNION ALL SELECT 'STAFF',     'report.view'
        UNION ALL SELECT 'STAFF',     'report.moderate'
        UNION ALL SELECT 'ADMIN',     'gate.view'
        UNION ALL SELECT 'ADMIN',     'gate.update'
        UNION ALL SELECT 'ADMIN',     'gate.manage'
        UNION ALL SELECT 'ADMIN',     'train.view'
        UNION ALL SELECT 'ADMIN',     'route.search'
        UNION ALL SELECT 'ADMIN',     'report.view'
        UNION ALL SELECT 'ADMIN',     'report.moderate'
        UNION ALL SELECT 'ADMIN',     'staff.manage'
        UNION ALL SELECT 'ADMIN',     'admin.manage'
        UNION ALL SELECT 'SUPER_ADMIN', 'gate.view'
        UNION ALL SELECT 'SUPER_ADMIN', 'gate.update'
        UNION ALL SELECT 'SUPER_ADMIN', 'gate.manage'
        UNION ALL SELECT 'SUPER_ADMIN', 'train.view'
        UNION ALL SELECT 'SUPER_ADMIN', 'route.search'
        UNION ALL SELECT 'SUPER_ADMIN', 'report.view'
        UNION ALL SELECT 'SUPER_ADMIN', 'report.moderate'
        UNION ALL SELECT 'SUPER_ADMIN', 'staff.manage'
        UNION ALL SELECT 'SUPER_ADMIN', 'admin.manage'
        UNION ALL SELECT 'SUPER_ADMIN', 'superadmin.access'
        UNION ALL SELECT 'SUPER_ADMIN', 'source.review'
        UNION ALL SELECT 'SUPER_ADMIN', 'audit.view'
        UNION ALL SELECT 'SUPER_ADMIN', 'system.configure'
       ) g
  JOIN permissions p ON p.code = g.code
 WHERE NOT EXISTS (
        SELECT 1 FROM role_permissions rp
         WHERE rp.role_name = g.role_name AND rp.permission_id = p.id
       );

-- 5.4 railway_zones
INSERT INTO railway_zones (code, name, headquarters, active)
SELECT s.code, s.name, s.headquarters, 1
  FROM (
        SELECT 'SCR' AS code, 'South Central Railway' AS name, 'Secunderabad' AS headquarters
        UNION ALL SELECT 'NWR', 'North Western Railway', 'Jaipur'
        UNION ALL SELECT 'SWR', 'South Western Railway', 'Hubballi'
        UNION ALL SELECT 'ECoR','East Coast Railway', 'Bhubaneswar'
        UNION ALL SELECT 'CR',  'Central Railway', 'Mumbai'
        UNION ALL SELECT 'WR',  'Western Railway', 'Mumbai'
       ) s
 WHERE NOT EXISTS (SELECT 1 FROM railway_zones z WHERE z.code = s.code);

-- 5.5 railway_divisions (all five required SCR divisions)
INSERT INTO railway_divisions (code, name, zone_code, active)
SELECT s.code, s.name, s.zone_code, 1
  FROM (
        SELECT 'HYB' AS code, 'Hyderabad' AS name, 'SCR' AS zone_code
        UNION ALL SELECT 'SC',  'Secunderabad', 'SCR'
        UNION ALL SELECT 'BZA', 'Vijayawada',   'SCR'
        UNION ALL SELECT 'GNT', 'Guntur',       'SCR'
        UNION ALL SELECT 'GTA', 'Guntakal',     'SCR'
       ) s
 WHERE NOT EXISTS (SELECT 1 FROM railway_divisions d WHERE d.code = s.code);

-- 5.6 data_sources
-- Names/modes/classifications match ReferenceDataInitializer.
-- "OSM" is OpenStreetMap; "Sensor Webhook" is Gate Sensor Webhook.
-- The restricted dataset is explicitly SIMULATION/SIMULATED - there is no
-- fake "official Indian Railways live feed" row here.
INSERT INTO data_sources
    (name, source_type, url, data_mode, data_classification, enabled, description, created_at)
SELECT s.name, s.source_type, s.url, s.data_mode, s.data_classification, s.enabled, s.description, UTC_TIMESTAMP(6)
  FROM (
        SELECT 'OpenStreetMap' AS name, 'MAP' AS source_type,
               'https://www.openstreetmap.org' AS url, 'REAL_API' AS data_mode,
               'VERIFIED_REFERENCE' AS data_classification, 1 AS enabled,
               'Map tiles and place reference data used by the route map.' AS description
        UNION ALL SELECT 'OSRM', 'ROUTING', 'https://router.project-osrm.org', 'REAL_API',
               'VERIFIED_REFERENCE', 1,
               'Calculates route geometry, distance and duration. No route is invented when unavailable.'
        UNION ALL SELECT 'Train Reference Catalogue', 'REFERENCE', 'https://www.railyatri.in/', 'REFERENCE',
               'VERIFIED_REFERENCE', 1,
               '30 checked train names, numbers, routes and scheduled times. No live tracking claim.'
        UNION ALL SELECT 'Restricted Source Dataset', 'IMPORT', 'data/railsafe-expanded-data.json', 'SIMULATION',
               'SIMULATED', 1,
               'Retained for Super Admin source review only; never promoted to operational data.'
        UNION ALL SELECT 'Gate Sensor Webhook', 'SENSOR', '/api/live/sensor', 'OPERATIONAL',
               'VERIFIED_LIVE', 0,
               'Optional authenticated gate-status sensor. Disabled until a shared secret is configured.'
        UNION ALL SELECT 'Simulation Provider', 'SIMULATION', 'internal', 'SIMULATION',
               'SIMULATED', 1,
               'Acknowledged demonstration feed. Never presented as genuine live railway data.'
       ) s
 WHERE NOT EXISTS (SELECT 1 FROM data_sources ds WHERE ds.name = s.name);
