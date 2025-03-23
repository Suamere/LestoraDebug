package com.lestora.debug.models;

import java.util.Objects;
import java.util.function.Supplier;

public class DebugSupplier {
    private final Supplier<DebugObject> supplier;
    private final String key;
    private final int priority;

    public Supplier<DebugObject> getSupplier() { return supplier; }
    public String getKey() { return key; }
    public int getPriority() { return priority; }

    public DebugSupplier(String key, int priority, Supplier<DebugObject> supplier) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
        this.priority = priority;
        this.supplier = Objects.requireNonNull(supplier, "supplier cannot be null");
    }
}