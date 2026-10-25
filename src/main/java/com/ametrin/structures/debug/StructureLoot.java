package com.ametrin.structures.debug;

import com.ametrin.structures.fixture.FillContainerFixture;
import com.ametrin.structures.fixture.FixtureBlockEntity;
import com.ametrin.structures.fixture.FixtureCondition;
import com.ametrin.structures.fixture.FixtureGeneration;
import com.ametrin.structures.fixture.LootContainerFixture;
import com.ametrin.structures.fixture.WeightedFixture;
import com.ametrin.structures.registry.ASBlocks;
import com.ametrin.structures.structure.StructureSetOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/// The loot containers in real layouts of a structure, without placing it.
///
/// Fixtures are drawn like generation would, with conditions checked at the marker. Processors only run when placing, so containers they would remove still count.
/// Only template pieces are read. Pieces that place their containers in code, like the desert pyramid's, have none.
final class StructureLoot {
    private StructureLoot() {}

    record Container(ResourceKey<LootTable> lootTable, BlockPos pos) {}

    /// Lays out `structure` at up to `count` spots its sets pick within `radius` chunks of `center`, nearest first, and returns each layout's containers.
    /// A spot another structure of the set takes is skipped.
    static List<List<Container>> layouts(ServerLevel level, Holder<Structure> structure, ChunkPos center, int radius, int count, RandomSource random) {
        var state = level.getChunkSource().getGeneratorState();
        record Spot(ChunkPos chunk, StructureSet set) {}
        var spots = new ArrayList<Spot>();
        for (var set : state.possibleStructureSets()) {
            if (set.value().structures().stream().noneMatch(entry -> entry.structure().value() == structure.value())) {
                continue;
            }
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                    if (set.value().placement().isStructureChunk(state, x, z)) {
                        spots.add(new Spot(new ChunkPos(x, z), set.value()));
                    }
                }
            }
        }
        spots.sort(Comparator.comparingInt(spot -> spot.chunk().distanceSquared(center)));

        var layouts = new ArrayList<List<Container>>();
        for (var spot : spots) {
            if (layouts.size() == count) {
                break;
            }
            for (var option : StructureSetOrder.of(spot.set(), state.getLevelSeed(), spot.chunk())) {
                var candidate = option.structure().value();
                var stub = candidate.findValidGenerationPoint(StructureSpread.context(level, state, candidate, spot.chunk()));
                if (stub.isPresent()) {
                    if (candidate == structure.value()) {
                        layouts.add(containers(level, stub.get().getPiecesBuilder().build(), random));
                    }
                    break;
                }
            }
        }
        return layouts;
    }

    static List<Container> containers(ServerLevel level, PiecesContainer pieces, RandomSource random) {
        var containers = new ArrayList<Container>();
        for (var piece : pieces.pieces()) {
            switch (piece) {
                case TemplateStructurePiece template -> collect(level, template.template(), template.placeSettings(), template.templatePosition(), random, containers);
                case PoolElementStructurePiece pooled when pooled.getElement() instanceof SinglePoolElement single -> collect(
                        level,
                        level.getStructureManager().getOrCreate(single.getTemplateLocation()),
                        new StructurePlaceSettings().setRotation(pooled.getRotation()),
                        pooled.getPosition(),
                        random,
                        containers);
                default -> {}
            }
        }
        return containers;
    }

    private static void collect(ServerLevel level, StructureTemplate template, StructurePlaceSettings settings, BlockPos origin, RandomSource random, List<Container> containers) {
        if (template.palettes.isEmpty()) {
            return;
        }
        for (var block : settings.getRandomPalette(template.palettes, origin).blocks()) {
            if (block.nbt() == null || block.state().getBlock() instanceof BrushableBlock) {
                continue;
            }
            var pos = StructureTemplate.calculateRelativePosition(settings, block.pos()).offset(origin);
            var lootTable = block.state().is(ASBlocks.FIXTURE) ? fixtureLootTable(level, block.nbt(), pos, random)
                    : block.nbt().read(RandomizableContainer.LOOT_TABLE_TAG, LootTable.KEY_CODEC);
            lootTable.ifPresent(table -> containers.add(new Container(table, pos)));
        }
    }

    private static Optional<ResourceKey<LootTable>> fixtureLootTable(ServerLevel level, CompoundTag marker, BlockPos pos, RandomSource random) {
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var alternatives = marker.read(FixtureBlockEntity.FIXTURES_KEY, WeightedFixture.LIST_CODEC, ops).orElse(List.of());
        return FixtureGeneration.draw(alternatives, new FixtureCondition.Context(level, pos), random, false).flatMap(fixture -> switch (fixture) {
            case LootContainerFixture container -> Optional.of(container.lootTable());
            case FillContainerFixture container -> Optional.of(container.lootTable());
            default -> Optional.empty();
        });
    }
}
