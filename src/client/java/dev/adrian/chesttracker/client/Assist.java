package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.config.ChestTrackerConfig;

/**
 * The one gate in front of everything this mod does <em>to</em> the player
 * rather than for them.
 *
 * <p>Two features move the player instead of merely drawing something: turning
 * to face a match, and opening a container already within reach. Both are
 * genuinely useful and both are indistinguishable, from the far end of a
 * connection, from an aim-assist and an auto-interact. A server sees a view
 * that snaps towards a block it never told the client was interesting, followed
 * by a perfectly aimed interaction packet - which is the shape of thing
 * anti-cheat is built to catch, and it does not get to hear the reason.
 *
 * <p>So they are off on anything that is not the player's own world, and there
 * is no index on a vanilla server for them to act on anyway - the client-side
 * fallback deliberately produces guidance and nothing else.
 *
 * <p>{@code hasSingleplayerServer()} is the test rather than "is this
 * multiplayer": it is true for the host of a world opened to LAN, whose world
 * this still is, and false for every connection out to somebody else's.
 */
public final class Assist {

    private Assist() {}

    /**
     * Whether the mod may move the player right now.
     *
     * <p>Always true in the player's own world. On a server it takes an
     * explicit opt-in - either the blanket one, which defaults off and says why
     * in the settings, or that server appearing on the trusted list.
     */
    public static boolean allowed() {
        if (!Session.active()) return false;
        if (Session.ownWorld()) return true;
        // A named server can be trusted on its own, without opening the two
        // features up on every server the player ever joins.
        return ChestTrackerConfig.get().assistAllowedOn(Session.serverAddress());
    }
}
