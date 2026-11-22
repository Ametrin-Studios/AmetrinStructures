package com.ametrin.structures.client;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.fixture.Fixture;
import com.ametrin.structures.fixture.FixtureBlockEntity;
import com.ametrin.structures.fixture.WeightedFixture;
import com.ametrin.structures.registry.ASBlockEntities;
import com.ametrin.structures.util.ASNbt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;

/// Lists a picked fixture's alternatives in its item tooltip while shift is held.
@ApiStatus.Internal
public final class FixtureTooltip {
    public static final class Keys {
        public static final String HOLD_SHIFT = "tooltip.ametrin_structures.fixture.hold_shift";
        public static final String CHANCE = "tooltip.ametrin_structures.fixture.chance";

        private Keys() {}
    }

    private FixtureTooltip() {}

    public static void append(ItemTooltipEvent event) {
        var data = event.getItemStack().get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) {
            return;
        }
        // Reads the raw tag rather than decoding, so it's cheap enough to run every frame and lists unreadable alternatives too.
        var tag = data.copyTag();
        if (!tag.getString("id").equals(BlockEntityType.getKey(ASBlockEntities.FIXTURE.get()).toString())) {
            return;
        }
        var alternatives = tag.getList(FixtureBlockEntity.FIXTURES_KEY, Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast).toList();
        if (alternatives.isEmpty()) {
            return;
        }
        var lines = new ArrayList<Component>();
        if (Screen.hasShiftDown()) {
            int total = alternatives.stream().mapToInt(FixtureTooltip::weight).sum();
            alternatives.forEach(alternative -> lines.add(describe(alternative, total)));
        } else {
            lines.add(Component.translatable(Keys.HOLD_SHIFT, alternatives.size()).withStyle(ChatFormatting.GRAY));
        }
        event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
    }

    private static Component describe(CompoundTag alternative, int totalWeight) {
        var line = Component.literal(" ")
                .append(Component.literal(percent((float) weight(alternative) / totalWeight) + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(typeName(ASNbt.getStringOr(alternative, Fixture.TYPE_KEY, "?"))).withStyle(ChatFormatting.BLUE));
        float chance = ASNbt.getFloatOr(alternative, WeightedFixture.GENERATION_CHANCE_KEY, 1.0F);
        if (chance < 1.0F) {
            line.append(Component.translatable(Keys.CHANCE, percent(chance)).withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    private static int weight(CompoundTag alternative) {
        return Math.max(1, ASNbt.getIntOr(alternative, WeightedFixture.WEIGHT_KEY, 1));
    }

    // The library's own types are shown without their namespace.
    private static String typeName(String type) {
        var id = ResourceLocation.tryParse(type);
        return id != null && id.getNamespace().equals(AmetrinStructures.MOD_ID) ? id.getPath() : type;
    }

    private static String percent(float fraction) {
        return Math.round(fraction * 100) + "%";
    }
}
