package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASFixtures;
import com.ametrin.structures.registry.ASRegistries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.stream.Stream;

/// Draws from a [FixturePreset] and runs the result as if it were this fixture.
public record PresetFixture(ResourceKey<FixturePreset> preset) implements Fixture {
    public static final FixtureField<ResourceKey<FixturePreset>> PRESET = FixtureField.required("preset", FieldType.registryKey(ASRegistries.FIXTURE_PRESET));
    public static final List<FixtureField<?>> FIELDS = List.of(PRESET);
    public static final MapCodec<PresetFixture> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(PRESET.forGetter(PresetFixture::preset)).apply(instance, PresetFixture::new));

    @Override
    public void apply(FixtureContext context) {
        // should never run
    }

    @Override
    public Stream<ResourceKey<?>> references() {
        return Stream.of(preset);
    }

    @Override
    public FixtureType type() {
        return ASFixtures.PRESET.get();
    }
}
