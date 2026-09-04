package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.net.QueryDto;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ContainerSearch {

    private ContainerSearch() {}

    private static final int MAX_TARGETS = 64;

    public static void findAndGuide(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return;

        String label = stack.getHoverName().getString();

        if (!ClientTracker.isAvailable()) {
            say(unavailableMessage(), ChatFormatting.RED);
            return;
        }

        String dimensionId = player.level().dimension().identifier().toString();
        say("Looking for " + label + "...", ChatFormatting.GRAY);

        ClientTracker.containers(id.toString(), filters(), MAX_TARGETS).thenAccept(response ->
                client.execute(() -> {
                    List<QueryDto.ContainerHit> hits = response.hits();
                    if (hits.isEmpty()) {
                        say("Nothing indexed holds " + label, ChatFormatting.YELLOW);
                        return;
                    }
                    LocalPlayer now = Minecraft.getInstance().player;
                    if (now == null
                            || !now.level().dimension().identifier().toString().equals(dimensionId)) {
                        return;
                    }
                    ContainerHighlight.get().select(
                            hits.stream().map(QueryDto.ContainerHit::pos).toList(),
                            dimensionId, label);
                }));
    }

    public static void sayNothingHovered() {
        say("Point at an item to search for it", ChatFormatting.GRAY);
    }

    private static QueryDto.Filters filters() {
        ChestTrackerConfig config = ChestTrackerConfig.get();
        return new QueryDto.Filters(config.includeNested, false, QueryDto.Filters.ORIGIN_ANY);
    }

    private static String unavailableMessage() {
        return switch (ClientTracker.availability()) {
            case CONNECTING -> "Still asking the server...";
            case NOT_PERMITTED -> "This server does not allow searching.";
            default -> "No index here yet.";
        };
    }

    private static void say(String message, ChatFormatting colour) {
        ClientCompat.actionBar(Component.literal(message).withStyle(colour));
    }
}
