package com.ametrin.structures.fixture;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class FieldTypeTest {
    @Test
    void itemsTakeComponentsAndAnOptionalCount(MinecraftServer server) {
        var registries = server.registryAccess();
        var item = FieldType.item().parser().parse("minecraft:leather_helmet[dyed_color=255] 2", registries).getOrThrow();
        assertEquals(Items.LEATHER_HELMET, item.item().value());
        assertEquals(2, item.count());
        assertEquals(new DyedItemColor(255), item.get(DataComponents.DYED_COLOR));
        assertEquals(1, FieldType.item().parser().parse("minecraft:diamond", registries).getOrThrow().count());
    }

    @Test
    void itemsReadBackAsTheTextThatParsesToThem(MinecraftServer server) {
        var registries = server.registryAccess();
        var type = FieldType.item();
        for (var text : new String[]{"minecraft:diamond", "minecraft:diamond 3", "minecraft:leather_helmet[minecraft:dyed_color=255]"}) {
            var tag = type.textToTag(text, registries).getOrThrow();
            assertEquals(text, type.tagToText(tag, registries).getOrThrow());
        }
    }

    @Test
    void unknownItemsAndEmptyCountsAreRejected(MinecraftServer server) {
        var registries = server.registryAccess();
        var parser = FieldType.item().parser();
        assertTrue(parser.parse("minecraft:not_an_item", registries).isError());
        assertTrue(parser.parse("minecraft:diamond 0", registries).isError());
        assertTrue(parser.parse("minecraft:diamond many", registries).isError());
    }

    @Test
    void mergedBlockStatesLeaveOutWhatTheMarkerReplaces() {
        assertEquals("minecraft:chest[type=single]", BlockStateMerging.serialize(Blocks.CHEST.defaultBlockState()));
        var turned = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST).setValue(ChestBlock.WATERLOGGED, true);
        assertEquals("minecraft:chest[facing=east,type=single]", BlockStateMerging.serialize(turned), "a facing other than the default is a turn");
        assertEquals("minecraft:oak_log", BlockStateMerging.serialize(Blocks.OAK_LOG.defaultBlockState()));
    }

    @Test
    void numbersStayInTheirRange(MinecraftServer server) {
        var registries = server.registryAccess();
        assertTrue(FieldType.integer(1, 5).parser().parse("5", registries).isSuccess());
        assertTrue(FieldType.integer(1, 5).parser().parse("6", registries).isError());
        assertTrue(FieldType.number(0.0F, 1.0F).parser().parse("1.5", registries).isError());
    }

    @Test
    void validationAppliesWhenTypedAndWhenStored(MinecraftServer server) {
        var registries = server.registryAccess();
        var chest = FieldType.blockState().validated(state -> state.is(Blocks.CHEST), "a chest");
        assertEquals(Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST),
                chest.parser().parse("minecraft:chest[facing=east]", registries).getOrThrow());
        assertTrue(chest.parser().parse("minecraft:stone", registries).isError());
        var stone = FieldType.blockState().textToTag("minecraft:stone", registries).getOrThrow();
        assertFalse(chest.tagToText(stone, registries).isSuccess());
    }
}
