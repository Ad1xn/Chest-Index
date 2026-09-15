package dev.adrian.chestindex.client;

import dev.adrian.chestindex.config.ChestIndexConfig;

public final class Assist {

    private Assist() {}

    public static boolean allowed() {
        if (!Session.active()) return false;
        if (Session.ownWorld()) return true;
        return ChestIndexConfig.get().assistAllowedOn(Session.serverAddress());
    }
}
