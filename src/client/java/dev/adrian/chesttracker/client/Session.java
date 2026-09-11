package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.client.net.ServerLink;
import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/**
 * Where this client is connected, and whether the mod may act there.
 *
 * <p>Both questions are asked from a dozen places - the key, the button, the
 * observer, the highlight - and both have exactly one right answer per
 * connection, so they live here rather than being worked out again at each
 * call site with slightly different edge cases.
 *
 * <h2>The master switch</h2>
 *
 * <p>Turning the mod off has to mean off. Not "hidden", not "the button is
 * gone but the index still fills": a player switching it off for a server is
 * answering a question about what their client is allowed to do there, and
 * anything still running behind the setting would be the mod answering it for
 * them. So every entry point checks {@link #active()} and stops.
 */
public final class Session {

    private Session() {}

    /**
     * The address of the server currently joined, or blank in singleplayer.
     *
     * <p>The address rather than the entry's name: the name is whatever the
     * player typed in their server list and two of them can share it, while
     * the address is what actually decides which server this is.
     */
    public static String serverAddress() {
        Minecraft client = Minecraft.getInstance();
        if (client.hasSingleplayerServer()) return "";

        ServerData data = client.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) return data.ip;

        // A direct connect, or a transfer, can leave the server-list entry
        // behind. The connection itself always knows where it went.
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

    /** True in the player's own world, singleplayer or hosting a LAN game. */
    public static boolean ownWorld() {
        return Minecraft.getInstance().hasSingleplayerServer();
    }

    /**
     * Whether the mod does anything at all right now.
     *
     * <p>Off either because the player switched it off outright, or because
     * this is one of the servers they listed as off-limits. The two are one
     * answer here because every caller wants the same thing from both.
     */
    public static boolean active() {
        long now = System.currentTimeMillis();
        if (now - activeAt < ACTIVE_CACHE_MS) return activeAnswer;
        activeAnswer = computeActive();
        activeAt = now;
        return activeAnswer;
    }

    /**
     * How long an answer to {@link #active()} is reused.
     *
     * <p>It is asked once per frame by the highlight and once per tick by
     * several others, and answering it walks the off-list: the address is
     * lower-cased, its port stripped and its trailing dots trimmed, and the
     * same is done to every entry it is compared against. That is a handful of
     * throwaway strings per frame to answer a question whose answer changes
     * when the player edits a setting.
     *
     * <p>A twentieth of a second rather than a tick, so it stays correct while
     * the game is paused - which is exactly when the settings screen that can
     * change the answer is open.
     */
    private static final long ACTIVE_CACHE_MS = 50;

    private static boolean activeAnswer;

    /**
     * When {@link #activeAnswer} was worked out.
     *
     * <p>Zero, not {@code Long.MIN_VALUE}: the test below is
     * {@code now - activeAt}, and subtracting the minimum from a current
     * millisecond count overflows to a negative number - which reads as "just
     * computed", so the cache never filled and every caller was told the mod
     * was switched off, permanently.
     */
    private static long activeAt;

    private static boolean computeActive() {
        ChestTrackerConfig config = ChestTrackerConfig.get();
        if (!config.enabled) return false;
        if (ownWorld()) return true;

        String address = serverAddress();
        if (!address.isEmpty() && config.disabledOn(address)) return false;

        // A server without the mod, and the player has said not to keep an
        // index for those. There is then nothing here for the mod to search, so
        // it stops rather than offering a screen that can only ever be empty.
        // Deliberately not applied while the answer is still unknown: a join
        // takes a moment to settle and turning off during it would flicker.
        return config.clientSideIndex || ServerLink.state() != ServerLink.State.ABSENT;
    }

    /** Why the mod is doing nothing, in a few words. */
    public static String inactiveReason() {
        ChestTrackerConfig config = ChestTrackerConfig.get();
        if (!config.enabled) return "Switched off in the settings.";

        String address = serverAddress();
        if (!address.isEmpty() && config.disabledOn(address)) {
            return "Switched off on " + ChestTrackerConfig.host(address) + ".";
        }
        if (!config.clientSideIndex) {
            return "This server does not have the mod, and indexing those is off.";
        }
        return "Switched off in the settings.";
    }

    // --- Telling the player, once ------------------------------------------

    /**
     * Whether the last tick found the mod active, so the moment it stops can be
     * noticed.
     */
    private static boolean wasActive = true;

    /** When a key press last re-stated that the mod is off. */
    private static long lastReminderAt;

    /**
     * Long enough that the reminder cannot become an error message the player
     * is fighting, short enough that it is there when they wonder why.
     */
    private static final long REMINDER_INTERVAL_MS = 30_000;

