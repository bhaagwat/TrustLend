package com.trustlend.model;

/** A loan officer / admin who logs into the desktop app. */
public class User implements Identifiable {

    public enum Role { ADMIN, OFFICER }

    private long id;
    private final String username;
    private final String passwordHash;
    private final String salt;
    private final String fullName;
    private final Role role;

    public User(long id, String username, String passwordHash, String salt, String fullName, Role role) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.fullName = fullName;
        this.role = role;
    }

    @Override public long getId() { return id; }
    @Override public void setId(long id) { this.id = id; }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
}
