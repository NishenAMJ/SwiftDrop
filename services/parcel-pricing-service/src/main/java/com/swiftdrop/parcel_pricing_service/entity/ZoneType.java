package com.swiftdrop.parcel_pricing_service.entity;

/**
 * How a zone is classified geographically. The team has not decided between
 * districts, postal codes or other boundaries, so this is only a label for now.
 * Add a value here (and in the migration's CHECK constraint) to support a new
 * classification.
 */
public enum ZoneType {
    GENERAL,
    DISTRICT,
    POSTAL_CODE
}
