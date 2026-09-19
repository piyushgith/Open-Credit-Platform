package com.opencredit.platform.offer.model;

/**
 * {@code ACTIVE -> SELECTED}. At most one offer per application ever reaches {@code SELECTED} —
 * enforced by a partial unique index on {@code offer (application_id) WHERE status = 'SELECTED'}.
 */
public enum OfferStatus {
    ACTIVE,
    SELECTED
}
