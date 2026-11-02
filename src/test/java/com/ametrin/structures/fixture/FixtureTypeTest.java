package com.ametrin.structures.fixture;

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
    void aReservedKeyOrAFieldMissingFromTheCodecIsRejected() {
        var reserved = FixtureField.withDefault(Fixture.TYPE_KEY, FieldType.bool(), false);
        var codec = RecordCodecBuilder.<EmptyFixture>mapCodec(instance -> instance.group(reserved.forGetter(_ -> false)).apply(instance, _ -> new EmptyFixture()));
        assertThrows(IllegalArgumentException.class, () -> new FixtureType(codec, List.of(reserved)));
        var missing = FixtureField.withDefault("hanging", FieldType.bool(), false);
        assertThrows(IllegalArgumentException.class, () -> new FixtureType(EmptyFixture.CODEC, List.of(missing)));
    }
}
