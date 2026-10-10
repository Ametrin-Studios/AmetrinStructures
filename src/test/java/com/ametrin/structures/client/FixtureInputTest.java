package com.ametrin.structures.client;

import com.ametrin.structures.fixture.FixtureBlockEntity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FixtureInputTest {
    @Test
    void weightsArePositiveWholeNumbers() {
        assertEquals(Optional.of(3), FixtureInput.parseWeight(" 3 "));
        assertEquals(Optional.empty(), FixtureInput.parseWeight("0"));
        assertEquals(Optional.empty(), FixtureInput.parseWeight("1.5"));
    }

    @Test
    void chancesLieBetweenZeroAndOne() {
        assertEquals(Optional.of(0.25F), FixtureInput.parseChance("0.25"));
        assertEquals(Optional.of(1.0F), FixtureInput.parseChance("1"));
        assertEquals(Optional.empty(), FixtureInput.parseChance("1.5"));
        assertEquals(Optional.empty(), FixtureInput.parseChance("half"));
    }

    @Test
    void offsetsStayWithinTheLimit() {
        assertEquals(Optional.of(-2.5), FixtureInput.parseOffset("-2.5"));
        assertEquals(Optional.empty(), FixtureInput.parseOffset(String.valueOf(FixtureBlockEntity.MAX_OFFSET + 1)));
    }

    @Test
    void wholeOffsetsShowWithoutDecimals() {
        assertEquals("2", FixtureInput.formatOffset(2.0));
        assertEquals("0.5", FixtureInput.formatOffset(0.5));
    }
}
