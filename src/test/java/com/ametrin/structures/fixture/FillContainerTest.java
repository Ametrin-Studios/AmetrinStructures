package com.ametrin.structures.fixture;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class FillContainerTest {
    // Decides like a brewing stand in a level: the test server has none to give it.
    private static final Container JUDGE = new SimpleContainer(5) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot < 3 ? stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) : slot == 4 && stack.is(Items.BLAZE_POWDER);
        }
    };

    @Test
    void eachItemGoesToASlotThatTakesIt() {
        var stand = brewingStand();
        var items = List.of(new ItemStack(Items.POTION), new ItemStack(Items.SPLASH_POTION), new ItemStack(Items.POTION), new ItemStack(Items.BLAZE_POWDER, 3));
        var leftOver = Fixtures.FillContainer.fill(stand, JUDGE, items, RandomSource.create(0));

        assertEquals(List.of(), leftOver);
        assertEquals(List.of("minecraft:potion", "minecraft:potion", "minecraft:splash_potion"), IntStream.range(0, 3)
                .mapToObj(slot -> BuiltInRegistries.ITEM.getKey(stand.getItem(slot).getItem()).toString())
                .sorted()
                .toList());
        assertTrue(stand.getItem(3).isEmpty());
        assertEquals(3, stand.getItem(4).getCount());
    }

    @Test
    void whatFitsNowhereIsLeftOver() {
        var stand = brewingStand();
        var items = List.of(new ItemStack(Items.POTION), new ItemStack(Items.POTION), new ItemStack(Items.POTION), new ItemStack(Items.POTION), new ItemStack(Items.STONE));
        var leftOver = Fixtures.FillContainer.fill(stand, JUDGE, items, RandomSource.create(0));
        assertEquals(List.of(Items.POTION, Items.STONE), leftOver.stream().map(ItemStack::getItem).toList());
    }

    @Test
    void aBlockWithoutAnInventoryIsRejected() {
        var json = JsonParser.parseString("""
                {"type": "ametrin_structures:fill_container", "loot_table": "minecraft:chests/simple_dungeon", "block": "minecraft:stone"}
                """);
        assertTrue(Fixture.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError());
    }

    @Test
    void anyBlockWithAnInventoryIsAccepted() {
        var json = JsonParser.parseString("""
                {"type": "ametrin_structures:fill_container", "loot_table": "minecraft:chests/simple_dungeon", "block": "minecraft:furnace"}
                """);
        var fixture = assertInstanceOf(Fixtures.FillContainer.class, Fixture.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(Blocks.FURNACE, fixture.block().getBlock());
    }

    @Test
    void anItemGoesToTheNarrowestSlotThatTakesIt() {
        // Like a brewing stand in a level: the ingredient slot also takes blaze powder.
        var judge = new SimpleContainer(5) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return switch (slot) {
                    case 3 -> stack.is(Items.BLAZE_POWDER) || stack.is(Items.NETHER_WART) || stack.is(Items.SUGAR);
                    case 4 -> stack.is(Items.BLAZE_POWDER);
                    default -> stack.is(Items.POTION);
                };
            }
        };
        for (int seed = 0; seed < 20; seed++) {
            var stand = brewingStand();
            Fixtures.FillContainer.fill(stand, judge, List.of(new ItemStack(Items.BLAZE_POWDER, 2)), RandomSource.create(seed));
            assertEquals(2, stand.getItem(4).getCount(), "seed " + seed);
            assertTrue(stand.getItem(3).isEmpty(), "seed " + seed);
        }
    }

    @Test
    void aChiseledBookshelfFillsWithoutALevel() {
        var shelf = new ChiseledBookShelfBlockEntity(BlockPos.ZERO, Blocks.CHISELED_BOOKSHELF.defaultBlockState());
        var items = List.of(new ItemStack(Items.STONE), new ItemStack(Items.BOOK, 2));
        var leftOver = Fixtures.FillContainer.fill(shelf, shelf, items, RandomSource.create(0));
        assertEquals(List.of(Items.STONE), leftOver.stream().map(ItemStack::getItem).toList(), "the shelf turns away anything but books");
        assertEquals(2, IntStream.range(0, shelf.getContainerSize()).filter(slot -> !shelf.getItem(slot).isEmpty()).count());
    }

    @Test
    void theBlockIsWrittenWithoutWhatTheLootDecides(MinecraftServer server) {
        var registries = server.registryAccess();
        var state = Blocks.BREWING_STAND.defaultBlockState().setValue(BlockStateProperties.HAS_BOTTLE_1, true);
        var tag = BlockState.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), state).getOrThrow();
        assertEquals("minecraft:brewing_stand", Fixtures.FillContainer.BLOCK.type().tagToText(tag, registries).getOrThrow());
    }

    // As one still generating: without a level.
    private static BrewingStandBlockEntity brewingStand() {
        return new BrewingStandBlockEntity(BlockPos.ZERO, Blocks.BREWING_STAND.defaultBlockState());
    }
}
