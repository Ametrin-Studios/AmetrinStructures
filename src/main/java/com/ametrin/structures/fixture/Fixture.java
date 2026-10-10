package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;

import java.util.stream.Stream;

/// Each kind is a record registered as a [FixtureType]. [Fixtures] has helpers for writing them.
public interface Fixture {
    String TYPE_KEY = "type";
    MapCodec<Fixture> MAP_CODEC = ASRegistries.FIXTURE_TYPES.byNameCodec().dispatchMap(TYPE_KEY, Fixture::type, FixtureType::codec);

    /// Runs the action, after the marker was replaced by its `becomes` state.
    void apply(FixtureContext context);

    FixtureType type();

    /// The registry entries the fixture names, so `/ametrin structures check` can report missing ones.
    default Stream<ResourceKey<?>> references() {
        return Stream.empty();
    }

    /// A stored fixture that can't be decoded, e.g. because it names a block from a mod that isn't
    /// installed. It keeps its data so it's saved back unchanged. Generation skips it.
    ///
    /// @param data  the whole alternative as it was stored, weight included
    /// @param error why it didn't decode
    record Unreadable(Tag data, String error) implements Fixture {
        @Override
        public void apply(FixtureContext context) {
        }

        /// @throws IllegalStateException it isn't of any registered type
        @Override
        public FixtureType type() {
            throw new IllegalStateException("an unreadable fixture has no type: " + error);
        }
    }
}
