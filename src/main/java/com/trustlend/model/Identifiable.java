package com.trustlend.model;

/**
 * Anything that is stored in the database with a numeric primary key.
 * Used as an upper bound in the generic DAO layer ({@code GenericDao<T extends Identifiable, ...>}).
 */
public interface Identifiable {

    /** @return database id, or 0 if the entity has not been saved yet */
    long getId();

    void setId(long id);

    /** Default method (Java 8+) shared by every implementing class. */
    default boolean isPersisted() {
        return getId() > 0;
    }
}
