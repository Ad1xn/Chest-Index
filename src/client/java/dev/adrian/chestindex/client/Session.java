package dev.adrian.chestindex.client;

import dev.adrian.chestindex.client.net.ServerLink;
import dev.adrian.chestindex.client.platform.ClientCompat;
import dev.adrian.chestindex.config.ChestIndexConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

public final class Session {
    private Session() {}

    public static String serverAddress() {
        Minecraft client = Minecraft.getInstance();
        if (client.hasSingleplayerServer()) return "";

        ServerData data = client.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) return data.ip;

        var connection = client.getConnection();
        if (connection != null) {
            ServerData listed = connection.getServerData();
            if (listed != null && listed.ip != null && !listed.ip.isBlank()) return listed.ip;
            var address = connection.getConnection() == null
                    ? null : connection.getConnection().getRemoteAddress();
            if (address != null) return address.toString();
        }
        return "";
    }

    public static boolean ownWorld() {
        return Minecraft.getInstance().hasSingleplayerServer();
    }

    public static boolean active() {
        long now = System.currentTimeMillis();
        if (now - activeAt < ACTIVE_CACHE_MS) return activeAnswer;
        activeAnswer = computeActive();
        activeAt = now;
        return activeAnswer;
    }

    private static final long ACTIVE_CACHE_MS = 50;

    private static boolean activeAnswer;

    private static long activeAt;

    private static boolean computeActive() {
        ChestIndexConfig config = ChestIndexConfig.get();
        if (!config.enabled) return false;
        if (ownWorld()) return true;

        String address = serverAddress();
        if (!address.isEmpty() && config.disabledOn(address)) return false;

        return config.clientSideIndex || ServerLink.state() != ServerLink.State.ABSENT;
    }

    public static String inactiveReason() {
        ChestIndexConfig config = ChestIndexConfig.get();
        if (!config.enabled) return "Switched off in the settings.";

        String address = serverAddress();
        if (!address.isEmpty() && config.disabledOn(address)) {
            return "Switched off on " + ChestIndexConfig.host(address) + ".";
        }
        if (!config.clientSideIndex) {
            return "This server doesn't have the mod, and indexing is off for those.";
        }
        return "Switched off in the settings.";
    }

    private static boolean wasActive = true;

    private static long lastReminderAt;

    private static final long REMINDER_INTERVAL_MS = 30_000;

    public static void tick() {
        if (Minecraft.getInstance().level == null) return;

        boolean now = active();
        if (now != wasActive) {
            wasActive = now;
            if (!now) announce();
        }
    }

    public static void remind() {
        long now = System.currentTimeMillis();
        if (now - lastReminderAt < REMINDER_INTERVAL_MS) return;
        lastReminderAt = now;
        announce();
    }

    private static void announce() {
        lastReminderAt = System.currentTimeMillis();
        ClientCompat.toast(Component.literal("ChestIndex is off here"),
                Component.literal(inactiveReason()));
    }

    private static final String REPLACED_MOD = "chesttracker";

    private static final String OWN_OLD_ID = "chest-tracker";

    private static boolean warnedAboutConflict;

    public static void warnAboutConflict() {
        if (warnedAboutConflict) return;
        if (Minecraft.getInstance().level == null) return;
        warnedAboutConflict = true;

        net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();

        if (loader.isModLoaded(OWN_OLD_ID)) {
            dev.adrian.chestindex.ChestIndex.LOG.warn(
                    "An old ChestTracker build ({}) is also installed. Delete the old jar, "
                            + "your settings and indexes were already moved over.", OWN_OLD_ID);
            ClientCompat.toast(Component.literal("An older ChestIndex is still installed"),
                    Component.literal("Delete the old chest-tracker jar from mods"));
            return;
        }

        if (!loader.isModLoaded(REPLACED_MOD)) return;

        dev.adrian.chestindex.ChestIndex.LOG.warn(
                "'Chest Tracker (Unofficial port)' ({}) is also installed. "
                        + "Remove it, ChestIndex replaces it.", REPLACED_MOD);
        ClientCompat.toast(Component.literal("Another chest tracker is installed"),
                Component.literal("Delete or disable 'Chest Tracker (Unofficial port)'"));
    }

    private static boolean warnedAboutEntities;

    public static void warnAboutEntities() {
        if (warnedAboutEntities || !active()) return;
        if (Minecraft.getInstance().level == null) return;
        if (ownWorld()) {
            warnedAboutEntities = true;
            return;
        }

        ChestIndexConfig config = ChestIndexConfig.get();
        if (!config.trackEntityContainers) return;
        if (ServerLink.state() != ServerLink.State.ABSENT) return;
        if (config.readsEntitiesOnVanillaServers()) return;

        warnedAboutEntities = true;
        ClientCompat.toast(Component.literal("Minecarts are not tracked here"),
                Component.literal("They move, and this server can't track them"));
    }

    public static void forget() {
        wasActive = true;
        lastReminderAt = 0L;
        warnedAboutEntities = false;
        activeAt = 0L;
    }
}
