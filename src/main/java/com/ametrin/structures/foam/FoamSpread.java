package com.ametrin.structures.foam;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.LevelReader;

import java.util.List;
import java.util.function.Consumer;

public record FoamSpread(FoamSpreadBehavior behavior,
                         List<FoamSpreadRestriction> restrictions) implements TooltipProvider {
    public static final Codec<FoamSpread> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    FoamSpreadBehavior.CODEC.fieldOf("behavior").forGetter(FoamSpread::behavior),
                    FoamSpreadRestriction.CODEC.listOf().optionalFieldOf("restrictions", List.of()).forGetter(FoamSpread::restrictions))
            .apply(instance, FoamSpread::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FoamSpread> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public FoamSpread {
        restrictions = List.copyOf(restrictions);
    }

    public boolean permits(LevelReader level, BlockPos source, ItemStack stack, BlockPos candidate) {
        var context = new FoamSpreadRestriction.Context(level, source, stack, candidate);
        return restrictions.stream().allMatch(restriction -> restriction.permits(context));
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter components) {
        tooltip.accept(Component.translatable("tooltip.ametrin_structures.foam.fill", Component.keybind("key.sneak"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(line(behavior.description(), ChatFormatting.BLUE));
        if (restrictions.isEmpty()) {
            tooltip.accept(line(Component.translatable("tooltip.ametrin_structures.foam.unbounded"), ChatFormatting.RED));
        }
        for (var restriction : restrictions) {
            tooltip.accept(line(restriction.description(), ChatFormatting.BLUE));
        }
    }

    private static Component line(Component description, ChatFormatting color) {
        return CommonComponents.space().append(description).withStyle(color);
    }
}
