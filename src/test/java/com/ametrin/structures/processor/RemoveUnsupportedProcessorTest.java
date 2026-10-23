package com.ametrin.structures.processor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemoveUnsupportedProcessorTest {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState RESIN = Blocks.RESIN_CLUMP.defaultBlockState()
            .setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), true)
            .setValue(MultifaceBlock.getFaceProperty(Direction.UP), true);

    @Test
    void sidesOnTheTemplatesBlocksStayAndOthersGo() {
        var result = process(AIR, info(BlockPos.ZERO, RESIN), info(BlockPos.ZERO.north(), STONE));
        var resin = result.getFirst().state();
        assertTrue(MultifaceBlock.hasFace(resin, Direction.NORTH));
        assertFalse(MultifaceBlock.hasFace(resin, Direction.UP));
    }

    @Test
    void theWorldSupportsWhereTheTemplatePlacesNothing() {
        var resin = process(STONE, info(BlockPos.ZERO, RESIN)).getFirst().state();
        assertEquals(RESIN, resin);
    }

    @Test
    void templateAirDoesNotSupportEvenOverSolidWorld() {
        var result = process(STONE, info(BlockPos.ZERO, RESIN), info(BlockPos.ZERO.north(), AIR), info(BlockPos.ZERO.above(), AIR));
        assertEquals(List.of(AIR, AIR), result.stream().map(StructureTemplate.StructureBlockInfo::state).toList(), "a block without sides is removed");
    }

    private static StructureTemplate.StructureBlockInfo info(BlockPos pos, BlockState state) {
        return new StructureTemplate.StructureBlockInfo(pos, state, (CompoundTag) null);
    }

    private static List<StructureTemplate.StructureBlockInfo> process(BlockState world, StructureTemplate.StructureBlockInfo... blocks) {
        var list = List.of(blocks);
        return RemoveUnsupportedProcessor.INSTANCE.finalizeProcessing(level(world), BlockPos.ZERO, BlockPos.ZERO, list, list, new StructurePlaceSettings());
    }

    // Every position outside the template holds `world`.
    private static ServerLevelAccessor level(BlockState world) {
        return (ServerLevelAccessor) Proxy.newProxyInstance(ServerLevelAccessor.class.getClassLoader(), new Class<?>[]{ServerLevelAccessor.class}, (_, method, _) -> {
            if (method.getName().equals("getBlockState")) {
                return world;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }
}
