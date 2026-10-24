package com.ametrin.structures.processor;

import com.ametrin.structures.registry.ASProcessors;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/// Support comes from the template's own blocks first, then from the world.
public final class RemoveUnsupportedProcessor extends StructureProcessor {
    public static final RemoveUnsupportedProcessor INSTANCE = new RemoveUnsupportedProcessor();
    public static final MapCodec<RemoveUnsupportedProcessor> CODEC = MapCodec.unit(INSTANCE);

    private RemoveUnsupportedProcessor() {}

    // Runs on the whole processed template, since the world doesn't contain the template's blocks yet.
    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level,
            BlockPos position,
            BlockPos referencePos,
            List<StructureTemplate.StructureBlockInfo> original,
            List<StructureTemplate.StructureBlockInfo> processed,
            StructurePlaceSettings settings) {
        if (processed.stream().noneMatch(info -> info.state().getBlock() instanceof MultifaceBlock)) {
            return processed;
        }
        var placed = new HashMap<BlockPos, BlockState>();
        processed.forEach(info -> placed.put(info.pos(), info.state()));
        var result = new ArrayList<StructureTemplate.StructureBlockInfo>(processed.size());
        for (var info : processed) {
            if (!(info.state().getBlock() instanceof MultifaceBlock)) {
                result.add(info);
                continue;
            }
            var state = info.state();
            for (var direction : Direction.values()) {
                if (!MultifaceBlock.hasFace(state, direction)) {
                    continue;
                }
                var neighbourPos = info.pos().relative(direction);
                var neighbour = placed.containsKey(neighbourPos) ? placed.get(neighbourPos) : level.getBlockState(neighbourPos);
                if (!MultifaceBlock.canAttachTo(level, direction, neighbourPos, neighbour)) {
                    state = state.setValue(MultifaceBlock.getFaceProperty(direction), false);
                }
            }
            result.add(MultifaceBlock.availableFaces(state).isEmpty()
                    ? new StructureTemplate.StructureBlockInfo(info.pos(), Blocks.AIR.defaultBlockState(), null)
                    : new StructureTemplate.StructureBlockInfo(info.pos(), state, info.nbt()));
        }
        return result;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ASProcessors.REMOVE_UNSUPPORTED.get();
    }
}
