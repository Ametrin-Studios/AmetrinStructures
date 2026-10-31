package com.ametrin.structures.placement;

import com.ametrin.structures.util.ASCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.Optional;

/// Keeps a placement `chunkCount` chunks away from the structures in a tag.
public record TagExclusionZone(TagKey<Structure> structures, int chunkCount) {
    public static final int MAX_CHUNK_COUNT = 16;

    public static final Codec<TagExclusionZone> CODEC = RecordCodecBuilder.<TagExclusionZone>create(instance -> instance.group(
                            TagKey.codec(Registries.STRUCTURE).fieldOf("structures").forGetter(TagExclusionZone::structures),
                            Codec.INT.fieldOf("chunk_count").forGetter(TagExclusionZone::chunkCount))
                    .apply(instance, TagExclusionZone::new))
            .validate(zone -> ASCodecs.check(zone, zone.problem()));

    private static final ThreadLocal<Boolean> CHECKING = ThreadLocal.withInitial(() -> false);

    Optional<String> problem() {
        return chunkCount < 1 || chunkCount > MAX_CHUNK_COUNT
                ? Optional.of("exclusion_zone chunk_count " + chunkCount + " is outside 1 to " + MAX_CHUNK_COUNT)
                : Optional.empty();
    }

    // Nested checks skip exclusion zones, so placements that exclude each other don't recurse forever.
    public boolean isForbidden(ChunkGeneratorStructureState state, int x, int z, StructurePlacement own) {
        if (CHECKING.get()) {
            return false;
        }
        CHECKING.set(true);
        try {
            return StructurePlacements.hasStructureChunkInRange(
                    state, set -> set.value().placement() != own, holder -> holder.is(structures), x, z, chunkCount);
        } finally {
            CHECKING.set(false);
        }
    }
}
