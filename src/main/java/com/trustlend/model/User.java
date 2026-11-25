package com.trustlend.model;

/** A loan officer / admin who logs into the desktop app. */
public class User implements Identifiable {

    /** Who is using TrustLend. OFFICER is kept so databases from older versions still load. */
    public enum Role {
        ADMIN("TrustLend HQ"), LENDER("Lender"), BORROWER("Borrower"), OFFICER("Loan officer");

        private final String label;

        Role(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    private long id;
    private final String username;
    private final String passwordHash;
    private final String salt;
    private final String fullName;
    private final Role role;
    private long vendorId;          // BORROWER: their vendor profile
    private String organisation;    // LENDER: e.g. "Meena Self-Help Group"
    private String lenderType;      // LENDER: Moneylender, Self-help group, Wholesaler, Microfinance

    public User(long id, String username, String passwordHash, String salt, String fullName, Role role) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.fullName = fullName;
        this.role = role;
    }

    public User(long id, String username, String passwordHash, String salt, String fullName, Role role,
                long vendorId, String organisation, String lenderType) {
        this(id, username, passwordHash, salt, fullName, role);
        this.vendorId = vendorId;
        this.organisation = organisation;
        this.lenderType = lenderType;
    }

    @Override public long getId() { return id; }
    @Override public void setId(long id) { this.id = id; }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public long getVendorId() { return vendorId; }
    public void setVendorId(long vendorId) { this.vendorId = vendorId; }
    public String getOrganisation() { return organisation; }
    public String getLenderType() { return lenderType; }

    public boolean isAdmin() { return role == Role.ADMIN || role == Role.OFFICER; }
    public boolean isLender() { return role == Role.LENDER; }
    public boolean isBorrower() { return role == Role.BORROWER; }

    /** Name shown on screen: the organisation for lenders, the person otherwise. */
    public String getDisplayName() {
        return isLender() && organisation != null && !organisation.isBlank() ? organisation : fullName;
    }
}
