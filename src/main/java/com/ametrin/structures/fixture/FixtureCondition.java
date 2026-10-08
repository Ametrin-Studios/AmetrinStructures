package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASRegistries;
import com.ametrin.structures.util.ASCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.neoforged.neoforge.common.conditions.ICondition;

import java.util.Collection;
import java.util.List;

/// Each kind is a record registered as a [FixtureConditionType]
///
/// Built-in types are in [FixtureConditions].
///
/// In a datapack, NeoForge's own conditions go in the same list, such as `neoforge:mod_loaded` or `neoforge:tag_empty`.
// fixtures needs to be preserved even when the condition is false. Neo discards entries that fail conditions so they would not be resaved correctly
public interface FixtureCondition {
    Codec<FixtureCondition> CODEC = Codec.either(
                    ASCodecs.<FixtureCondition, FixtureConditionType>dispatch(() -> ASRegistries.FIXTURE_CONDITION_TYPES, FixtureCondition::type, FixtureConditionType::codec),
                    ICondition.CODEC)
            .xmap(
                    either -> either.map(condition -> condition, FixtureConditions.NeoForge::new),
                    condition -> condition instanceof FixtureConditions.NeoForge(ICondition wrapped)
                            ? Either.<FixtureCondition, ICondition>right(wrapped)
                            : Either.<FixtureCondition, ICondition>left(condition));

    Codec<List<FixtureCondition>> LIST_CODEC = CODEC.listOf();

    boolean test(Context context);

    FixtureConditionType type();

    /// @param pos where the fixture would act
    record Context(WorldGenLevel level, BlockPos pos) implements ICondition.IContext {
        // NeoForge's conditions see the world's tags.
        @Override
        public <T> boolean isTagLoaded(TagKey<T> key) {
            return level.registryAccess().lookup(key.registry()).flatMap(lookup -> lookup.get(key)).isPresent();
        }

        @Override
        public <T> Collection<Holder<T>> getTag(TagKey<T> key) {
            return level.registryAccess().lookup(key.registry())
                    .flatMap(lookup -> lookup.get(key))
                    .<Collection<Holder<T>>>map(tag -> tag.stream().toList())
                    .orElseGet(List::of);
        }
    }
}
