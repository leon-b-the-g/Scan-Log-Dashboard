package com.chordata.scandash.model;

/**
 * A site (school) served by the catering operation.
 */
public record School(String id, String name) {

    @Override
    public String toString() {
        return name;
    }
}