    /**
     * Notices the mod switching itself off and says so, once.
     *
     * <p>A toast rather than a line above the hotbar. Being off is not an
     * error, and an error is what the action bar looks like - the mod should
     * read as absent here, not as broken. Called from the client tick.
     */
    public static void tick() {
        // Only once there is a world. At the title screen there is no server to
        // be off on, and a toast there would be the mod announcing itself for
        // no reason on every launch.
        if (Minecraft.getInstance().level == null) return;

        boolean now = active();
        if (now != wasActive) {
            wasActive = now;
            if (!now) announce();
        }
    }

    /**
     * The player pressed a key that does nothing here.
     *
     * <p>The key itself stays silent - the point is that the mod is not there -
     * but somebody pressing it is asking a question, and answering it once in a
     * while is better than never. Throttled so holding the key cannot spam it.
     */
    public static void remind() {
        long now = System.currentTimeMillis();
        if (now - lastReminderAt < REMINDER_INTERVAL_MS) return;
        lastReminderAt = now;
        announce();
    }

    private static void announce() {
        lastReminderAt = System.currentTimeMillis();
        ClientCompat.toast(Component.literal("ChestTracker is off here"),
                Component.literal(inactiveReason()));
    }

    /**
     * The mod this one replaces, if it is also installed.
     *
     * <p>This used to be a {@code breaks} declaration in the mod metadata,
     * which refused to launch - correctly, since two mods binding the same keys
     * and drawing the same button on the same window is nobody's idea of a good
     * time. The trouble is that Fabric Loader writes that message itself, and
     * what it writes is "replace ChestTracker with a version compatible with
     * chesttracker 2.8.4" - which points at the wrong mod. Somebody reading it
     * goes looking for a build of this mod that does not exist, when what they
     * need is to remove the other one.
     *
     * <p>So the declaration is gone and the game starts. It says the true thing
     * instead, in the log and once on screen, and the player decides.
     */
    private static final String REPLACED_MOD = "chesttracker";

    private static boolean warnedAboutConflict;

    /**
     * Says, once, if the mod this replaces is installed alongside it.
     *
     * <p>Held until the player is in a world: a toast at launch is drawn behind
     * the loading screen and seen by nobody.
     */
    public static void warnAboutConflict() {
        if (warnedAboutConflict) return;
        // The promise in the javadoc, which nothing was keeping: the flag below
        // was spent on the first client tick, which happens at the title screen,
        // so the toast was raised where there was no world to raise it over and
        // the one time it would have been read never came.
        if (Minecraft.getInstance().level == null) return;
        // Set before the test, not after: when the other mod is absent - which
        // is the normal case - this must stop asking rather than run a loader
        // lookup on every tick for the rest of the session.
        warnedAboutConflict = true;
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(REPLACED_MOD)) return;

        dev.adrian.chesttracker.ChestTracker.LOG.warn(
                "'Chest Tracker (Unofficial port)' ({}) is installed alongside this mod. "
                        + "Delete or disable it - ChestTracker replaces it, and running both "
                        + "gives you two search screens bound to the same keys.", REPLACED_MOD);
        ClientCompat.toast(Component.literal("Two chest trackers installed"),
                Component.literal("Delete or disable 'Chest Tracker (Unofficial port)'"));
    }

    /**
     * Whether the entity warning has been given for this connection.
     *
     * <p>Once per server, not once per session: the answer is about the server
     * just joined, and joining a different one is a new answer.
     */
    private static boolean warnedAboutEntities;

    /**
     * Says, once per connection, that minecarts and boats are not being read
     * here.
     *
     * <p>A second warning beside the first, and it earns its place: this is the
     * one kind of container whose absence is silent. A chest the mod has not
     * seen shows as nothing and the empty grid says why. A chest minecart the
     * mod is deliberately not reading looks exactly like a chest minecart that
     * is empty - the player gets a confident wrong answer with nothing to
     * suggest a setting was involved.
     */
    public static void warnAboutEntities() {
        if (warnedAboutEntities || !active()) return;
        if (Minecraft.getInstance().level == null) return;
        // Spent, not skipped: whether this is the player's own world cannot
        // change without a new connection, and returning without spending it
        // left this whole check running on every tick for the session.
        if (ownWorld()) {
            warnedAboutEntities = true;
            return;
        }

        ChestTrackerConfig config = ChestTrackerConfig.get();
        if (!config.trackEntityContainers) return;
        // A server running the mod reads its own entities, so there is nothing
        // to warn about; only the client-side fallback holds them back.
        if (ServerLink.state() != ServerLink.State.ABSENT) return;
        if (config.entityContainersOnVanillaServers) return;

        warnedAboutEntities = true;
        ClientCompat.toast(Component.literal("Minecarts are not tracked here"),
                Component.literal("They move, and this server has no index to follow them"));
    }

    /** A new connection is a new answer; the next one is worth announcing again. */
    public static void forget() {
        wasActive = true;
        lastReminderAt = 0L;
        warnedAboutEntities = false;
        // A new connection is a new answer, and the cached one is about the
        // server being left. Zero rather than the minimum; see activeAt.
        activeAt = 0L;
    }
}
