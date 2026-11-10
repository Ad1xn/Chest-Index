package dev.adrian.chestindex.paper;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Set;

record Settings(Access access, boolean scanOnStart, boolean trackEntityContainers,
                boolean indexUnlootedGeneratedChests, Set<String> machineTypes, Set<String> utilityTypes) {

    enum Access {
        ALL,
        OWNED,
        OP;

        static Access parse(String value) {
            if (value == null) return ALL;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return ALL;
            }
        }
    }

    static Settings from(FileConfiguration config) {
        return new Settings(
                Access.parse(config.getString("access", "ALL")),
                config.getBoolean("scan-on-start", true),
                config.getBoolean("track-entity-containers", true),
                config.getBoolean("index-unlooted-generated-chests", false),
                Set.copyOf(listOr(config, "machine-types", List.of(
                        "minecraft:hopper", "minecraft:dropper", "minecraft:dispenser", "minecraft:crafter"))),
                Set.copyOf(listOr(config, "utility-types", List.of(
                        "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
                        "minecraft:brewing_stand", "minecraft:campfire", "minecraft:soul_campfire",
                        "minecraft:jukebox", "minecraft:lectern", "minecraft:decorated_pot",
                        "minecraft:chiseled_bookshelf"))));
    }

    private static List<String> listOr(FileConfiguration config, String key, List<String> fallback) {
        return config.isList(key) ? config.getStringList(key) : fallback;
    }
}
