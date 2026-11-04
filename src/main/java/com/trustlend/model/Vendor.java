package com.trustlend.model;

import java.io.Serial;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * A street vendor or small shopkeeper. Inherits identity data from {@link Person}.
 * The trust score itself is NOT stored here; it is computed by the engine and
 * cached in {@link #getCurrentScore()} purely for display.
 */
public class Vendor extends Person {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Static (class-level) counter: how many Vendor objects were created in this JVM. */
    private static int instancesCreated = 0;

    private VendorCategory category;
    private String marketArea;
    private String city;
    private int yearsInBusiness;
    private LocalDate joinDate;
    private boolean active = true;

    /** Display-only cache, filled by the service layer after scoring. */
    private transient int currentScore;
    private transient RiskBand riskBand;

    public Vendor(String name, String phone, VendorCategory category,
                  String marketArea, String city, int yearsInBusiness, LocalDate joinDate) {
        this(0, name, phone, category, marketArea, city, yearsInBusiness, joinDate, true);
    }

    public Vendor(long id, String name, String phone, VendorCategory category, String marketArea,
                  String city, int yearsInBusiness, LocalDate joinDate, boolean active) {
        super(id, name, phone);
        this.category = Objects.requireNonNull(category);
        this.marketArea = Objects.requireNonNull(marketArea);
        this.city = Objects.requireNonNull(city);
        this.yearsInBusiness = yearsInBusiness;
        this.joinDate = Objects.requireNonNull(joinDate);
        this.active = active;
        instancesCreated++;
    }

    public static int getInstancesCreated() {
        return instancesCreated;
    }

    @Override
    public String describeRole() {
        return category.getLabel() + " vendor at " + marketArea + ", " + city;
    }

    /** Months the vendor has been on the TrustLend platform. */
    public int monthsOnPlatform(LocalDate today) {
        Period p = Period.between(joinDate, today);
        return Math.max(0, p.getYears() * 12 + p.getMonths());
    }

    public VendorCategory getCategory() { return category; }
    public void setCategory(VendorCategory category) { this.category = category; }

    public String getMarketArea() { return marketArea; }
    public void setMarketArea(String marketArea) { this.marketArea = marketArea; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public int getYearsInBusiness() { return yearsInBusiness; }
    public void setYearsInBusiness(int yearsInBusiness) { this.yearsInBusiness = yearsInBusiness; }

    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getCurrentScore() { return currentScore; }
    public void setCurrentScore(int currentScore) { this.currentScore = currentScore; }

    public RiskBand getRiskBand() { return riskBand; }
    public void setRiskBand(RiskBand riskBand) { this.riskBand = riskBand; }
}
