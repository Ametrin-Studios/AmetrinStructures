package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtureConditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.conditions.ICondition;

import java.util.List;
import java.util.Optional;

public final class FixtureConditions {
    private FixtureConditions() {}

    public record InBiome(HolderSet<Biome> biomes) implements FixtureCondition {
        public static final MapCodec<InBiome> CODEC = RegistryCodecs.holderSet(Registries.BIOME)
                .fieldOf("biomes")
                .xmap(InBiome::new, InBiome::biomes);

        @Override
        public boolean test(Context context) {
            return biomes.contains(context.level().getBiome(context.pos()));
        }

        @Override
        public FixtureConditionType type() {
            return ASFixtureConditions.BIOME.get();
        }
    }

    public record InDimension(List<ResourceKey<Level>> dimensions) implements FixtureCondition {
        public static final MapCodec<InDimension> CODEC = ResourceKey.codec(Registries.DIMENSION).listOf()
                .fieldOf("dimensions")
                .xmap(InDimension::new, InDimension::dimensions);

        public InDimension {
            dimensions = List.copyOf(dimensions);
        }

        @Override
        public boolean test(Context context) {
            return dimensions.contains(context.level().getLevel().dimension());
        }

        @Override
        public FixtureConditionType type() {
            return ASFixtureConditions.DIMENSION.get();
        }
    }

    /// Passes from `min` to `max` Y, both included. Either may be left out.
    public record HeightRange(Optional<Integer> min, Optional<Integer> max) implements FixtureCondition {
        public static final MapCodec<HeightRange> CODEC = RecordCodecBuilder.<HeightRange>mapCodec(instance -> instance.group(
                                Codec.INT.optionalFieldOf("min").forGetter(HeightRange::min),
                                Codec.INT.optionalFieldOf("max").forGetter(HeightRange::max))
                        .apply(instance, HeightRange::new))
                .validate(HeightRange::validate);

        @Override
        public boolean test(Context context) {
            int y = context.pos().getY();
            return min.map(bottom -> y >= bottom).orElse(true) && max.map(top -> y <= top).orElse(true);
        }

        private DataResult<HeightRange> validate() {
            return min.isPresent() && max.isPresent() && min.get() > max.get()
                    ? DataResult.error(() -> "min " + min.get() + " is above max " + max.get())
                    : DataResult.success(this);
        }

        @Override
        public FixtureConditionType type() {
            return ASFixtureConditions.HEIGHT_RANGE.get();
        }
    }

    public record Not(FixtureCondition condition) implements FixtureCondition {
        public static final MapCodec<Not> CODEC = FixtureCondition.CODEC.fieldOf("condition").xmap(Not::new, Not::condition);

        @Override
        public boolean test(Context context) {
            return !condition.test(context);
        }

        @Override
        public FixtureConditionType type() {
            return ASFixtureConditions.NOT.get();
        }
    }

    public record AnyOf(List<FixtureCondition> conditions) implements FixtureCondition {
        public static final MapCodec<AnyOf> CODEC = FixtureCondition.LIST_CODEC.fieldOf("conditions").xmap(AnyOf::new, AnyOf::conditions);

        public AnyOf {
            conditions = List.copyOf(conditions);
        }

        @Override
        public boolean test(Context context) {
            return conditions.stream().anyMatch(condition -> condition.test(context));
        }

        @Override
        public FixtureConditionType type() {
            return ASFixtureConditions.ANY_OF.get();
        }
    }

    public record AllOf(List<FixtureCondition> conditions) implements FixtureCondition {
        public static final MapCodec<AllOf> CODEC = FixtureCondition.LIST_CODEC.fieldOf("conditions").xmap(AllOf::new, AllOf::conditions);

        public AllOf {
            conditions = List.copyOf(conditions);
        }

        @Override
        public boolean test(Context context) {
            return conditions.stream().allMatch(condition -> condition.test(context));
        }

        @Override
        public FixtureConditionType type() {
            return ASFixtureConditions.ALL_OF.get();
        }
    }

    public record NeoForge(ICondition condition) implements FixtureCondition {
        @Override
        public boolean test(Context context) {
            return condition.test(context);
        }

        /// @throws IllegalStateException it is stored by NeoForge's condition type instead
        @Override
        public FixtureConditionType type() {
            throw new IllegalStateException("a NeoForge condition has no fixture condition type: " + condition);
        }
    }
}
