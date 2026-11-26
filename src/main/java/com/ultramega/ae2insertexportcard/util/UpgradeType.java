package com.ultramega.ae2insertexportcard.util;

public enum UpgradeType {
    INSERT(0, "insert"),
    EXPORT(1, "export");

    private final int id;
    private final String name;

    UpgradeType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
