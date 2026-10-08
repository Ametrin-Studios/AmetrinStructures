package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASRegistries;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(EphemeralTestServerProvider.class)
class FixtureTypeTest {
    @Test
    void everyRegisteredTypeLoaded() {
        // Each type checks its keys when it's constructed, so registering them all is the check.
        assertFalse(ASRegistries.FIXTURE_TYPES.keySet().isEmpty());
    }

    @Test
    void aReservedKeyIsRejected() {
        var field = FixtureField.withDefault(Fixture.TYPE_KEY, FieldType.bool(), false);
        var codec = RecordCodecBuilder.<Fixtures.Empty>mapCodec(instance -> instance.group(field.forGetter(_ -> false)).apply(instance, _ -> new Fixtures.Empty()));
        assertThrows(IllegalArgumentException.class, () -> new FixtureType(codec, List.of(field)));
    }

    @Test
    void aFieldMissingFromTheCodecIsRejected() {
        var field = FixtureField.withDefault("hanging", FieldType.bool(), false);
        assertThrows(IllegalArgumentException.class, () -> new FixtureType(Fixtures.Empty.CODEC, List.of(field)));
    }
}
