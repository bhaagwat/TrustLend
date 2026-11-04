package com.trustlend.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Abstract base class for every human in the system (vendors today, maybe guarantors later).
 * Demonstrates abstraction, encapsulation (private fields + accessors) and
 * a protected constructor that only sub-classes can call.
 */
public abstract class Person implements Identifiable, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long id;
    private String name;
    private String phone;

    protected Person(long id, String name, String phone) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.phone = Objects.requireNonNull(phone, "phone");
    }

    /** Every concrete person must describe their role in one short phrase. */
    public abstract String describeRole();

    /** Two-letter initials used for avatars in the UI. */
    public String getInitials() {
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return sb.toString();
    }

    @Override public long getId() { return id; }
    @Override public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = Objects.requireNonNull(name); }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = Objects.requireNonNull(phone); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person other = (Person) o;
        // Unsaved objects are only equal to themselves
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return id != 0 ? Long.hashCode(id) : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return name;
    }
}
