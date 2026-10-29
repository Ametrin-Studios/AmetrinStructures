package com.ametrin.structures.debug;

import com.ametrin.structures.util.ASLog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import static com.ametrin.structures.debug.ChatReport.*;

/// `/ametrin structures pieces <structure> [layouts]` lays out a structure at random seeds where the caller stands, without placing it,
/// and reports which pieces it uses, how big it gets and which jigsaws stay open. See [StructureLayouts] for what's ignored.
public final class PiecesCommand {
    public static final class Keys {
        public static final String STARTED = "commands.ametrin_structures.pieces.started";
        public static final String FAILED = "commands.ametrin_structures.pieces.failed";
        public static final String NONE = "commands.ametrin_structures.pieces.none";
        public static final String SUMMARY = "commands.ametrin_structures.pieces.summary";
        public static final String COUNT = "commands.ametrin_structures.pieces.count";
        public static final String SIZE = "commands.ametrin_structures.pieces.size";
        public static final String OPEN_JIGSAWS = "commands.ametrin_structures.pieces.open_jigsaws";
        public static final String RANGE = "commands.ametrin_structures.pieces.range";

        private Keys() {}
    }

    private static final int DEFAULT_LAYOUTS = 100;
    private static final int MAX_LAYOUTS = 1_000;

    private PiecesCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> pieces() {
        return Commands.literal("pieces")
                .then(ChatReport.more())
                .then(Commands.argument("structure", ResourceKeyArgument.key(Registries.STRUCTURE))
                        .executes(command -> pieces(command, DEFAULT_LAYOUTS))
                        .then(Commands.argument("layouts", IntegerArgumentType.integer(1, MAX_LAYOUTS))
                                .executes(command -> pieces(command, IntegerArgumentType.getInteger(command, "layouts")))));
    }

    /// @param pieces      how often each piece is used, see [StructureLayouts#name]
    /// @param openJigsaws per target pool, jigsaws nothing attached to. Jigsaws into `minecraft:empty` aren't meant to connect, so they're left out.
    record Layout(Map<String, Integer> pieces, Map<String, Integer> openJigsaws, BoundingBox box) {}

    static Layout describe(StructureTemplateManager templates, PiecesContainer pieces) {
        var names = new HashMap<String, Integer>();
        var jigsaws = new ArrayList<StructureTemplate.JigsawBlockInfo>();
        for (var piece : pieces.pieces()) {
            names.merge(StructureLayouts.name(piece), 1, Integer::sum);
            if (piece instanceof PoolElementStructurePiece pooled) {
                // The random only shuffles the order.
                jigsaws.addAll(pooled.getElement().getShuffledJigsawBlocks(templates, pooled.getPosition(), pooled.getRotation(), RandomSource.create(0)));
            }
        }
        var faces = jigsaws.stream().map(jigsaw -> Face.of(jigsaw.pos(), facing(jigsaw))).collect(Collectors.toSet());
        var open = new HashMap<String, Integer>();
        for (var jigsaw : jigsaws) {
            var facing = facing(jigsaw);
            if (!jigsaw.pool().equals(Pools.EMPTY) && !faces.contains(Face.of(jigsaw.pos().relative(facing), facing.getOpposite()))) {
                open.merge(jigsaw.pool().identifier().toString(), 1, Integer::sum);
            }
        }
        return new Layout(names, open, pieces.calculateBoundingBox());
    }

    private static Direction facing(StructureTemplate.JigsawBlockInfo jigsaw) {
        return JigsawBlock.getFrontFacing(jigsaw.state());
    }

    // Attached jigsaws face each other from neighboring blocks. Terrain matching pieces move up or down after they're
    // attached, so sideways jigsaws only need to line up horizontally.
    private record Face(int x, int y, int z, Direction facing) {
        static Face of(BlockPos pos, Direction facing) {
            return new Face(pos.getX(), facing.getAxis().isHorizontal() ? 0 : pos.getY(), pos.getZ(), facing);
        }
    }

