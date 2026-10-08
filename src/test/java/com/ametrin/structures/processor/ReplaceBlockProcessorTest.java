package com.ametrin.structures.processor;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

/// The replace processor as a datapack author writes it. Runs on a server so block tags are bound.
@ExtendWith(EphemeralTestServerProvider.class)
class ReplaceBlockProcessorTest {
    private static final BlockState NORTH_TOP_OAK_STAIRS = Blocks.OAK_STAIRS.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.NORTH)
            .setValue(StairBlock.HALF, Half.TOP);

    /// The server only starts when a test asks for it, and block tags are only bound once it has.
    @BeforeAll
    static void bindTags(MinecraftServer server) {}

    @Test
    void theConditionTakesABlockATagOrAState() {
        assertInstanceOf(ReplaceBlockProcessor.Condition.IsBlock.class, condition("\"minecraft:oak_planks\""));
        assertInstanceOf(ReplaceBlockProcessor.Condition.InTag.class, condition("\"#minecraft:stairs\""));
        assertInstanceOf(
                ReplaceBlockProcessor.Condition.IsState.class,
                condition("{\"id\":\"minecraft:oak_stairs\",\"properties\":{\"facing\":\"north\"}}"));
    }

    @Test
    void conditionsRoundTrip() {
        for (String json : new String[]{"\"minecraft:oak_planks\"", "\"#minecraft:stairs\""}) {
            JsonElement written = ReplaceBlockProcessor.Condition.CODEC
                    .encodeStart(JsonOps.INSTANCE, condition(json))
                    .getOrThrow();
            assertEquals(JsonParser.parseString(json), written);
        }
    }

    @Test
    void aBlockMatchesEveryState() {
        assertTrue(condition("\"minecraft:oak_stairs\"").test(NORTH_TOP_OAK_STAIRS));
        assertTrue(!condition("\"minecraft:oak_stairs\"").test(Blocks.SPRUCE_STAIRS.defaultBlockState()));
    }

    @Test
    void aTagMatchesItsMembers() {
        assertTrue(condition("\"#minecraft:stairs\"").test(Blocks.SPRUCE_STAIRS.defaultBlockState()));
        assertTrue(!condition("\"#minecraft:stairs\"").test(Blocks.OAK_PLANKS.defaultBlockState()));
    }

    @Test
    void aStateMatchesOnlyThatState() {
        var condition = condition("{\"id\":\"minecraft:oak_stairs\",\"properties\":{\"facing\":\"north\",\"half\":\"top\"}}");
        assertTrue(condition.test(NORTH_TOP_OAK_STAIRS));
        assertTrue(!condition.test(NORTH_TOP_OAK_STAIRS.setValue(StairBlock.FACING, Direction.EAST)));
    }

    @Test
    void withoutPreserveStateTheTargetIsPlacedAsDeclared() {
        var processor = processor("{\"condition\":\"#minecraft:stairs\",\"chance\":1,\"change_to\":\"minecraft:spruce_stairs\"}");
        assertEquals(Blocks.SPRUCE_STAIRS.defaultBlockState(), process(processor, NORTH_TOP_OAK_STAIRS));
    }

    @Test
    void preserveStateKeepsTheShapeOfTheReplacedBlock() {
        var processor = processor(
                "{\"condition\":\"#minecraft:stairs\",\"chance\":1,\"change_to\":\"minecraft:spruce_stairs\",\"preserve_state\":true}");
        assertEquals(
                Blocks.SPRUCE_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.NORTH)
                        .setValue(StairBlock.HALF, Half.TOP),
                process(processor, NORTH_TOP_OAK_STAIRS));
    }

    @Test
    void chanceZeroNeverReplaces() {
        var processor = processor("{\"condition\":\"minecraft:oak_stairs\",\"chance\":0,\"change_to\":\"minecraft:air\"}");
        assertEquals(NORTH_TOP_OAK_STAIRS, process(processor, NORTH_TOP_OAK_STAIRS));
    }

    @Test
    void blockEntityDataOnlyStaysWithTheSameBlockOrPreservedState() {
        var data = new CompoundTag();
        data.putString("LootTable", "minecraft:chests/simple_dungeon");
        var chest = new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, Blocks.CHEST.defaultBlockState(), data);
        var toBarrel = processor("{\"condition\":\"minecraft:chest\",\"chance\":1,\"change_to\":\"minecraft:barrel\"}");
        var toChest = processor("{\"condition\":\"minecraft:chest\",\"chance\":1,\"change_to\":{\"id\":\"minecraft:chest\",\"properties\":{\"facing\":\"east\"}}}");
        var preserving = processor("{\"condition\":\"minecraft:chest\",\"chance\":1,\"change_to\":\"minecraft:barrel\",\"preserve_state\":true}");
        assertNull(process(toBarrel, chest).nbt());
        assertEquals(data, process(toChest, chest).nbt());
        assertEquals(data, process(preserving, chest).nbt());
    }

    private static ReplaceBlockProcessor.Condition condition(String json) {
        return ReplaceBlockProcessor.Condition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static ReplaceBlockProcessor processor(String json) {
        return ReplaceBlockProcessor.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static BlockState process(ReplaceBlockProcessor processor, BlockState state) {
        return process(processor, new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, state, (CompoundTag) null)).state();
    }

    @SuppressWarnings("DataFlowIssue") // the processor never reads the level
    private static StructureTemplate.StructureBlockInfo process(ReplaceBlockProcessor processor, StructureTemplate.StructureBlockInfo info) {
        return processor.process(null, BlockPos.ZERO, BlockPos.ZERO, info, info, new StructurePlaceSettings(), null);
    }
}
