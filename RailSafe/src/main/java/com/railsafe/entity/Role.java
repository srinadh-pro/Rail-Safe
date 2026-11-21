package com.railsafe.entity;

/**
 * Application roles.
 * CUSTOMER    - customer-facing commuter: views permitted gates, trains, alerts and routes.
 * USER        - legacy commuter role retained for existing accounts; same customer permissions.
 * STAFF       - authorised railway staff: can update gates and approve reports.
 * ADMIN       - full access to gates and reports, including deleting gates.
 * SUPER_ADMIN - every ADMIN capability plus management of ADMIN accounts and Source Review.
 */
public enum Role {
    CUSTOMER,
    USER,
    STAFF,
    ADMIN,
    SUPER_ADMIN
}
