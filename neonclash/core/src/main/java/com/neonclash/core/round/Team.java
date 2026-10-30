package com.neonclash.core.round;

public enum Team {
    RED, BLUE;

    public Team opponent() {
        return this == RED ? BLUE : RED;
    }

    public static Team parse(String s) {
        return switch (s.toLowerCase(java.util.Locale.ROOT)) {
            case "red" -> RED;
            case "blue" -> BLUE;
            default -> throw new IllegalArgumentException("unknown team: " + s);
        };
    }
}
