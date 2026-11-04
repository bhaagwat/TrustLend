package com.trustlend.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * A directed, weighted edge in the trust graph: "voucher" vouches for "vouchee"
 * with a strength from 1 (weak acquaintance) to 5 (would stake my own credit).
 */
public class Vouch implements Identifiable, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int MIN_STRENGTH = 1;
    public static final int MAX_STRENGTH = 5;

    public enum Relationship {
        NEIGHBOURING_STALL("Neighbouring stall"),
        SUPPLIER("Supplier"),
        CUSTOMER("Regular customer"),
        SELF_HELP_GROUP("Self-help group member"),
        MARKET_ASSOCIATION("Market association"),
        FAMILY_FRIEND("Family friend");

        private final String label;
        Relationship(String label) { this.label = label; }
        public String getLabel() { return label; }
        @Override public String toString() { return label; }
    }

    private long id;
    private final long voucherId;
    private final long voucheeId;
    private int strength;
    private Relationship relationship;
    private final LocalDate createdDate;

    public Vouch(long id, long voucherId, long voucheeId, int strength,
                 Relationship relationship, LocalDate createdDate) {
        this.id = id;
        this.voucherId = voucherId;
        this.voucheeId = voucheeId;
        this.strength = strength;
        this.relationship = relationship;
        this.createdDate = createdDate;
    }

    /** Edge weight in [0.2, 1.0] used by the graph. */
    public double weight() {
        return strength / (double) MAX_STRENGTH;
    }

    @Override public long getId() { return id; }
    @Override public void setId(long id) { this.id = id; }

    public long getVoucherId() { return voucherId; }
    public long getVoucheeId() { return voucheeId; }
    public int getStrength() { return strength; }
    public void setStrength(int strength) { this.strength = strength; }
    public Relationship getRelationship() { return relationship; }
    public void setRelationship(Relationship relationship) { this.relationship = relationship; }
    public LocalDate getCreatedDate() { return createdDate; }
}
