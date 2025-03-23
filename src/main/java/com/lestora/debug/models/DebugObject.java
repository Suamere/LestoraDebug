package com.lestora.debug.models;

public class DebugObject {
    private String shortKey;
    private String shortValue;
    private String longKey;
    private String longValue;

    private int shortKeyColor;
    private int shortValueColor;
    private int longKeyColor;
    private int longValueColor;

    private boolean shortKeyDropShadow;
    private boolean shortValueDropShadow;
    private boolean longKeyDropShadow;
    private boolean longValueDropShadow;

    // Getters for text.
    public String getShortKey() { return shortKey; }
    public String getShortValue() { return shortValue; }
    public String getLongKey() { return longKey; }
    public String getLongValue() { return longValue; }

    // Getters for color.
    public int getShortKeyColor() { return shortKeyColor; }
    public int getShortValueColor() { return shortValueColor; }
    public int getLongKeyColor() { return longKeyColor; }
    public int getLongValueColor() { return longValueColor; }

    // Getters for drop-shadow.
    public boolean isShortKeyDropShadow() { return shortKeyDropShadow; }
    public boolean isShortValueDropShadow() { return shortValueDropShadow; }
    public boolean isLongKeyDropShadow() { return longKeyDropShadow; }
    public boolean isLongValueDropShadow() { return longValueDropShadow; }

    // Main constructor with all fields including color and drop-shadow.
    public DebugObject(String shortKey, int shortKeyColor, boolean shortKeyDropShadow,
                       String shortValue, int shortValueColor, boolean shortValueDropShadow,
                       String longKey, int longKeyColor, boolean longKeyDropShadow,
                       String longValue, int longValueColor, boolean longValueDropShadow) {
        this.shortKey = checkAndTruncate(shortKey, "shortKey", 8);
        this.shortKeyColor = shortKeyColor;
        this.shortKeyDropShadow = shortKeyDropShadow;

        this.shortValue = checkAndTruncate(shortValue, "shortValue", 20);
        this.shortValueColor = shortValueColor;
        this.shortValueDropShadow = shortValueDropShadow;

        this.longKey = checkAndTruncate(longKey, "longKey", 30);
        this.longKeyColor = longKeyColor;
        this.longKeyDropShadow = longKeyDropShadow;

        this.longValue = checkAndTruncate(longValue, "longValue", 40);
        this.longValueColor = longValueColor;
        this.longValueDropShadow = longValueDropShadow;
    }

    // Constructor with only plain String values; defaults to white and no drop shadow.
    public DebugObject(String shortKey, String shortValue, String longKey, String longValue) {
        this(shortKey, 0xFFFFFF, false,
                shortValue, 0xFFFFFF, false,
                longKey, 0xFFFFFF, false,
                longValue, 0xFFFFFF, false);
    }

    // Constructor with only short values; uses them for the long values.
    public DebugObject(String shortKey, String shortValue) {
        this(shortKey, shortValue, shortKey, shortValue);
    }

    // Setters for all fields.
    public void setShortKey(String shortKey) {
        this.shortKey = checkAndTruncate(shortKey, "shortKey", 8);
    }

    public void setShortValue(String shortValue) {
        this.shortValue = checkAndTruncate(shortValue, "shortValue", 20);
    }

    public void setLongKey(String longKey) {
        this.longKey = checkAndTruncate(longKey, "longKey", 30);
    }

    public void setLongValue(String longValue) {
        this.longValue = checkAndTruncate(longValue, "longValue", 40);
    }

    public void setShortKeyColor(int shortKeyColor) {
        this.shortKeyColor = shortKeyColor;
    }

    public void setShortValueColor(int shortValueColor) {
        this.shortValueColor = shortValueColor;
    }

    public void setLongKeyColor(int longKeyColor) {
        this.longKeyColor = longKeyColor;
    }

    public void setLongValueColor(int longValueColor) {
        this.longValueColor = longValueColor;
    }

    public void setShortKeyDropShadow(boolean shortKeyDropShadow) {
        this.shortKeyDropShadow = shortKeyDropShadow;
    }

    public void setShortValueDropShadow(boolean shortValueDropShadow) {
        this.shortValueDropShadow = shortValueDropShadow;
    }

    public void setLongKeyDropShadow(boolean longKeyDropShadow) {
        this.longKeyDropShadow = longKeyDropShadow;
    }

    public void setLongValueDropShadow(boolean longValueDropShadow) {
        this.longValueDropShadow = longValueDropShadow;
    }

    private static String checkAndTruncate(String input, String fieldName, int maxLength) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or empty");
        }
        if (input.length() > maxLength) {
            input = input.substring(0, maxLength);
        }
        return input;
    }
}
