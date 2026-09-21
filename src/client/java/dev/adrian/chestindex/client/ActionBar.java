package dev.adrian.chestindex.client;

import dev.adrian.chestindex.client.platform.ClientCompat;
import net.minecraft.network.chat.Component;

public final class ActionBar {
    private ActionBar() {}

    private static final long HOLD_MS = 2000;

    private static volatile long heldUntil;

    public static void say(Component message) {
        heldUntil = System.currentTimeMillis() + HOLD_MS;
        ClientCompat.actionBar(message);
    }

    public static void guidance(Component message) {
        if (System.currentTimeMillis() < heldUntil) return;
        ClientCompat.actionBar(message);
    }
}
