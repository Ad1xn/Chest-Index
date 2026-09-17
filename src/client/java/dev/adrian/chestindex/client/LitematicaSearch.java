package dev.adrian.chestindex.client;

import dev.adrian.chestindex.ChestIndex;
import dev.adrian.chestindex.client.highlight.ContainerHighlight;
import dev.adrian.chestindex.client.platform.ClientCompat;
import dev.adrian.chestindex.client.platform.Gfx;
import dev.adrian.chestindex.client.ui.VanillaButton;
import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.core.net.QueryDto;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LitematicaSearch {

    private LitematicaSearch() {}

    private static final String MATERIAL_LIST_GUI = "fi.dy.masa.litematica.gui.GuiMaterialList";

    private static final int MAX_CONTAINERS = 64;

    private static final int BUTTON_GAP = 2;

    private static final String ALL_LABEL = "Search all";

    private static boolean reported;

    private static Screen openList;

    private static boolean drawnBeforeTooltips;

    public static void drawBeforeTooltips(Gfx gfx, Screen screen, int mouseX, int mouseY) {
        if (screen != openList) return;
        drawnBeforeTooltips = true;
        drawButtons(gfx, screen, mouseX, mouseY);
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!isMaterialList(screen)) {
                if (openList != null && screen != openList) openList = null;
                return;
            }
            if (!Session.active()) return;
            if (!ChestIndexConfig.get().litematicaButton) return;

            openList = screen;
            if (!reported) {
                reported = true;
                ChestIndex.LOG.info("Litematica material list hook installed");
            }

            ClientCompat.afterScreenRender(screen, (gfx, mouseX, mouseY) -> {
                if (drawnBeforeTooltips) {
                    drawnBeforeTooltips = false;
                    return;
                }
                drawButtons(gfx, screen, mouseX, mouseY);
            });

            ScreenMouseEvents.allowMouseClick(screen).register((clicked, event) -> {
                if (event.button() != 0) return true;

                for (LitematicaGui.Row row : LitematicaGui.rows(clicked)) {
                    if (rowButton(row).contains(event.x(), event.y())) {
                        VanillaButton.playClick();
                        searchOne(clicked, row);
                        return false;
                    }
                }

                LitematicaGui.Rect all = allButton(clicked);
                if (all != null && all.contains(event.x(), event.y())) {
                    VanillaButton.playClick();
                    run(clicked);
                    return false;
                }
                return true;
            });
        });
    }

    private static LitematicaGui.Rect rowButton(LitematicaGui.Row row) {
        LitematicaGui.Rect anchor = row.anchor();
        int size = anchor.height();
        return new LitematicaGui.Rect(anchor.x() - size - BUTTON_GAP, anchor.y(), size, size);
    }

    private static LitematicaGui.Rect allButton(Screen screen) {
        LitematicaGui.Rect export = LitematicaGui.exportButton(screen);
        if (export == null) return null;

        int width = VanillaButton.widthFor(ALL_LABEL);
        List<LitematicaGui.Rect> taken = obstacles(screen, export);

        LitematicaGui.Rect spot = new LitematicaGui.Rect(
                export.right() + BUTTON_GAP, export.y(), width, export.height());

        for (int pass = 0; pass <= taken.size(); pass++) {
            LitematicaGui.Rect blocker = firstOverlap(taken, spot);
            if (blocker == null) break;
            spot = new LitematicaGui.Rect(
                    blocker.right() + BUTTON_GAP, spot.y(), spot.width(), spot.height());
        }

        if (spot.right() > screen.width - 1) {
            LitematicaGui.Rect above = new LitematicaGui.Rect(
                    export.x(), export.y() - export.height() - BUTTON_GAP, width, export.height());
            return firstOverlap(taken, above) == null ? above
                    : new LitematicaGui.Rect(
                            screen.width - width - 1, spot.y(), width, spot.height());
        }
        return spot;
    }

    private static List<LitematicaGui.Rect> obstacles(Screen screen, LitematicaGui.Rect export) {
        List<LitematicaGui.Rect> taken = new ArrayList<>();
        for (LitematicaGui.Rect button : LitematicaGui.buttons(screen)) {
            if (!button.equals(export)) taken.add(button);
        }
        for (AbstractWidget widget : ClientCompat.widgets(screen)) {
            if (!widget.visible) continue;
            taken.add(new LitematicaGui.Rect(
                    widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
        }
        return taken;
    }

    private static LitematicaGui.Rect firstOverlap(
            List<LitematicaGui.Rect> taken, LitematicaGui.Rect spot) {
        LitematicaGui.Rect worst = null;
        for (LitematicaGui.Rect other : taken) {
            if (other.overlaps(spot) && (worst == null || other.right() > worst.right())) {
                worst = other;
            }
        }
        return worst;
    }

    private static void drawButtons(Gfx gfx, Screen screen, int mouseX, int mouseY) {
        for (LitematicaGui.Row row : LitematicaGui.rows(screen)) {
            LitematicaGui.Rect button = rowButton(row);
            VanillaButton.draw(gfx, button.x(), button.y(), button.width(), button.height(),
                    button.contains(mouseX, mouseY));
            VanillaButton.magnifier(gfx,
                    button.x() + (button.width() - 8) / 2,
                    button.y() + (button.height() - 8) / 2,
                    VanillaButton.ICON);
        }

        LitematicaGui.Rect all = allButton(screen);
        if (all != null) {
            VanillaButton.draw(gfx, all.x(), all.y(), all.width(), all.height(),
                    all.contains(mouseX, mouseY), ALL_LABEL);
        }
    }

    private static void searchOne(Screen screen, LitematicaGui.Row row) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        if (!ClientTracker.isAvailable()) {
            say("No index here yet.", ChatFormatting.RED);
            return;
        }

        String itemId = row.itemId();
        String label = displayName(itemId);
        String dimensionId = player.level().dimension().identifier().toString();
        Map<String, Integer> needed = Map.of(itemId, row.needed());

        ClientTracker.containers(List.of(itemId), ContainerSearch.searchFilters(), MAX_CONTAINERS, "")
                .thenAccept(response -> client.execute(() -> {
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
                    ContainerHighlight.get().selectHits(hits, dimensionId, label);
                    ContainerHighlight.get().searchingFor(List.of(itemId));
                    MaterialGoals.track(needed, dimensionId, ContainerSearch.searchFilters());
                    screen.onClose();
                }));
    }

    private static String displayName(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return itemId;
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? itemId : new ItemStack(item).getHoverName().getString();
    }

    private static boolean isMaterialList(Screen screen) {
        return MATERIAL_LIST_GUI.equals(screen.getClass().getName());
    }

    private static void run(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        if (!ClientTracker.isAvailable()) {
            say("No index here yet.", ChatFormatting.RED);
            return;
        }

        Map<String, Integer> needed = itemsNeeded(screen);
        if (needed.isEmpty()) {
            say("Could not read the material list", ChatFormatting.YELLOW);
            return;
        }
        List<String> wanted = new ArrayList<>(needed.keySet());

        String dimensionId = player.level().dimension().identifier().toString();
        String label = wanted.size() == 1
                ? "1 material" : wanted.size() + " materials";
        say("Looking for " + label + "...", ChatFormatting.GRAY);

        ClientTracker.containers(wanted, ContainerSearch.searchFilters(), MAX_CONTAINERS, "")
                .thenAccept(response -> client.execute(() -> {
                    List<QueryDto.ContainerHit> hits = response.hits();
                    if (hits.isEmpty()) {
                        say("Nothing indexed holds any of it", ChatFormatting.YELLOW);
                        return;
                    }
                    LocalPlayer now = Minecraft.getInstance().player;
                    if (now == null
                            || !now.level().dimension().identifier().toString().equals(dimensionId)) {
                        return;
                    }
                    ContainerHighlight.get().selectHits(hits, dimensionId, label);
                    ContainerHighlight.get().searchingFor(wanted);
                    MaterialGoals.track(needed, dimensionId, ContainerSearch.searchFilters());
                    screen.onClose();
                }));
    }

    private static Map<String, Integer> itemsNeeded(Screen screen) {
        try {
            Method getMaterialList = screen.getClass().getMethod("getMaterialList");
            Object materialList = getMaterialList.invoke(screen);
            if (materialList == null) return Map.of();

            List<?> entries = missingOnly(materialList);
            if (entries == null || entries.isEmpty()) entries = all(materialList);
            if (entries == null) return Map.of();

            Map<String, Integer> needed = new LinkedHashMap<>();
            for (Object entry : entries) {
                ItemStack stack = stackOf(entry);
                if (stack == null || stack.isEmpty()) continue;
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id == null) continue;

                int count = countOf(entry);
                needed.merge(id.toString(), count, Integer::sum);
                if (needed.size() >= QueryDto.ContainerRequest.MAX_ITEMS) break;
            }
            return needed;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ChestIndex.LOG.warn("Could not read Litematica's material list: {}", e.toString());
            return Map.of();
        }
    }

    private static int countOf(Object entry) {
        try {
            Object total = entry.getClass().getMethod("getCountTotal").invoke(entry);
            if (total instanceof Integer count && count > 0) return count;
        } catch (ReflectiveOperationException | RuntimeException absent) {
        }
        return 1;
    }

    private static List<?> missingOnly(Object materialList) {
        try {
            Method method = materialList.getClass()
                    .getMethod("getMaterialsMissingOnly", boolean.class);
            return (List<?>) method.invoke(materialList, true);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    private static List<?> all(Object materialList) {
        try {
            Method method = materialList.getClass().getMethod("getMaterialsAll");
            return (List<?>) method.invoke(materialList);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    private static ItemStack stackOf(Object entry) {
        try {
            Object stack = entry.getClass().getMethod("getStack").invoke(entry);
            return stack instanceof ItemStack item ? item : null;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    private static void say(String message, ChatFormatting colour) {
        ActionBar.say(Component.literal(message).withStyle(colour));
    }
}
