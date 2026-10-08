package com.ametrin.structures.client;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.fixture.Fixture;
import com.ametrin.structures.fixture.FixtureBlockEntity;
import com.ametrin.structures.fixture.WeightedFixture;
import com.ametrin.structures.registry.ASBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;

/// Lists a picked fixture's alternatives in its item tooltip while shift is held.
@ApiStatus.Internal
public final class FixtureTooltip {
    private FixtureTooltip() {}

    public static void append(ItemTooltipEvent event) {
        var data = event.getItemStack().get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null || data.type() != ASBlockEntities.FIXTURE.get()) {
            return;
        }
        // Reads the raw tag rather than decoding, so it's cheap enough to run every frame and lists unreadable alternatives too.
        var alternatives = data.copyTagWithoutId().getListOrEmpty(FixtureBlockEntity.FIXTURES_KEY).compoundStream().toList();
        if (alternatives.isEmpty()) {
            return;
        }
        var lines = new ArrayList<Component>();
        if (Minecraft.getInstance().hasShiftDown()) {
            int total = alternatives.stream().mapToInt(FixtureTooltip::weight).sum();
            alternatives.forEach(alternative -> lines.add(describe(alternative, total)));
        } else {
            lines.add(Component.translatable("tooltip.ametrin_structures.fixture.hold_shift", alternatives.size()).withStyle(ChatFormatting.GRAY));
        }
        event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
    }

    private static Component describe(CompoundTag alternative, int totalWeight) {
        var line = Component.literal(" ")
                .append(Component.literal(percent((float) weight(alternative) / totalWeight) + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(typeName(alternative.getStringOr(Fixture.TYPE_KEY, "?"))).withStyle(ChatFormatting.BLUE));
        float chance = alternative.getFloatOr(WeightedFixture.GENERATION_CHANCE_KEY, 1.0F);
        if (chance < 1.0F) {
            line.append(Component.translatable("tooltip.ametrin_structures.fixture.chance", percent(chance)).withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    private static int weight(CompoundTag alternative) {
        return Math.max(1, alternative.getIntOr(WeightedFixture.WEIGHT_KEY, 1));
    }

    // The library's own types are shown without their namespace.
    private static String typeName(String type) {
        var id = Identifier.tryParse(type);
        return id != null && id.getNamespace().equals(AmetrinStructures.MOD_ID) ? id.getPath() : type;
    }

    private static String percent(float fraction) {
        return Math.round(fraction * 100) + "%";
    }
}
