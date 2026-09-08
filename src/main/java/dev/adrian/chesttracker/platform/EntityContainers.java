package dev.adrian.chesttracker.platform;

import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.model.Origin;
import dev.adrian.chesttracker.core.model.StackEntry;
import dev.adrian.chesttracker.core.store.StringPalette;
import dev.adrian.chesttracker.core.util.BlockKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Containers that are entities rather than blocks: chest minecarts, hopper
 * minecarts, chest boats.
 *
 * <h2>Why these are never written to the index</h2>
 *
 * <p>Every other container in this mod has a position that is a fact about the
 * world. These do not. A chest minecart is on a rail, and a rail is a thing
 * players build to move it - so a stored position is not merely stale, it is
 * <em>wrong by design</em>, and it goes wrong at exactly the speed the item
 * travels. Guiding somebody to where a minecart was is worse than not knowing
 * about it at all, because they walk there.
 *
 * <p>So these are read live, at the moment the question is asked, and never
 * saved. That has three consequences worth being straight about:
 *
 * <ul>
 *   <li>Only loaded entities are found. An unloaded chest minecart is not
 *       simply un-indexed - it does not currently exist as an entity at all.
 *   <li>The answer is good for about as long as it takes to read it. The
 *       highlight follows the guidance it was given and the cart does not.
 *   <li>Nothing about them survives a restart, and nothing needs to. There is
 *       no file, no format, and so nothing anybody has to delete.
 * </ul>
 *
 * <p>The last point is not incidental. The index file format is versioned and
 * shared with everyone already running the mod; adding a moving container to it
 * would have meant a migration for a record that is out of date before it is
 * written.
 */
public final class EntityContainers {

    private EntityContainers() {}

    /**
     * How far out to look, in blocks.
     *
     * <p>Bounded because this runs per query rather than off a stored index,
     * and an unbounded sweep of every entity in a dimension is a real cost on a
     * busy server. Two hundred and fifty-six blocks is past anything the player
     * can see and well inside what they might walk to before the cart moves.
     */
    public static final double RADIUS = 256.0;

    /**
     * One container entity: the record a query can match, and the entity it is.
     *
     * <p>The id is carried separately rather than put on the record because
     * {@link ContainerRecord} is what gets written to the index file, and a
     * network id is meaningless the moment the entity is unloaded - let alone
     * after a restart. It exists only for the length of one answer, which is
     * exactly how long it is any use.
     *
     * @param entityId the entity's network id, the same number the client
     *                 knows it by
     */
    public record Found(ContainerRecord record, int entityId) {}

    /**
     * Container entities near {@code centre}, as records the index can search.
     *
     * <p>They are handed back as ordinary {@link ContainerRecord}s so the
     * caller can put them in a throwaway index and run the real query against
     * them - matching, nesting, origin filters, distance ranking and all. That
     * is the whole reason for this shape: an entity container must answer the
     * same questions a chest does, and reimplementing any of it here would be
     * a second set of rules to keep in step.
     */
    public static List<Found> near(Level level, String dimensionId,
                                   StringPalette palette, long centre) {
        if (level == null) return List.of();

        int cx = BlockKey.x(centre);
        int cy = BlockKey.y(centre);
        int cz = BlockKey.z(centre);
        AABB box = new AABB(cx - RADIUS, cy - RADIUS, cz - RADIUS,
                cx + RADIUS, cy + RADIUS, cz + RADIUS);

        int dimensionKey = palette.intern(dimensionId);
        long tick = level.getGameTime();

        List<Found> records = new ArrayList<>();
        // Two entities can share a block - a minecart passing over another, or
        // a chest boat beside one - and the index is keyed by position, so the
        // second would silently replace the first. Skipped instead, which at
        // worst leaves one of a pair unlisted for as long as they overlap.
        Set<Long> taken = new HashSet<>();

        // instanceof Container rather than a type list: it is exact, and it
        // picks up a modded container entity without knowing it exists - the
        // same test the live block scan uses.
        for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof Container)) {
            int x = entity.getBlockX();
            int y = entity.getBlockY();
            int z = entity.getBlockZ();
            if (!BlockKey.isRepresentable(x, y, z)) continue;

            long pos = BlockKey.pack(x, y, z);
            if (!taken.add(pos)) continue;

            var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (key == null) continue;

            List<StackEntry> contents =
                    LiveContainerReader.read((Container) entity, palette);
            records.add(new Found(new ContainerRecord(
                    pos,
                    dimensionKey,
                    palette.intern(key.toString()),
                    // Nobody "placed" a minecart in the sense the origin filter
                    // means, and a filter for generated loot must not claim it.
                    Origin.PLAYER_PLACED,
                    null,
                    false,
                    true,
                    null,
                    tick,
                    contents), entity.getId()));
        }
        return records;
    }
}
