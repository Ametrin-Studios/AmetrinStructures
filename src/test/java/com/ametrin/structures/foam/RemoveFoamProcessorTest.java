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
        assertEquals(Blocks.AIR.defaultBlockState(), process(RemoveFoamProcessor.AIR, ASBlocks.FOAM_SPREADER.get().defaultBlockState()), "a spreader saved mid fill");
        assertNull(process(RemoveFoamProcessor.AIR, Blocks.AIR.defaultBlockState()));
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        assertEquals(slab, process(RemoveFoamProcessor.AIR, slab), "waterloggable blocks stay dry");
    }

    @Test
    void fillingWithWaterTurnsFoamIntoASourceAndWaterlogsWhatCanBe() {
        assertEquals(WATER_SOURCE, process(RemoveFoamProcessor.WATER, foam()));
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        assertEquals(slab.setValue(BlockStateProperties.WATERLOGGED, true), process(RemoveFoamProcessor.WATER, slab));
        assertEquals(Blocks.STONE.defaultBlockState(), process(RemoveFoamProcessor.WATER, Blocks.STONE.defaultBlockState()));
        assertNull(process(RemoveFoamProcessor.WATER, Blocks.AIR.defaultBlockState()), "saved air stays void");
    }

    @Test
    void fillingWithLavaWaterlogsNothing() {
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        RemoveFoamProcessor lava = new RemoveFoamProcessor(Fluids.LAVA.defaultFluidState().createLegacyBlock());
        assertEquals(Blocks.LAVA.defaultBlockState(), process(lava, foam()));
        assertEquals(slab, process(lava, slab));
    }

    @Test
    void whatEarlierProcessorsMadeIsKept() {
        // A jigsaw block whose final state is air, or foam a first removal already turned into air.
        assertEquals(Blocks.AIR.defaultBlockState(), process(RemoveFoamProcessor.AIR, Blocks.JIGSAW.defaultBlockState(), Blocks.AIR.defaultBlockState()));
        assertEquals(Blocks.AIR.defaultBlockState(), process(RemoveFoamProcessor.AIR, foam(), Blocks.AIR.defaultBlockState()));
        assertEquals(Blocks.COBWEB.defaultBlockState(), process(RemoveFoamProcessor.AIR, Blocks.AIR.defaultBlockState(), Blocks.COBWEB.defaultBlockState()), "saved air an earlier processor replaced");
    }

    @Test
    void thePlainRemovalIsInstalledFirstUnlessOneIsDeclared() {
        var other = new BlockIgnoreProcessor(List.of(Blocks.STONE));
        var declared = new StructurePlaceSettings().addProcessor(other).addProcessor(RemoveFoamProcessor.WATER);
        assertEquals(List.of(other, RemoveFoamProcessor.WATER), RemoveFoamProcessor.addDefaultIfAbsent(declared).getProcessors());
        var installed = RemoveFoamProcessor.addDefaultIfAbsent(new StructurePlaceSettings().addProcessor(other)).getProcessors();
        assertEquals(List.of(RemoveFoamProcessor.AIR, other), installed);
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
