package com.ametrin.structures.debug;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/// The chunks that placements pick within `radius` chunks of `center`, nearest first.
/// They're found ring by ring as they're asked for, so stopping early skips the rest of the radius.
///
/// @param <T> what each placement belongs to, e.g. a structure set
final class StructureCandidates<T> implements Iterator<StructureCandidates.Candidate<T>> {
    private static final int RING_WIDTH = 32;

    record Candidate<T>(ChunkPos chunk, T source) {}

    private final ChunkGeneratorStructureState state;
    private final List<T> sources;
    private final Function<T, StructurePlacement> placement;
    private final ChunkPos center;
    private final int radius;
    private final PriorityQueue<Candidate<T>> pending;
    // Chunks this far from the center along an axis, or farther, haven't been looked at yet.
    private int scanned;

    StructureCandidates(ChunkGeneratorStructureState state, List<T> sources, Function<T, StructurePlacement> placement, ChunkPos center, int radius) {
        this.state = state;
        this.sources = List.copyOf(sources);
        this.placement = placement;
        this.center = center;
        this.radius = radius;
        this.pending = new PriorityQueue<>(Comparator.comparingInt(candidate -> candidate.chunk().distanceSquared(center)));
    }

    @Override
    public boolean hasNext() {
        fill();
        return !pending.isEmpty();
    }

    @Override
    public Candidate<T> next() {
        fill();
        if (pending.isEmpty()) {
            throw new NoSuchElementException();
        }
        return pending.remove();
    }

    // A chunk that hasn't been scanned is at least `scanned` chunks away, so a pending one that close is the nearest.
    private void fill() {
        while (scanned <= radius && (pending.isEmpty() || pending.element().chunk().distanceSquared(center) > (long) scanned * scanned)) {
            scanRing(scanned, Math.min(scanned + RING_WIDTH - 1, radius));
            scanned += RING_WIDTH;
        }
    }

    /// The chunks from `inner` to `outer` chunks away from the center along an axis.
    private void scanRing(int inner, int outer) {
        int x = center.x();
        int z = center.z();
        if (inner == 0) {
            scan(x - outer, z - outer, x + outer, z + outer);
            return;
        }
        scan(x - outer, z - outer, x + outer, z - inner);
        scan(x - outer, z + inner, x + outer, z + outer);
        scan(x - outer, z - inner + 1, x - inner, z + inner - 1);
        scan(x + inner, z - inner + 1, x + outer, z + inner - 1);
    }

    private void scan(int minX, int minZ, int maxX, int maxZ) {
        long maxDistance = (long) radius * radius;
        for (var source : sources) {
            picked(placement.apply(source), minX, minZ, maxX, maxZ, chunk -> {
                if (chunk.distanceSquared(center) <= maxDistance) {
                    pending.add(new Candidate<>(chunk, source));
                }
            });
        }
    }

    private void picked(StructurePlacement placement, int minX, int minZ, int maxX, int maxZ, Consumer<ChunkPos> into) {
        switch (placement) {
            // Each cell of `spacing` chunks has one potential chunk, so one position per cell is enough.
            case RandomSpreadStructurePlacement spread -> {
                int spacing = spread.spacing();
                for (int x = minX; x < maxX + spacing; x += spacing) {
                    for (int z = minZ; z < maxZ + spacing; z += spacing) {
                        var chunk = spread.getPotentialStructureChunk(state.getLevelSeed(), x, z);
                        if (isInside(chunk, minX, minZ, maxX, maxZ) && spread.isStructureChunk(state, chunk.x(), chunk.z())) {
                            into.accept(chunk);
                        }
                    }
                }
            }
            case ConcentricRingsStructurePlacement rings -> Objects.requireNonNullElse(state.getRingPositionsFor(rings), List.<ChunkPos>of()).stream()
                    .filter(chunk -> isInside(chunk, minX, minZ, maxX, maxZ) && rings.isStructureChunk(state, chunk.x(), chunk.z()))
                    .forEach(into);
            default -> {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (placement.isStructureChunk(state, x, z)) {
                            into.accept(new ChunkPos(x, z));
                        }
                    }
                }
            }
        }
    }

    private static boolean isInside(ChunkPos chunk, int minX, int minZ, int maxX, int maxZ) {
        return chunk.x() >= minX && chunk.x() <= maxX && chunk.z() >= minZ && chunk.z() <= maxZ;
    }
}
