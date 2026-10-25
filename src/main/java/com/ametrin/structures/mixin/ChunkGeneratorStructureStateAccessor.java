package com.ametrin.structures.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ChunkGeneratorStructureState.class)
public interface ChunkGeneratorStructureStateAccessor {
    @Mutable
    @Accessor("possibleStructureSets")
    void ametrin$setPossibleStructureSets(List<Holder<StructureSet>> sets);
}
