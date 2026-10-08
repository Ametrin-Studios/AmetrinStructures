package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RemoveFoamProcessorTest {
    private static final BlockState WATER_SOURCE = Blocks.WATER.defaultBlockState();

    // The server only starts when a test asks for it, and block tags are only bound once it has.
    @BeforeAll
    static void bindTags(MinecraftServer server) {}

    @Test
    void plainRemovalTurnsFoamIntoAirAndDropsSavedAir() {
        assertEquals(Blocks.AIR.defaultBlockState(), process(RemoveFoamProcessor.AIR, foam()));
        assertNull(process(RemoveFoamProcessor.AIR, Blocks.AIR.defaultBlockState()));
    }

    @Test
    void plainRemovalLeavesWaterloggableBlocksDry() {
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        assertEquals(slab, process(RemoveFoamProcessor.AIR, slab));
    }

    @Test
    void fillingTurnsFoamIntoASource() {
        assertEquals(WATER_SOURCE, process(RemoveFoamProcessor.WATER, foam()));
    }

    @Test
    void fillingWithWaterWaterlogsWhatCanBe() {
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        assertEquals(slab.setValue(BlockStateProperties.WATERLOGGED, true), process(RemoveFoamProcessor.WATER, slab));
        assertEquals(Blocks.STONE.defaultBlockState(), process(RemoveFoamProcessor.WATER, Blocks.STONE.defaultBlockState()));
    }

    @Test
    void fillingWithLavaWaterlogsNothing() {
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        RemoveFoamProcessor lava = new RemoveFoamProcessor(Fluids.LAVA.defaultFluidState().createLegacyBlock());
        assertEquals(Blocks.LAVA.defaultBlockState(), process(lava, foam()));
        assertEquals(slab, process(lava, slab));
    }

    @Test
    void aSpreaderSavedMidFillIsRemoved() {
        assertEquals(Blocks.AIR.defaultBlockState(), process(RemoveFoamProcessor.AIR, ASBlocks.FOAM_SPREADER.get().defaultBlockState()));
    }

    @Test
    void savedAirStaysVoidWhenFilled() {
        assertNull(process(RemoveFoamProcessor.WATER, Blocks.AIR.defaultBlockState()));
    }

//    @Test
//    void fillRoundTrips() {
//        var json = JsonParser.parseString("{\"fill\":\"minecraft:water\"}");
//        RemoveFoamProcessor parsed = RemoveFoamProcessor.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
//        assertEquals(Optional.of(Fluids.WATER), parsed.fill());
//        assertEquals(json, RemoveFoamProcessor.CODEC.codec().encodeStart(JsonOps.INSTANCE, parsed).getOrThrow());
//    }

    @Test
    void aDeclaredRemovalKeepsItsPlace() {
        var other = new BlockIgnoreProcessor(List.of(Blocks.STONE));
        var settings = new StructurePlaceSettings().addProcessor(other).addProcessor(RemoveFoamProcessor.WATER);
        assertEquals(List.of(other, RemoveFoamProcessor.WATER), RemoveFoamProcessor.addDefaultIfAbsent(settings).getProcessors());
    }

    @Test
    void airAnEarlierProcessorProducedIsPlaced() {
        // A jigsaw block whose final state is air, or foam a first removal already turned into air.
        var produced = process(RemoveFoamProcessor.AIR, Blocks.JIGSAW.defaultBlockState(), Blocks.AIR.defaultBlockState());
        assertEquals(Blocks.AIR.defaultBlockState(), produced);
        assertEquals(Blocks.AIR.defaultBlockState(), process(RemoveFoamProcessor.AIR, foam(), Blocks.AIR.defaultBlockState()));
    }

    @Test
    void savedAirAnEarlierProcessorReplacedIsKept() {
        assertEquals(Blocks.COBWEB.defaultBlockState(),
                process(RemoveFoamProcessor.AIR, Blocks.AIR.defaultBlockState(), Blocks.COBWEB.defaultBlockState()));
    }

    @Test
    void withoutADeclaredRemovalThePlainOneIsInstalled() {
        var other = new BlockIgnoreProcessor(List.of(Blocks.STONE));
        var settings = new StructurePlaceSettings().addProcessor(other);
        var installed = RemoveFoamProcessor.addDefaultIfAbsent(settings).getProcessors();
        assertSame(RemoveFoamProcessor.AIR, installed.getFirst());
        assertEquals(2, installed.size());
    }

    private static BlockState foam() {
        return ASBlocks.FOAM.get().defaultBlockState();
    }

    private static @Nullable BlockState process(RemoveFoamProcessor processor, BlockState state) {
        return process(processor, state, state);
    }

    /// `saved` is what the template holds, `current` what earlier processors made of it.
    @SuppressWarnings("DataFlowIssue") // the processor never reads the level
    private static @Nullable BlockState process(RemoveFoamProcessor processor, BlockState saved, BlockState current) {
        var original = new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, saved, (CompoundTag) null);
        var info = new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, current, (CompoundTag) null);
        var result = processor.process(null, BlockPos.ZERO, BlockPos.ZERO, original, info, new StructurePlaceSettings(), null);
        return result == null ? null : result.state();
    }
}
