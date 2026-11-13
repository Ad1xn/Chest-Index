package dev.adrian.chestindex.client;

import dev.adrian.chestindex.ChestIndex;
import dev.adrian.chestindex.client.highlight.ContainerHighlight;
import dev.adrian.chestindex.client.index.ClientIndex;
import dev.adrian.chestindex.client.index.ClientObserver;
import dev.adrian.chestindex.client.net.ServerLink;
import dev.adrian.chestindex.client.platform.ClientCompat;
import dev.adrian.chestindex.client.platform.Keys;
import dev.adrian.chestindex.client.platform.WorldHighlightHook;
import dev.adrian.chestindex.client.ui.ChestIndexScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class ChestIndexClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(ChestIndex.MOD_ID, "title"));

    private static KeyMapping openSearch;
    private static KeyMapping searchHovered;

    @Override
    public void onInitializeClient() {
        ChestIndex.LOG.info("ChestIndex initialising (client)");

        ServerLink.register();

        ClientObserver.register();

        WorldHighlightHook.register();

        openSearch = ClientCompat.registerKeyMapping(new KeyMapping(
                "key.chestindex.search",
                Keys.keyboard(),
                Keys.GRAVE,
                CATEGORY));

        searchHovered = ClientCompat.registerKeyMapping(new KeyMapping(
                "key.chestindex.search_hovered",
                Keys.keyboard(),
                Keys.Z,
                CATEGORY));

        ContainerScreens.register(searchHovered);

        LitematicaSearch.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSearch.consumeClick()) {
                if (!Session.active()) {
                    Session.remind();
                    continue;
                }
                ClientCompat.openScreen(ChestIndexScreen.fromHotkey());
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
