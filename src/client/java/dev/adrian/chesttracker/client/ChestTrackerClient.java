package dev.adrian.chesttracker.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.adrian.chesttracker.ChestTracker;
import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.client.index.ClientIndex;
import dev.adrian.chesttracker.client.index.ClientObserver;
import dev.adrian.chesttracker.client.net.ServerLink;
import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.client.platform.WorldHighlightHook;
import dev.adrian.chesttracker.client.ui.ChestTrackerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ChestTrackerClient implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(ChestTracker.MOD_ID, "title"));

    private static KeyMapping openSearch;
    private static KeyMapping searchHovered;

    @Override
    public void onInitializeClient() {
        ChestTracker.LOG.info("ChestTracker initialising (client)");

        ServerLink.register();

        ClientObserver.register();

        WorldHighlightHook.register();

        openSearch = ClientCompat.registerKeyMapping(new KeyMapping(
                "key.chest-tracker.search",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                CATEGORY));

        searchHovered = ClientCompat.registerKeyMapping(new KeyMapping(
                "key.chest-tracker.search_hovered",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                CATEGORY));

        ContainerScreens.register(searchHovered);

        LitematicaSearch.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSearch.consumeClick()) {
                if (!Session.active()) {
                    Session.remind();
                    continue;
                }
                ClientCompat.openScreen(new ChestTrackerScreen());
            }
            Session.tick();
            Session.warnAboutEntities();
            Session.warnAboutConflict();
            ContainerHighlight.get().tick();
            ContainerScreens.tick();
            ServerLink.tick();
            MaterialGoals.tick();
            ClientIndex.tick();
        });
    }
}
