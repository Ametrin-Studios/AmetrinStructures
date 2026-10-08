package com.ametrin.structures.structure.filter;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.EnumMap;
import java.util.Map;

/// Cached terrain lookups for one generation attempt, shared by the start height and the filters.
/// Prefer it over the chunk generator: every uncached lookup evaluates the noise of a whole column.
public final class TerrainSampler {
    private final Structure.GenerationContext context;
    private final Heightmap.Types heightmap;
    private final Map<Heightmap.Types, Long2IntMap> heights = new EnumMap<>(Heightmap.Types.class);
    private final Long2ObjectMap<NoiseColumn> columns = new Long2ObjectOpenHashMap<>();

    /// @param heightmap the heightmap the structure stands on
    public TerrainSampler(Structure.GenerationContext context, Heightmap.Types heightmap) {
        this.context = context;
        this.heightmap = heightmap;
    }

    public Heightmap.Types heightmap() {
        return heightmap;
    }

    /// The first free Y on [#heightmap()].
    public int surfaceHeight(int x, int z) {
        return surfaceHeight(heightmap, x, z);
    }

    public int surfaceHeight(Heightmap.Types type, int x, int z) {
        return heights(type).computeIfAbsent(ColumnPos.asLong(x, z), _ -> context.chunkGenerator()
                .getFirstFreeHeight(x, z, type, context.heightAccessor(), context.randomState()));
    }

    public int columnHeight(Heightmap.Types type, int x, int z) {
        return heights(type).computeIfAbsent(ColumnPos.asLong(x, z), _ -> {
            // As the chunk generator measures it: one above the highest block the heightmap counts.
            var column = column(x, z);
            var opaque = type.isOpaque();
            var level = context.heightAccessor();
            for (int y = level.getMaxY(); y >= level.getMinY(); y--) {
                if (opaque.test(column.getBlock(y))) {
                    return y + 1;
                }
            }
            return level.getMinY();
        });
    }

    private Long2IntMap heights(Heightmap.Types type) {
        return heights.computeIfAbsent(type, _ -> new Long2IntOpenHashMap());
    }

    public NoiseColumn column(int x, int z) {
        return columns.computeIfAbsent(ColumnPos.asLong(x, z), _ -> context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState()));
    }
}
