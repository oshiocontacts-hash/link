package com.neonclash.core.style;

public enum StyleRank {
    D, C, B, A, S;

    public StyleRank up() {
        return this == S ? S : values()[ordinal() + 1];
    }

    public StyleRank down() {
        return this == D ? D : values()[ordinal() - 1];
    }
}
