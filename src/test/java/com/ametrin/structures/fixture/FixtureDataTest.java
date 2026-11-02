package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class FixtureDataTest {
    @Test
    void theStoredDataIsSavedAsItWasWritten(MinecraftServer server) throws Exception {
        // An explicit default, a field left out, and a key this version doesn't know.
        var data = List.<Tag>of(
                TagParser.parseCompoundFully("{type: \"ametrin_structures:vault\", ominous: false}"),
                TagParser.parseCompoundFully("{type: \"ametrin_structures:vault\", future_key: 1}"));
        var registries = server.registryAccess();
        var marker = marker();
        marker.setFixtureData(data, registries);

        var saved = marker.saveCustomOnly(registries);
        var loaded = marker();
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        assertEquals(data, loaded.fixtureData());
    }

    @Test
    void theAlternativesAreDecodedAndInvalidOnesKeptUnreadable(MinecraftServer server) throws Exception {
        var marker = marker();
        marker.setFixtureData(List.of(
                TagParser.parseCompoundFully("{type: \"ametrin_structures:vault\", weight: 3}"),
                TagParser.parseCompoundFully("{type: \"ametrin_structures:loot_container\"}"),
                TagParser.parseCompoundFully("{type: \"ametrin_structures:archaeology\", block: {Name: \"minecraft:suspicious_gravel\"}}"),
                TagParser.parseCompoundFully("{type: \"ametrin_structures:archaeology\", block: {Name: \"minecraft:sand\"}}")), server.registryAccess());
        var fixtures = marker.fixtures();
        assertEquals(3, fixtures.get(0).weight());
        assertEquals(new VaultFixture(false), fixtures.get(0).fixture());
        assertInstanceOf(Fixture.Unreadable.class, fixtures.get(1).fixture(), "a loot container needs its loot table");
        assertInstanceOf(ArchaeologyFixture.class, fixtures.get(2).fixture());
        assertInstanceOf(Fixture.Unreadable.class, fixtures.get(3).fixture(), "archaeology needs a brushable block");
    }

    @Test
    void aPickedMarkerStacksWithItsCopyOncePlaced(MinecraftServer server) throws Exception {
        var registries = server.registryAccess();
        var marker = marker();
        marker.setFixtureData(List.of(TagParser.parseCompoundFully("{type: \"ametrin_structures:vault\"}")), registries);
        marker.setCustomName(Component.literal("crate"));

        // Like vanilla's pick block with data.
        var picked = new ItemStack(ASBlocks.FIXTURE.get());
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        marker.saveCustomOnly(output);
        marker.removeComponentsFromTag(output);
        BlockItem.setBlockEntityData(picked, marker.getType(), output);
        picked.applyComponents(marker.collectComponents());

        var placed = marker();
        picked.get(DataComponents.BLOCK_ENTITY_DATA).loadInto(placed, registries);
        placed.applyComponentsFromItemStack(picked);
        assertTrue(ItemStack.isSameItemSameComponents(picked, placed.toItemStack(registries)));
    }

    private static FixtureBlockEntity marker() {
        return new FixtureBlockEntity(BlockPos.ZERO, ASBlocks.FIXTURE.get().defaultBlockState());
    }
}
