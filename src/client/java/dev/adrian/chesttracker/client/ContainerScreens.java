package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.client.ui.SearchButton;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.mixin.client.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

public final class ContainerScreens {

    private ContainerScreens() {}

    public static void register(KeyMapping searchHovered) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;

            ContainerScreenAccessor access = (ContainerScreenAccessor) container;

            if (ChestTrackerConfig.get().containerSearchButton) {
                ClientCompat.addWidget(screen, new SearchButton(
                        access.chesttracker$leftPos() + access.chesttracker$imageWidth(),
                        access.chesttracker$topPos()));
            }

            ScreenKeyboardEvents.afterKeyPress(screen).register((ignored, keyEvent) -> {
                if (!searchHovered.matches(keyEvent)) return;

                Slot hovered = access.chesttracker$hoveredSlot();
                if (hovered == null || !hovered.hasItem()) {
                    ContainerSearch.sayNothingHovered();
                    return;
                }

                ContainerSearch.findAndGuide(hovered.getItem());
            });
        });
    }
}
