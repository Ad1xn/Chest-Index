package dev.adrian.chestindex.core.model;

public enum Origin {
    NATURAL,
    PLAYER_PLACED,
    UNKNOWN;

    public Origin merge(Origin observed) {
        if (observed == PLAYER_PLACED) return PLAYER_PLACED;
        if (this == PLAYER_PLACED) return PLAYER_PLACED;
        if (observed == NATURAL || this == NATURAL) return NATURAL;
        return UNKNOWN;
    }
}
