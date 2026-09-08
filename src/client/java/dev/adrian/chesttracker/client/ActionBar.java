package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.client.platform.ClientCompat;
import net.minecraft.network.chat.Component;

/**
 * The one strip of screen this mod writes to, and who gets it.
 *
 * <p>There are two kinds of message and they were competing for the same line.
 * One is an <em>answer</em> - "Looking for Diamond...", "it is in your ender
 * chest", "nothing indexed holds this" - written once, in reply to something
 * the player just did. The other is <em>guidance</em>, the bearing and distance
 * to the container being walked to, which the highlight rewrites twenty times a
 * second for as long as it is up.
 *
 * <p>Written to the same place with no arbitration, guidance always wins: an
 * answer lasted one tick before the highlight painted over it. So a search
 * started while an earlier one was still highlighted appeared to say nothing at
 * all - and worse, what it said nothing over was the <em>previous</em> item's
 * name, because the old highlight goes on describing its own target until the
 * new reply lands.
 *
 * <p>So an answer holds the line for long enough to be read, and guidance waits.
 * That is the right way round: guidance is a fact that is still true a second
 * later and can be restated whenever, while an answer is a reply to one action
 * and is gone forever if it is missed.
 */
public final class ActionBar {

    private ActionBar() {}

    /**
     * How long an answer keeps the line.
     *
     * <p>Two seconds is about what it takes to read a short sentence and look
     * away. Long enough to survive the highlight, short enough that guidance is
     * back before the player has walked anywhere.
     */
    private static final long HOLD_MS = 2000;

    /** When guidance may write again. Client thread only, but read on it too. */
    private static volatile long heldUntil;

    /**
     * A reply to something the player just did. Always shown, and holds the
     * line against guidance for a moment afterwards.
     */
    public static void say(Component message) {
        heldUntil = System.currentTimeMillis() + HOLD_MS;
        ClientCompat.actionBar(message);
    }

    /**
     * The repeating bearing-and-distance line, which yields to an answer.
     *
     * <p>Dropped rather than queued while an answer is up. It is rewritten
     * every tick anyway, so the next one along says the same thing - and by
     * then it will be describing whatever the new search found rather than the
     * old one.
     */
    public static void guidance(Component message) {
        if (System.currentTimeMillis() < heldUntil) return;
        ClientCompat.actionBar(message);
    }
}
