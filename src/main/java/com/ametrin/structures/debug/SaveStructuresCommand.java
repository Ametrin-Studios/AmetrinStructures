package com.ametrin.structures.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;

import java.util.ArrayList;
import java.util.Comparator;

/// `/ametrin structures save [radius]` saves every structure block in save mode within `radius` blocks of the caller
public final class SaveStructuresCommand {
    private static final int DEFAULT_RADIUS = 64;
    private static final int MAX_RADIUS = 512;

    private SaveStructuresCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> saveStructures() {
        return Commands.literal("save")
                .executes(context -> save(context.getSource(), DEFAULT_RADIUS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                        .executes(context -> save(context.getSource(), IntegerArgumentType.getInteger(context, "radius"))));
    }

    private static int save(CommandSourceStack source, int radius) {
        var level = source.getLevel();
        var center = BlockPos.containing(source.getPosition());
        var structureBlocks = new ArrayList<StructureBlockEntity>();
        // Only loaded chunks: a structure block in an unloaded one has nothing loaded to save either.
        ChunkPos.rangeClosed(ChunkPos.containing(center.offset(-radius, 0, -radius)), ChunkPos.containing(center.offset(radius, 0, radius)))
                .filter(chunk -> level.hasChunk(chunk.x(), chunk.z()))
                .flatMap(chunk -> level.getChunk(chunk.x(), chunk.z()).getBlockEntities().values().stream())
                .forEach(blockEntity -> {
                    if (blockEntity instanceof StructureBlockEntity structureBlock
                            && structureBlock.getMode() == StructureMode.SAVE
                            && structureBlock.getBlockPos().closerThan(center, radius)) {
                        structureBlocks.add(structureBlock);
                    }
                });
        structureBlocks.sort(Comparator.comparing(StructureBlockEntity::getStructureName));

        var failed = new ArrayList<String>();
        for (var structureBlock : structureBlocks) {
            if (!structureBlock.saveStructure()) {
                failed.add(structureBlock.hasStructureName() ? structureBlock.getStructureName() : structureBlock.getBlockPos().toShortString());
            }
        }
        int saved = structureBlocks.size() - failed.size();
        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.save_structures.saved", saved, radius), true);
        if (!failed.isEmpty()) {
            source.sendFailure(Component.translatable("commands.ametrin_structures.save_structures.failed", String.join(", ", failed)));
        }
        return saved;
    }
}
