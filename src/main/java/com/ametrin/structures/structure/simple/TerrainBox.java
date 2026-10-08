package com.ametrin.structures.structure.simple;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// The part of a template that terrain adaptation fits the terrain to.
public sealed interface TerrainBox {
    Codec<TerrainBox> CODEC = Codec.either(Automatic.CODEC, BoundingBox.CODEC)
            .xmap(
                    either -> either.map(automatic -> automatic, Local::new),
                    box -> switch (box) {
                        case Automatic automatic -> Either.left(automatic);
                        case Local(BoundingBox local) -> Either.right(local);
                    });

    /// The whole template.
    static TerrainBox template() {
        return Automatic.TEMPLATE;
    }

    /// The blocks at and below ground level
    static TerrainBox footprint() {
        return Automatic.FOOTPRINT;
    }

    /// in template coordinates, before any rotation
    static TerrainBox of(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new Local(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
    }

    /// The box in template coordinates, or empty for the whole template.
    ///
    /// @param groundLevelDelta how far above the bottom of the template the ground lies
    @ApiStatus.Internal
    Optional<BoundingBox> resolve(StructureTemplate template, int groundLevelDelta);

    enum Automatic implements TerrainBox, StringRepresentable {
        TEMPLATE("template"),
        FOOTPRINT("footprint");

        public static final Codec<Automatic> CODEC = StringRepresentable.fromEnum(Automatic::values);

        // Keyed by the template's first palette: saving or loading a template replaces its palettes,
        // so a re-saved template gets a fresh footprint and the old entry is collected.
        private static final LoadingCache<StructureTemplate.Palette, Map<Integer, Optional<BoundingBox>>> FOOTPRINTS =
                CacheBuilder.newBuilder().weakKeys().build(CacheLoader.from(() -> new ConcurrentHashMap<>()));

        private final String name;

        Automatic(String name) {
            this.name = name;
        }

        @Override
        public Optional<BoundingBox> resolve(StructureTemplate template, int groundLevelDelta) {
            return this == FOOTPRINT ? footprint(template, groundLevelDelta) : Optional.empty();
        }

        private static Optional<BoundingBox> footprint(StructureTemplate template, int groundLevelDelta) {
            if (template.palettes.isEmpty()) {
                return Optional.empty();
            }
            return FOOTPRINTS.getUnchecked(template.palettes.getFirst())
                    .computeIfAbsent(groundLevelDelta, delta -> computeFootprint(template, delta));
        }

        // A raised template has no blocks below the ground, so its bottom layer counts as ground level.
        private static Optional<BoundingBox> computeFootprint(StructureTemplate template, int groundLevelDelta) {
            var groundLayer = Math.max(groundLevelDelta, 0);
            var grounded = template.palettes.stream()
                    .flatMap(palette -> palette.blocks().stream())
                    .filter(block -> block.pos().getY() <= groundLayer && !block.state().isAir())
                    .map(StructureTemplate.StructureBlockInfo::pos)
                    .toList();
            var height = template.getSize().getY();
            return BoundingBox.encapsulatingPositions(grounded).map(box ->
                    BoundingBox.fromCorners(new BlockPos(box.minX(), 0, box.minZ()), new BlockPos(box.maxX(), height - 1, box.maxZ())));
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    record Local(BoundingBox box) implements TerrainBox {
        @Override
        public Optional<BoundingBox> resolve(StructureTemplate template, int groundLevelDelta) {
            return Optional.of(box);
        }
    }
}
