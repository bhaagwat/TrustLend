package com.trustlend.model;

/** Kind of informal business a vendor runs. */
public enum VendorCategory {
    FRUITS_VEGETABLES("Fruits & Vegetables", "🥕"),
    STREET_FOOD("Street Food", "🍲"),
    TEA_STALL("Tea Stall", "☕"),
    KIRANA("Kirana / General Store", "🛒"),
    TAILORING("Tailoring", "🧵"),
    FLOWERS("Flowers", "🌼"),
    MOBILE_REPAIR("Mobile Repair", "📱"),
    CLOTHING("Clothing & Textiles", "👕"),
    HANDICRAFTS("Handicrafts", "🏺"),
    OTHER("Other", "🏪");

    private final String label;
    private final String icon;

    VendorCategory(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String getLabel() { return label; }
    public String getIcon() { return icon; }

    @Override
    public String toString() { return label; }
}
