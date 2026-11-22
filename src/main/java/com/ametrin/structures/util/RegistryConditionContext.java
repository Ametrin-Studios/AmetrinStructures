package com.ametrin.structures.util;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.conditions.ICondition;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Lets NeoForge's conditions see the tags of `registries`, e.g. a world's.
public record RegistryConditionContext(HolderLookup.Provider registries) implements ICondition.IContext {
    @Override
    public <T> Collection<Holder<T>> getTag(TagKey<T> key) {
        return registries.lookup(key.registry())
                .flatMap(lookup -> lookup.get(key))
                .<Collection<Holder<T>>>map(tag -> tag.stream().toList())
                .orElseGet(List::of);
    }

    @Override
    public <T> Map<ResourceLocation, Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> registry) {
        return registries.lookup(registry)
                .map(lookup -> lookup.listTags().collect(Collectors.toMap(tag -> tag.key().location(), tag -> (Collection<Holder<T>>) tag.stream().toList())))
                .orElseGet(Map::of);
    }
}
