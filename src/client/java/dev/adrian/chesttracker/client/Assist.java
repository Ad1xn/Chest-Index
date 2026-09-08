package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.config.ChestTrackerConfig;

public final class Assist {

    private Assist() {}

    public static boolean allowed() {
        if (!Session.active()) return false;
        if (Session.ownWorld()) return true;
        return ChestTrackerConfig.get().assistAllowedOn(Session.serverAddress());
    }
}
