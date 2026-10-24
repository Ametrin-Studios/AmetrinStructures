package com.ametrin.structures.client;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.network.ASPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Completes ids in vanilla's structure block and jigsaw block screens, from what the server knows about.
@ApiStatus.Internal
@EventBusSubscriber(modid = AmetrinStructures.MOD_ID, value = Dist.CLIENT)
public final class VanillaScreenCompletions {
    // The boxes are private, so they're found by their label instead.
    private record CompletedBox(Class<? extends Screen> screen, String label, Identifier keys) {}

    private static final List<CompletedBox> BOXES = List.of(
            new CompletedBox(StructureBlockEditScreen.class, "structure_block.structure_name", ASPayloads.RequestRegistryKeys.STRUCTURE_TEMPLATES),
            new CompletedBox(JigsawBlockEditScreen.class, "jigsaw_block.pool", Registries.TEMPLATE_POOL.identifier()));

    private static @Nullable Screen screen;
    private static @Nullable CompletionPopup completions;

    private VanillaScreenCompletions() {}

    @SubscribeEvent
    static void onInit(ScreenEvent.Init.Post event) {
        screen = null;
        completions = null;
        var current = event.getScreen();
        for (var child : current.children()) {
            if (!(child instanceof EditBox box) || !(box.getMessage().getContents() instanceof TranslatableContents label)) {
                continue;
            }
            for (var target : BOXES) {
                if (target.screen().isInstance(current) && label.getKey().equals(target.label())) {
                    RegistryKeyCache.refresh();
                    screen = current;
                    completions = new CompletionPopup(Minecraft.getInstance().font);
                    completions.attach(box, () -> RegistryKeyCache.get(target.keys()).stream().map(Identifier::toString).toList());
                    return;
                }
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
