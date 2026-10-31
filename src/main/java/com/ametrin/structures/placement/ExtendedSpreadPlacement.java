package com.ametrin.structures.placement;

import com.ametrin.structures.util.ASCodecs;
import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public abstract class ExtendedSpreadPlacement extends RandomSpreadStructurePlacement { // extends RandomSpreadStructurePlacement because /locate and similar have no generic case
    private final Optional<TagExclusionZone> tagExclusionZone;
    private final int minChunksFromCenter;

    protected ExtendedSpreadPlacement(Vec3i locateOffset, float probability, int salt, Optional<TagExclusionZone> exclusionZone, int minChunksFromCenter, int spacing, int separation) {
        super(locateOffset, FrequencyReductionMethod.DEFAULT, probability, salt, Optional.empty(), spacing, separation, RandomSpreadType.LINEAR);
        this.tagExclusionZone = exclusionZone;
        this.minChunksFromCenter = minChunksFromCenter;
    }

    protected static <P extends ExtendedSpreadPlacement> Products.P5<RecordCodecBuilder.Mu<P>, Vec3i, Float, Integer, Optional<TagExclusionZone>, Integer> spreadCodec(RecordCodecBuilder.Instance<P> instance) {
        return instance.group(
                Vec3i.CODEC.optionalFieldOf("locate_offset", Vec3i.ZERO).forGetter(ExtendedSpreadPlacement::locateOffset),
                Codec.FLOAT.optionalFieldOf("probability", 1.0F).forGetter(ExtendedSpreadPlacement::frequency),
                Codec.INT.optionalFieldOf("salt", 0).forGetter(ExtendedSpreadPlacement::salt),
                TagExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(ExtendedSpreadPlacement::structureExclusionZone),
                Codec.INT.optionalFieldOf("min_chunks_from_center", 0).forGetter(ExtendedSpreadPlacement::minChunksFromCenter));
    }

    /// For the codec and the builder, so both reject the same settings.
    protected static <P extends ExtendedSpreadPlacement> DataResult<P> validate(P placement) {
        return ASCodecs.check(placement, placement.problem());
    }

    /// Why the settings are invalid, if they are. Subclasses check their own settings first.
    protected Optional<String> problem() {
        if (frequency() < 0.0F || frequency() > 1.0F) {
            return Optional.of("probability " + frequency() + " is outside 0 to 1");
        }
        if (minChunksFromCenter < 0) {
            return Optional.of("min_chunks_from_center " + minChunksFromCenter + " is negative");
        }
        return tagExclusionZone.flatMap(TagExclusionZone::problem);
    }

    /// Minimum distance from the world origin, in chunks
    public int minChunksFromCenter() {
        return minChunksFromCenter;
    }

    public boolean isTooCloseToCenter(int chunkX, int chunkZ) {
        return StructurePlacements.isTooCloseToCenter(chunkX, chunkZ, minChunksFromCenter);
    }

    public Optional<TagExclusionZone> structureExclusionZone() {
        return tagExclusionZone;
    }

    // Locate checks this, not isStructureChunk, so it skips these spots without loading them.
    @Override
    public boolean applyAdditionalChunkRestrictions(int x, int z, long seed) {
        return !isTooCloseToCenter(x, z) && super.applyAdditionalChunkRestrictions(x, z, seed);
    }

    @Override
    public boolean applyInteractionsWithOtherStructures(ChunkGeneratorStructureState state, int x, int z) {
        return tagExclusionZone.isEmpty() || !tagExclusionZone.get().isForbidden(state, x, z, this);
    }

    public abstract static class Builder<B extends Builder<B, P>, P extends ExtendedSpreadPlacement> {
        private Vec3i locateOffset = Vec3i.ZERO;
        private float probability = 1.0F;
        @Nullable
        private Integer salt;
        private Optional<TagExclusionZone> exclusionZone = Optional.empty();
        private int minChunksFromCenter = 0;

        protected abstract B self();

        protected abstract P create(Vec3i locateOffset, float probability, int salt, Optional<TagExclusionZone> exclusionZone, int minChunksFromCenter);

        public B locateOffset(Vec3i locateOffset) {
            this.locateOffset = locateOffset;
            return self();
        }

        public B probability(float probability) {
            this.probability = probability;
            return self();
        }

        public B salt(int salt) {
            this.salt = salt;
            return self();
        }

        public B salt(String namespace, String id) {
            this.salt = StructurePlacements.salt(namespace, id);
            return self();
        }

        public B exclusionZone(TagKey<Structure> structures, int chunkCount) {
            this.exclusionZone = Optional.of(new TagExclusionZone(structures, chunkCount));
            return self();
        }

        /// Keeps the structure at least this many chunks, in a straight line, from the world origin.
        public B minChunksFromCenter(int minChunksFromCenter) {
            this.minChunksFromCenter = minChunksFromCenter;
            return self();
        }

        /// @throws IllegalStateException when a setting is out of range
        public P build() {
            return build(salt == null ? 0 : salt);
        }

        /// salt defaults to one derived from `id`.
        public P build(Identifier id) {
            return build(id.getNamespace(), id.getPath());
        }

        /// salt defaults to one derived from `namespace` and `id`.
        public P build(String namespace, String id) {
            return build(salt == null ? StructurePlacements.salt(namespace, id) : salt);
        }

        private P build(int salt) {
            return validate(create(locateOffset, probability, salt, exclusionZone, minChunksFromCenter)).getOrThrow(IllegalStateException::new);
        }
    }
}
