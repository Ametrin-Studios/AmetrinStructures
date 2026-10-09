package com.ametrin.structures.client;

import com.ametrin.structures.network.ASPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

@ApiStatus.Internal
@EventBusSubscriber(value = Dist.CLIENT)
public final class StructureBlockCompletions {
    private static final String NAME_BOX_KEY = "structure_block.structure_name";

    private static @Nullable Screen screen;
    private static @Nullable CompletionPopup completions;

    private StructureBlockCompletions() {}

    @SubscribeEvent
    static void onInit(ScreenEvent.Init.Post event) {
        screen = null;
        completions = null;
        if (!(event.getScreen() instanceof StructureBlockEditScreen structureBlock)) {
            return;
        }
        // The box is private, so it's found by its label instead.
        for (var child : structureBlock.children()) {
            if (child instanceof EditBox box
                    && box.getMessage().getContents() instanceof TranslatableContents label
                    && label.getKey().equals(NAME_BOX_KEY)) {
                RegistryKeyCache.refresh();
                screen = structureBlock;
                completions = new CompletionPopup(Minecraft.getInstance().font);
                completions.attach(box, () -> RegistryKeyCache.get(ASPayloads.RequestRegistryKeys.STRUCTURE_TEMPLATES)
                        .stream()
                        .map(Identifier::toString)
                        .toList());
                return;
            }
        }
    }

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        var popup = popupFor(event.getScreen());
        if (popup != null) {
            popup.extractRenderState(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        var popup = popupFor(event.getScreen());
        if (popup != null && popup.keyPressed(event.getKeyEvent())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        var popup = popupFor(event.getScreen());
        if (popup != null && popup.mouseClicked(event.getMouseButtonEvent())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        var popup = popupFor(event.getScreen());
        if (popup != null && popup.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onClosing(ScreenEvent.Closing event) {
        if (event.getScreen() == screen) {
            screen = null;
            completions = null;
        }
    }

    private static @Nullable CompletionPopup popupFor(Screen current) {
        return current == screen ? completions : null;
    }
}
