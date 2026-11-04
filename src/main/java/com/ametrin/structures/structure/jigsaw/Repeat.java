package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.filter.PlacementFilter;
import net.minecraft.core.Direction;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

import java.util.ArrayList;
import java.util.List;

/// The row of copies a [JigsawPools.Pool#repeated] element places, see [RepeatedPoolElement].
public final class Repeat {
    private final Direction direction;
    private final IntProvider copies;
    private int minCopies = 1;
    private final List<PlacementFilter> filters = new ArrayList<>();

    private Repeat(Direction direction, IntProvider copies) {
        this.direction = direction;
        this.copies = copies;
    }

    /// Up to `copies` copies in `direction`, as many as fit.
    public static Repeat of(Direction direction, int copies) {
        return of(direction, ConstantInt.of(copies));
    }

    /// Up to a random number of copies in `direction`, as many as fit.
    public static Repeat of(Direction direction, IntProvider copies) {
        return new Repeat(direction, copies);
    }

    /// Where fewer copies than `min` fit, the element doesn't fit. Defaults to 1.
    public Repeat minCopies(int min) {
        this.minCopies = min;
        return this;
    }

    /// Only places copies that pass `filter`.
    public Repeat filter(PlacementFilter filter) {
        this.filters.add(filter);
        return this;
    }

    RepeatedPoolElement wrap(StructurePoolElement element) {
        return new RepeatedPoolElement(element, direction, copies, minCopies, filters);
    }
}
