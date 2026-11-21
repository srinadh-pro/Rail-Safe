package com.railsafe.entity;

/**
 * Shows how trustworthy a gate record is.
 * DEMO       - fictional demonstration data.
 * UNVERIFIED - entered but not yet verified by staff.
 * VERIFIED   - reviewed/updated by an authorised staff member.
 */
public enum VerificationStatus {
    DEMO,
    UNVERIFIED,
    VERIFIED
}
