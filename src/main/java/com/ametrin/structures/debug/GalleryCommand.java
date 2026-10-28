package com.ametrin.structures.debug;

import com.ametrin.structures.util.ASLog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/// `/ametrin structures gallery <structure> [count]` places layouts of a structure at random seeds in a grid, starting next to the caller,
/// with each layout's lowest block at the caller's height. See [StructureLayouts] for what's ignored. It can't be undone.
final class GalleryCommand {
    private static final int DEFAULT_COUNT = 9;
    private static final int MAX_COUNT = 100;
    private static final int GAP = 8;
    // A seed can lay out no pieces, so a few more are tried.
    private static final int TRIES_PER_LAYOUT = 4;

    private GalleryCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> gallery() {
        return Commands.literal("gallery")
                .then(Commands.argument("structure", ResourceKeyArgument.key(Registries.STRUCTURE))
                        .executes(command -> gallery(command, DEFAULT_COUNT))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                                .executes(command -> gallery(command, IntegerArgumentType.getInteger(command, "count")))));
    }

    private static int gallery(CommandContext<CommandSourceStack> command, int count) throws CommandSyntaxException {
        var source = command.getSource();
        var structure = ResourceKeyArgument.getStructure(command, "structure");
        var name = structure.key().identifier().toString();
        var level = source.getLevel();
        var here = BlockPos.containing(source.getPosition());
        var random = level.getRandom().fork();
        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.gallery.started", count, name), false);
        CompletableFuture.supplyAsync(() -> {
                    var layouts = new ArrayList<PiecesContainer>();
                    for (int i = 0; i < count * TRIES_PER_LAYOUT && layouts.size() < count; i++) {
                        StructureLayouts.layOut(level, structure.value(), ChunkPos.containing(here), random.nextLong()).ifPresent(layouts::add);
                    }
                    return layouts;
                }, Util.backgroundExecutor())
                .thenAcceptAsync(layouts -> {
                    if (layouts.isEmpty()) {
                        source.sendFailure(Component.translatable("commands.ametrin_structures.pieces.none", name, count * TRIES_PER_LAYOUT));
                        return;
                    }
                    int columns = place(level, structure.value(), here.offset(GAP, 0, GAP), layouts);
                    source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.gallery.placed", layouts.size(), name, columns), true);
                }, source.getServer())
                // Covers both laying out and placing, so an exception while placing isn't lost.
                .whenCompleteAsync((_, error) -> {
                    if (error != null) {
                        ASLog.error("gallery failed", error);
                        source.sendFailure(Component.translatable("commands.ametrin_structures.gallery.failed", error.toString()));
                    }
                }, source.getServer());
        return 1;
    }

    /// Places the layouts in rows, the first one's lowest corner at `corner`. Each cell is as big as the biggest layout.
    ///
    /// @return the number of columns
    private static int place(ServerLevel level, Structure structure, BlockPos corner, List<PiecesContainer> layouts) {
        int columns = (int) Math.ceil(Math.sqrt(layouts.size()));
        int cellX = layouts.stream().mapToInt(pieces -> pieces.calculateBoundingBox().getXSpan()).max().orElse(0) + GAP;
        int cellZ = layouts.stream().mapToInt(pieces -> pieces.calculateBoundingBox().getZSpan()).max().orElse(0) + GAP;
        var generator = level.getChunkSource().getGenerator();
        for (int i = 0; i < layouts.size(); i++) {
            var pieces = layouts.get(i);
            var box = pieces.calculateBoundingBox();
            int dx = corner.getX() + i % columns * cellX - box.minX();
            int dy = corner.getY() - box.minY();
            int dz = corner.getZ() + i / columns * cellZ - box.minZ();
            pieces.pieces().forEach(piece -> piece.move(dx, dy, dz));
            var moved = pieces.calculateBoundingBox();
            var start = new StructureStart(structure, ChunkPos.containing(moved.getCenter()), 0, pieces);
            // Like /place structure, one chunk at a time, but loading the chunks it needs.
            var min = new ChunkPos(SectionPos.blockToSectionCoord(moved.minX()), SectionPos.blockToSectionCoord(moved.minZ()));
            var max = new ChunkPos(SectionPos.blockToSectionCoord(moved.maxX()), SectionPos.blockToSectionCoord(moved.maxZ()));
            ChunkPos.rangeClosed(min, max).forEach(chunk -> {
                level.getChunk(chunk.x(), chunk.z());
                var chunkBox = new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY() + 1, chunk.getMaxBlockZ());
                start.placeInChunk(level, level.structureManager(), generator, level.getRandom(), chunkBox, chunk);
            });
        }
        return columns;
    }
}
