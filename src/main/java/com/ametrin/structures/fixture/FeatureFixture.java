package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.ametrin.structures.util.ASLog;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.List;
import java.util.stream.Stream;

public record FeatureFixture(ResourceKey<ConfiguredFeature<?, ?>> feature) implements Fixture {
    public static final FixtureField<ResourceKey<ConfiguredFeature<?, ?>>> FEATURE = FixtureField.required("feature", FieldType.registryKey(Registries.CONFIGURED_FEATURE));
    public static final List<FixtureField<?>> FIELDS = List.of(FEATURE);
    public static final MapCodec<FeatureFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    FEATURE.forGetter(FeatureFixture::feature))
            .apply(instance, FeatureFixture::new));

    @Override
    public void apply(FixtureContext context) {
        context.level().registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(feature)
                .map(Holder::value)
                .ifPresentOrElse(
                        value -> value.place(context.level(), context.generator(), context.random(), context.actionBlockPos()),
                        () -> ASLog.warn("fixture feature {} is not registered", feature.identifier()));
    }

    @Override
    public Stream<ResourceKey<?>> references() {
        return Stream.of(feature);
    }

    @Override
    public FixtureType type() {
        return ASFixtures.FEATURE.get();
    }
}
