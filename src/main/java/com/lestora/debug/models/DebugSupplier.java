package com.lestora.debug.models;

import java.util.Objects;
import java.util.function.Supplier;

public record DebugSupplier(String key, int priority, Supplier<DebugObject> supplier) {
    public DebugSupplier(String key, int priority, Supplier<DebugObject> supplier) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
        this.priority = priority;
        this.supplier = Objects.requireNonNull(supplier, "supplier cannot be null");
    }
}