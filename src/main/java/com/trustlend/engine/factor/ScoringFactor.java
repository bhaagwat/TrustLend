package com.trustlend.engine.factor;

import com.trustlend.engine.VendorProfile;

import java.time.LocalDate;

/**
 * Strategy interface: one independent signal about a vendor's creditworthiness.
 * New signals (e.g. utility-bill payments) can be added without touching the engine.
 */
public interface ScoringFactor {

    /** Short key matching the config weight, e.g. "consistency" → weight.consistency. */
    String key();

    String displayName();

    /** @return a value in [0, 1] plus human-readable reasons */
    FactorResult evaluate(VendorProfile profile, LocalDate today);
}
