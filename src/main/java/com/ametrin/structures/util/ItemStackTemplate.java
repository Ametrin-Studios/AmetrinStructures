package com.ametrin.structures.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/// An immutable item stack, like vanilla's of later versions. Stacks are mutable and don't compare by value, so records hold these instead.
public record ItemStackTemplate(Holder<Item> item, int count, DataComponentPatch components) {
    public static final MapCodec<ItemStackTemplate> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStackTemplate::item),
                    ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(ItemStackTemplate::count),
                    DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStackTemplate::components))
            .apply(instance, ItemStackTemplate::new));
    public static final Codec<ItemStackTemplate> CODEC = Codec.withAlternative(MAP_CODEC.codec(), ItemStack.ITEM_NON_AIR_CODEC, ItemStackTemplate::new);

    public ItemStackTemplate(Holder<Item> item) {
        this(item, 1, DataComponentPatch.EMPTY);
    }

    public ItemStackTemplate(Item item) {
        this(BuiltInRegistries.ITEM.wrapAsHolder(item));
    }

    public ItemStackTemplate(Item item, DataComponentPatch components) {
        this(BuiltInRegistries.ITEM.wrapAsHolder(item), 1, components);
    }

    public ItemStack create() {
        return new ItemStack(item, count, components);
    }
}
