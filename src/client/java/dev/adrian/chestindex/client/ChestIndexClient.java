package dev.adrian.chestindex.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.adrian.chestindex.ChestIndex;
import dev.adrian.chestindex.client.highlight.ContainerHighlight;
import dev.adrian.chestindex.client.index.ClientIndex;
import dev.adrian.chestindex.client.index.ClientObserver;
import dev.adrian.chestindex.client.net.ServerLink;
import dev.adrian.chestindex.client.platform.ClientCompat;
import dev.adrian.chestindex.client.platform.WorldHighlightHook;
import dev.adrian.chestindex.client.ui.ChestIndexScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Client entrypoint: keybind, search screen, and the guidance highlight. */
public final class ChestIndexClient implements ClientModInitializer {

    /**
     * The mod's own group in the controls screen.
     *
     * <p>Every other mod with more than one binding has one, and two loose
     * entries filed under vanilla's Inventory heading are hard to find among
     * the thirty already there.
     *
     * <p>The translation key is derived from the id, so this reads
     * {@code key.category.chestindex.title} in the language file.
     */
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(ChestIndex.MOD_ID, "title"));

    private static KeyMapping openSearch;
    private static KeyMapping searchHovered;

    @Override
    public void onInitializeClient() {
        ChestIndex.LOG.info("ChestIndex initialising (client)");

        // Receivers and connection state, so the screen knows what it is
        // talking to before the player opens it.
        ServerLink.register();

        // The fallback for servers that do not have the mod: an index built
        // only from what this client is sent and what the player opens. It
        // registers listeners and never a sender, which is the whole point.
        ClientObserver.register();

        // Boxes around tracked containers. The only part of the mod that talks
        // to the world renderer, and the only place the two targets diverge
        // enough to need a whole separate registration.
        WorldHighlightHook.register();

        // GRAVE matches the muscle memory of the mod this replaces.
        openSearch = ClientCompat.registerKeyMapping(new KeyMapping(
                "key.chestindex.search",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                CATEGORY));

        // Z is free in vanilla and sits under the left hand while the right one
        // is on the mouse, which is the posture this is used in: cursor over a
        // stack in a chest, asking where the rest of it is.
        searchHovered = ClientCompat.registerKeyMapping(new KeyMapping(
                "key.chestindex.search_hovered",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                CATEGORY));

        // Keybinds do not fire while a screen is open, so the in-container half
        // of this listens on the screen itself - and, because that turned out
        // not to be delivered here, polls the window too. See ContainerScreens.
        ContainerScreens.register(searchHovered);

        // Only ever fires if Litematica is installed; it is matched by class
        // name and is not a build dependency.
        LitematicaSearch.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSearch.consumeClick()) {
                // Silent, because off should read as absent rather than as
                // broken - no error line, no screen, nothing. The toast that
                // went up when the mod switched itself off is the answer, and
                // pressing the key repeats it now and then in case that was a
                // while ago.
                if (!Session.active()) {
                    Session.remind();
                    continue;
                }
                ClientCompat.openScreen(new ChestIndexScreen());
            }
            // Notices the mod switching itself off - a server on the off-list,
            // or one without the mod when those are not indexed.
            Session.tick();
            // Says so, once per connection, when container entities are being
            // held back because this server cannot be trusted to follow them.
            Session.warnAboutEntities();
            // Says so, once, if the mod this one replaces is also installed.
            Session.warnAboutConflict();
            ContainerHighlight.get().tick();
            ContainerScreens.tick();
            // Expires the wait for a server that never announced itself, and
            // any request whose reply is never coming.
            ServerLink.tick();
            // Retires each schematic material from the highlight once enough
            // of it is in the player's inventory.
            MaterialGoals.tick();
            // Writes the client-side index out now and then, so a crash costs
            // a minute of observations rather than the session.
            ClientIndex.tick();
        });
    }
}
