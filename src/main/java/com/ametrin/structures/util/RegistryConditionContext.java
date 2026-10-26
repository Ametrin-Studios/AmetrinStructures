package com.ametrin.structures.util;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.conditions.ICondition;

import java.util.Collection;
import java.util.List;

/// Lets NeoForge's conditions see the tags of `registries`, e.g. a world's.
public record RegistryConditionContext(HolderLookup.Provider registries) implements ICondition.IContext {
    @Override
    public <T> boolean isTagLoaded(TagKey<T> key) {
        return registries.lookup(key.registry()).flatMap(lookup -> lookup.get(key)).isPresent();
    }

    @Override
    public <T> Collection<Holder<T>> getTag(TagKey<T> key) {
        return registries.lookup(key.registry())
                .flatMap(lookup -> lookup.get(key))
                .<Collection<Holder<T>>>map(tag -> tag.stream().toList())
                .orElseGet(List::of);
    }
}