    private static int pieces(CommandContext<CommandSourceStack> command, int count) throws CommandSyntaxException {
        var source = command.getSource();
        var structure = ResourceKeyArgument.getStructure(command, "structure");
        var name = structure.key().identifier().toString();
        var level = source.getLevel();
        var chunk = ChunkPos.containing(BlockPos.containing(source.getPosition()));
        var random = level.getRandom().fork();
        source.sendSuccess(() -> Component.translatable(Keys.STARTED, count, name), false);
        CompletableFuture.supplyAsync(() -> {
                    var layouts = new ArrayList<Layout>();
                    for (int i = 0; i < count; i++) {
                        StructureLayouts.layOut(level, structure.value(), chunk, random.nextLong())
                                .ifPresent(pieces -> layouts.add(describe(level.getStructureTemplateManager(), pieces)));
                    }
                    return layouts;
                }, Util.backgroundExecutor())
                .whenCompleteAsync((layouts, error) -> {
                    if (error != null) {
                        ASLog.error("pieces report failed", error);
                        source.sendFailure(Component.translatable(Keys.FAILED, error.toString()));
                    } else if (layouts.isEmpty()) {
                        source.sendFailure(Component.translatable(Keys.NONE, name, count));
                    } else {
                        report(source, name, count, layouts);
                    }
                }, source.getServer());
        return 1;
    }

    private static void report(CommandSourceStack source, String name, int tries, List<Layout> layouts) {
        int samples = layouts.size();
        var summary = Component.translatable(Keys.SUMMARY, name, samples, percent(tries - samples, tries));
        var extraLines = new ArrayList<Component>();

        var pieceCounts = layouts.stream().mapToInt(layout -> layout.pieces().values().stream().mapToInt(Integer::intValue).sum()).summaryStatistics();
        extraLines.add(Component.translatable(Keys.COUNT, format(pieceCounts.getAverage()), pieceCounts.getMin(), pieceCounts.getMax()));
        var width = size(layouts, BoundingBox::getXSpan);
        var height = size(layouts, BoundingBox::getYSpan);
        var length = size(layouts, BoundingBox::getZSpan);
        extraLines.add(Component.translatable(Keys.SIZE,
                format(width.getAverage()), format(height.getAverage()), format(length.getAverage()), width.getMax(), height.getMax(), length.getMax()));

        var open = sortedByTotal(LootStatsCommand.count(layouts.stream().map(Layout::openJigsaws).toList()));
        var openBreakdown = breakdown("open jigsaws into\tper layout\tshare of layouts", open, samples);
        long openTotal = open.stream().mapToLong(entry -> entry.getValue().total()).sum();
        var openLine = Component.translatable(Keys.OPEN_JIGSAWS, format((double) openTotal / samples));
        extraLines.add(open.isEmpty() ? openLine : openLine.withStyle(style -> style.withUnderlined(true).withHoverEvent(new HoverEvent.ShowText(openBreakdown.hover()))));

        var tsv = new StringBuilder("piece\tper layout\tshare of layouts\tfewest\tmost\n");
        var lines = new ArrayList<Component>();
        for (var entry : sortedByTotal(LootStatsCommand.count(layouts.stream().map(Layout::pieces).toList())).reversed()) {
            var counts = entry.getValue();
            tsv.append(entry.getKey()).append('\t').append(average(counts, samples)).append('\t').append(percent(counts.samplesWith(), samples))
                    .append('\t').append(counts.min()).append('\t').append(counts.max()).append('\n');
            var details = Component.translatable(Keys.RANGE, counts.min(), counts.max()).withStyle(ChatFormatting.GRAY);
            lines.add(line(counts, samples, entry.getKey()).withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(details))));
        }
        tsv.append(openBreakdown.tsv());
        ChatReport.send(source, summary, extraLines, lines, tsv.toString(), "/ametrin structures pieces");
    }

    private static IntSummaryStatistics size(List<Layout> layouts, ToIntFunction<BoundingBox> span) {
        return layouts.stream().mapToInt(layout -> span.applyAsInt(layout.box())).summaryStatistics();
    }
}
