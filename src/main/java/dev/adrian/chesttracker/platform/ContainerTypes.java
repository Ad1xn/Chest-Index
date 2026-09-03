package dev.adrian.chesttracker.platform;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ContainerTypes {

    private static final Set<String> VANILLA = Set.of(
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:barrel",
            "minecraft:shulker_box",
            "minecraft:ender_chest",
            "minecraft:hopper",
            "minecraft:dropper",
            "minecraft:dispenser",
            "minecraft:furnace",
            "minecraft:blast_furnace",
            "minecraft:smoker",
            "minecraft:brewing_stand",
            "minecraft:crafter",
            "minecraft:chiseled_bookshelf",
            "minecraft:decorated_pot",
            "minecraft:campfire",
            "minecraft:jukebox",
            "minecraft:lectern");

    private final Set<String> known = Collections.synchronizedSet(new LinkedHashSet<>(VANILLA));

    public static String idOf(BlockEntity blockEntity) {
        BlockEntityType<?> type = blockEntity.getType();
        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        return key == null ? null : key.toString();
    }

    public static boolean isContainer(BlockEntity blockEntity) {
        return blockEntity instanceof Container;
    }

    public boolean learn(String id) {
        return id != null && known.add(id);
    }

    public Set<String> known() {
        synchronized (known) {
            return Set.copyOf(known);
        }
    }

    public boolean isKnown(String id) {
        return known.contains(id);
    }

    public void restore(Set<String> ids) {
        known.addAll(ids);
    }

    public static Set<String> vanilla() {
        return VANILLA;
    }
}
