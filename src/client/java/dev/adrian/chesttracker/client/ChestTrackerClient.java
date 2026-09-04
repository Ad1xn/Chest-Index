package dev.adrian.chesttracker.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.adrian.chesttracker.ChestTracker;
import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.client.net.ServerLink;
import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.client.ui.ChestTrackerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ChestTrackerClient implements ClientModInitializer {

    private static KeyMapping openSearch;
    private static KeyMapping searchHovered;

    @Override
    public void onInitializeClient() {
        ChestTracker.LOG.info("ChestTracker initialising (client)");

        ServerLink.register();

        openSearch = ClientCompat.registerKeyMapping(new KeyMapping(
                "key.chest-tracker.search",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                KeyMapping.Category.INVENTORY));

        searchHovered = ClientCompat.registerKeyMapping(new KeyMapping(
                "key.chest-tracker.search_hovered",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                KeyMapping.Category.INVENTORY));

        ContainerScreens.register(searchHovered);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSearch.consumeClick()) {
                ClientCompat.openScreen(new ChestTrackerScreen());
            }
            ContainerHighlight.get().tick();
            ServerLink.tick();
        });
    }
}
