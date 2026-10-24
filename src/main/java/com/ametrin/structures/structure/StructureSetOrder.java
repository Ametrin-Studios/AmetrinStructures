package com.ametrin.structures.structure;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.List;

public final class StructureSetOrder {
    private StructureSetOrder() {}

    /// The order vanilla tries the set's structures in at `chunk`, until one fits.
    public static List<StructureSet.StructureSelectionEntry> of(StructureSet set, long levelSeed, ChunkPos chunk) {
        // Keep in sync with ChunkGenerator#createStructures.
        var options = new ArrayList<>(set.structures());
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(levelSeed, chunk.x(), chunk.z());
        int total = options.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();

        var order = new ArrayList<StructureSet.StructureSelectionEntry>(options.size());
        while (!options.isEmpty()) {
            int choice = random.nextInt(total);
            int index = 0;
            for (var option : options) {
                choice -= option.weight();
                if (choice < 0) {
                    break;
                }
                index++;
            }
            var selected = options.remove(index);
            total -= selected.weight();
            order.add(selected);
        }
        return order;
    }
}
