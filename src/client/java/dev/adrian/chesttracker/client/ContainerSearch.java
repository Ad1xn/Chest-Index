package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.net.QueryDto;
import dev.adrian.chesttracker.core.util.BlockKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ContainerSearch {

    private ContainerSearch() {}

    private static final int MAX_TARGETS = 64;

    public static boolean findAndGuide(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return false;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;

        String label = stack.getHoverName().getString();

        if (!ClientTracker.isAvailable()) {
            say(unavailableMessage(), ChatFormatting.RED);
            return false;
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
                    ContainerHighlight.get().searchingFor(id.toString());
                    openIfInReach(hits, now);
                }));
        return true;
    }

    public static void sayNothingHovered() {
        say("Point at an item to search for it", ChatFormatting.GRAY);
    }

    private static QueryDto.Filters filters() {
        ChestTrackerConfig config = ChestTrackerConfig.get();
        return new QueryDto.Filters(config.includeNested, config.showMachines, config.originFilter);
    }

    private static boolean openIfInReach(List<QueryDto.ContainerHit> hits, LocalPlayer player) {
        if (!ChestTrackerConfig.get().openInReach) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || ClientCompat.currentScreen() != null) return false;

        double reach = player.blockInteractionRange();
        QueryDto.ContainerHit best = null;
        BlockPos bestPos = null;

        for (QueryDto.ContainerHit hit : hits) {
            BlockPos pos = new BlockPos(BlockKey.x(hit.pos()), BlockKey.y(hit.pos()), BlockKey.z(hit.pos()));
            if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) continue;
            if (!(client.level.getBlockEntity(pos) instanceof Container)) continue;
            if (best == null || hit.matchedCount() > best.matchedCount()) {
                best = hit;
                bestPos = pos;
            }
        }

        if (best == null) return false;

        BlockHitResult where = new BlockHitResult(Vec3.atCenterOf(bestPos), Direction.UP, bestPos, false);
        client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, where);
        return true;
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
