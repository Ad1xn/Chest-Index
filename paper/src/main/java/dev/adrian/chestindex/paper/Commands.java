package dev.adrian.chestindex.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class Commands implements TabExecutor {
    private static final List<String> SUBCOMMANDS = List.of("status", "rescan", "recheck", "reload");

    private final ChestIndexPlugin plugin;

    Commands(ChestIndexPlugin plugin) {
        this.plugin = plugin;
    }

    private static void reply(CommandSender sender, String text) {
        sender.sendMessage(Component.text("ChestIndex  ", NamedTextColor.GRAY)
                .append(Component.text(text, NamedTextColor.WHITE)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> status(sender);
            case "rescan" -> {
                if (plugin.regionScan().isRunning()) {
                    reply(sender, "A scan is already running.");
                    return true;
                }
                plugin.tracker().forgetWhatWasScanned();
                reply(sender, plugin.startScan(Map.of(), true)
                        ? "Reading every region file again."
                        : "Could not start the scan.");
            }
            case "recheck" -> {
                if (plugin.regionScan().isRunning()) {
                    reply(sender, "A scan is already running.");
                    return true;
                }
                Map<String, Set<Long>> chunks = plugin.tracker().indexedChunks();
                int total = chunks.values().stream().mapToInt(Set::size).sum();
                if (total == 0) {
                    reply(sender, "Nothing is indexed yet.");
                    return true;
                }
                plugin.tracker().forgetOriginChecks();
                reply(sender, plugin.startScan(chunks, false)
                        ? String.format("Looking at %,d chunks again.", total)
                        : "Could not start the re-check.");
            }
            case "reload" -> {
                plugin.reloadSettings();
                reply(sender, "Settings reloaded.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void status(CommandSender sender) {
        Tracker tracker = plugin.tracker();
        reply(sender, String.format("%,d containers indexed", tracker.totalContainers()));
        List<String> dimensions = new ArrayList<>(tracker.dimensions());
        dimensions.sort(null);
        for (String dimension : dimensions) {
            int count = tracker.index(dimension).size();
            if (count > 0) reply(sender, String.format("  %s  %,d", dimension, count));
        }
        RegionScan.Progress progress = plugin.regionScan().progress();
        if (progress.running()) {
            reply(sender, String.format("Scanning: %,d of %,d regions, %,d chunks read",
                    progress.regionsRead(), progress.regionsTotal(), progress.chunksRead()));
        }
        reply(sender, "Access: " + plugin.settings().access().name().toLowerCase(Locale.ROOT));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String typed = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(typed)).toList();
    }
}
