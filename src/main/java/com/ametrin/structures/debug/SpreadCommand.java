package com.ametrin.structures.debug;

import com.ametrin.structures.network.ASPayloads;
import com.ametrin.structures.util.ASLog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.commands.arguments.TeamColorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.scores.TeamColor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/// `/ametrin structures spread <structure|#tag> [radius] [color] [rejected]` reports where a structure,
/// or those of a tag, would generate around the caller and why the other candidate chunks fail, without
/// generating anything, see [StructureSpread]. `spread set <structure_set> …` does the same for all of
/// a structure set's structures, and only its placement. With Xaero's Minimap installed the spots also
/// show as waypoints, in `color` (picked from the reported id by default), next to earlier reports';
/// `rejected` adds the failed candidates inside the structures' biomes in gray, `rejected all` every
/// failed candidate, and either replaces all earlier waypoints. `spread clear` removes them.
/// `/ametrin structures visit next|previous|<number>` then teleports a player through the spots of
/// their last report, nearest first.
public final class SpreadCommand {
    private static final int DEFAULT_RADIUS = 128;
    private static final int MAX_RADIUS = 1024;
    private static final String VISIT_NEXT = "/ametrin structures visit next";

    private static final DynamicCommandExceptionType INVALID_STRUCTURE = new DynamicCommandExceptionType(
            id -> Component.translatableEscape("commands.locate.structure.invalid", id));
    private static final DynamicCommandExceptionType INVALID_SET = new DynamicCommandExceptionType(
            id -> Component.translatableEscape("commands.ametrin_structures.spread.invalid_set", id));

    /// Per kind, nearest first, so a map mod is not flooded by a dense structure.
    private static final int MAX_WAYPOINTS = 1000;
    /// Slowest first.
    private static final int MAX_TIMINGS = 5;

    private static final List<TeamColor> WAYPOINT_COLORS = TeamColor.VALUES.stream()
            .filter(color -> color != TeamColor.BLACK && color != TeamColor.GRAY && color != TeamColor.DARK_GRAY && color != TeamColor.WHITE)
            .toList();

    /// Each player's last report, for `visit`.
    private static final Map<UUID, Visits> VISITS = new ConcurrentHashMap<>();

    private SpreadCommand() {}

    private record Visits(ResourceKey<Level> dimension, TeamColor color, List<StructureSpread.Found> found,
                          int index) {
        Visits at(int index) {
            return new Visits(dimension, color, found, index);
        }
    }

    private record Target(Kind kind, Identifier id, Predicate<Holder<StructureSet>> sets,
                          Predicate<Holder<Structure>> structures) {
        enum Kind {
            STRUCTURE,
            TAG,
            SET
        }

        static Target of(Kind kind, Identifier id, Predicate<Holder<Structure>> structures) {
            return new Target(kind, id, set -> set.value().structures().stream().anyMatch(entry -> structures.test(entry.structure())), structures);
        }

        Component name(TeamColor color) {
            return Component.literal(kind == Kind.TAG ? "#" + id : id.toString()).withColor(color.textColor());
        }
    }

    @FunctionalInterface
    private interface TargetArgument {
        Target read(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    static LiteralArgumentBuilder<CommandSourceStack> spread() {
        return Commands.literal("spread")
                .then(Commands.literal("clear").executes(context -> clear(context.getSource())))
                .then(Commands.literal("set")
                        .then(withOptions(Commands.argument("set", ResourceKeyArgument.key(Registries.STRUCTURE_SET)), SpreadCommand::set)))
                .then(withOptions(Commands.argument("structure", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.STRUCTURE)), SpreadCommand::structures));
    }

    static LiteralArgumentBuilder<CommandSourceStack> visit() {
        return Commands.literal("visit")
                .then(Commands.literal("next").executes(context -> visit(context.getSource(), index -> index + 1)))
                .then(Commands.literal("previous").executes(context -> visit(context.getSource(), index -> index - 1)))
                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int number = IntegerArgumentType.getInteger(context, "number");
                            return visit(context.getSource(), index -> number - 1);
                        }));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withOptions(T node, TargetArgument target) {
        return node.executes(context -> spread(context, target.read(context), DEFAULT_RADIUS, null, Rejected.NONE))
                .then(withRejected(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS)), target, _ -> null)
                        .then(withRejected(Commands.argument("color", TeamColorArgument.teamColor()), target, SpreadCommand::color)));
    }


    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withRejected(
            T node, TargetArgument target, Function<CommandContext<CommandSourceStack>, @Nullable TeamColor> color) {
        return node.executes(context -> spread(context, target.read(context), radius(context), color.apply(context), Rejected.NONE))
                .then(Commands.literal("rejected")
                        .executes(context -> spread(context, target.read(context), radius(context), color.apply(context), Rejected.IN_BIOME))
                        .then(Commands.literal("all")
                                .executes(context -> spread(context, target.read(context), radius(context), color.apply(context), Rejected.ALL))));
    }

    private static Target structures(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var argument = ResourceOrTagKeyArgument.getResourceOrTagKey(context, "structure", Registries.STRUCTURE, INVALID_STRUCTURE);
        var registry = context.getSource().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var key = argument.unwrap().left();
        if (key.isPresent()) {
            registry.get(key.get()).orElseThrow(() -> INVALID_STRUCTURE.create(argument.asPrintable()));
            return Target.of(Target.Kind.STRUCTURE, key.get().identifier(), argument);
        }
        var tag = argument.unwrap().right().orElseThrow();
        registry.get(tag).orElseThrow(() -> INVALID_STRUCTURE.create(argument.asPrintable()));
        return Target.of(Target.Kind.TAG, tag.location(), argument);
    }

    private static Target set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var key = ResourceKeyArgument.getRegistryKey(context, "set", Registries.STRUCTURE_SET, INVALID_SET);
        context.getSource().registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(key)
                .orElseThrow(() -> INVALID_SET.create(key.identifier()));
        return new Target(Target.Kind.SET, key.identifier(), set -> set.is(key), _ -> true);
    }

    public static void forget(PlayerEvent.PlayerLoggedOutEvent event) {
        VISITS.remove(event.getEntity().getUUID());
    }

    private static int radius(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "radius");
    }

    private static TeamColor color(CommandContext<CommandSourceStack> context) {
        return TeamColorArgument.getTeamColor(context, "color");
    }

    enum Rejected {
        NONE,
        /// Only those whose spot is in a looked-for structure's biomes: the rest fail wherever those could never be.
        IN_BIOME,
        ALL;

        boolean accepts(StructureSpread.RejectedSpot spot) {
            return this == ALL || this == IN_BIOME && spot.inBiome();
        }
    }

    private static int spread(CommandContext<CommandSourceStack> context, Target target, int radius, @Nullable TeamColor color, Rejected rejected) {
        var source = context.getSource();
        var waypointColor = color != null ? color : defaultColor(target.id());
        var name = target.name(waypointColor);
        var level = source.getLevel();
        var center = ChunkPos.containing(BlockPos.containing(source.getPosition()));
        var player = source.getPlayer();

        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.spread.started", name, radius), false);
        CompletableFuture.supplyAsync(() -> StructureSpread.analyze(level, target.sets(), target.structures(), center, radius, rejected != Rejected.NONE), Util.backgroundExecutor())
                .whenCompleteAsync((report, error) -> {
                    if (error != null) {
                        ASLog.error("structure spread report failed", error);
                        source.sendFailure(Component.translatable("commands.ametrin_structures.spread.failed", error.toString()));
                        return;
                    }
                    // A player who left meanwhile was already forgotten; storing their report would leak it.
                    if (player != null && !player.hasDisconnected()) {
                        VISITS.put(player.getUUID(), new Visits(level.dimension(), waypointColor, report.found(), -1));
                        sendWaypoints(player, target.id(), level.dimension(), waypointColor, rejected, report);
                    }
                    send(source, target, name, radius, rejected, report);
                }, source.getServer());
        return 1;
    }

    static TeamColor defaultColor(Identifier id) {
        return WAYPOINT_COLORS.get(Math.floorMod(id.hashCode(), WAYPOINT_COLORS.size()));
    }

    private static void sendWaypoints(
            ServerPlayer player, Identifier report, ResourceKey<Level> dimension, TeamColor color, Rejected shownRejected,
            StructureSpread.Report spread) {
        if (!player.connection.hasChannel(ASPayloads.SpreadWaypoints.TYPE)) {
            return;
        }
        var found = spread.found().stream()
                .limit(MAX_WAYPOINTS)
                .map(spot -> new ASPayloads.SpreadWaypoints.FoundSpot(spot.origin(), spot.id()))
                .toList();
        var rejected = spread.rejectedSpots().stream()
                .filter(shownRejected::accepts)
                .limit(MAX_WAYPOINTS)
                .map(spot -> new ASPayloads.SpreadWaypoints.RejectedSpot(spot.position(), spot.reason().key(), spot.reason().argument()))
                .toList();
        PacketDistributor.sendToPlayer(player, new ASPayloads.SpreadWaypoints(
                report, dimension, color, shownRejected != Rejected.NONE, found, rejected));
    }

    private static int clear(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (player.connection.hasChannel(ASPayloads.ClearSpreadWaypoints.TYPE)) {
            PacketDistributor.sendToPlayer(player, ASPayloads.ClearSpreadWaypoints.INSTANCE);
        }
        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.spread.cleared"), false);
        return 1;
    }

    private static void send(CommandSourceStack source, Target target, Component name, int radius, Rejected rejected, StructureSpread.Report report) {
        if (report.structureSetCount() == 0) {
            var reason = target.kind() == Target.Kind.SET ? "commands.ametrin_structures.spread.set_unused" : "commands.ametrin_structures.spread.no_set";
            source.sendFailure(Component.translatable(reason, name));
            return;
        }
        int generated = report.found().size();
        int percent = report.candidateCount() == 0 ? 0 : Math.round(100F * generated / report.candidateCount());
        line(source, Component.translatable("commands.ametrin_structures.spread.summary", name, radius, report.candidateCount(), generated, percent));
        if (target.kind() != Target.Kind.STRUCTURE) {
            report.found().stream()
                    .collect(Collectors.groupingBy(StructureSpread.Found::id, Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<Identifier, Long>comparingByValue().reversed())
                    .forEach(entry -> line(source, Component.translatable(
                            "commands.ametrin_structures.spread.generated", entry.getKey().toString(), entry.getValue())));
        }

        report.rejections().entrySet().stream()
                .sorted(Map.Entry.<StructureSpread.Reason, Integer>comparingByValue().reversed())
                .forEach(entry -> line(source, Component.translatable(
                        "commands.ametrin_structures.spread.rejected", entry.getValue(), reason(entry.getKey()))));
        long shownRejected = report.rejectedSpots().stream().filter(rejected::accepts).count();
        long hiddenRejected = report.rejectedSpots().size() - shownRejected;
        if (hiddenRejected > 0) {
            line(source, Component.translatable("commands.ametrin_structures.spread.outside_biome_hidden", hiddenRejected));
        }
        if (generated > MAX_WAYPOINTS || shownRejected > MAX_WAYPOINTS) {
            line(source, Component.translatable("commands.ametrin_structures.spread.waypoints_limited", MAX_WAYPOINTS));
        }
        sendTimings(source, report.timings());
        if (generated == 0) {
            return;
        }

        var heights = report.startHeights();
        line(source, Component.translatable("commands.ametrin_structures.spread.heights",
                String.format("%.1f", heights.getAverage()), heights.getMin(), heights.getMax()));
        report.meanSpacing().ifPresent(spacing -> line(source, Component.translatable(
                "commands.ametrin_structures.spread.spacing", Math.round(spacing))));

        var nearest = report.found().getFirst().origin();
        var here = BlockPos.containing(source.getPosition());
        line(source, Component.translatable("commands.ametrin_structures.spread.nearest",
                Math.round(StructureSpread.horizontalDistance(nearest, here)), coordinates(nearest)));
        if (source.getPlayer() != null) {
            line(source, Component.translatable("commands.ametrin_structures.spread.visit_hint")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent.RunCommand(VISIT_NEXT))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(VISIT_NEXT)))));
        }
    }

    private static void sendTimings(CommandSourceStack source, List<StructureSpread.Timing> timings) {
        for (var timing : timings.subList(0, Math.min(timings.size(), MAX_TIMINGS))) {
            line(source, Component.translatable("commands.ametrin_structures.spread.timing",
                    timing.id().toString(), duration(timing.nanos()), timing.attempts(), duration((double) timing.nanos() / Math.max(timing.attempts(), 1))));
            if (timing.steps().isEmpty()) {
                continue;
            }
            var steps = timing.steps().entrySet().stream()
                    .map(entry -> Component.translatable("commands.ametrin_structures.spread.timing.step",
                            Component.translatable("commands.ametrin_structures.spread.step." + entry.getKey().key(), entry.getKey().argument()),
                            duration(entry.getValue()),
                            Math.round(100.0 * entry.getValue() / Math.max(timing.nanos(), 1))))
                    .toList();
            line(source, Component.translatable("commands.ametrin_structures.spread.timing.steps", ComponentUtils.formatList(steps, Component.literal(", "))));
        }
        if (timings.size() > MAX_TIMINGS) {
            line(source, Component.translatable("commands.ametrin_structures.spread.timing.limited", MAX_TIMINGS));
        }
    }

    private static String duration(double nanos) {
        if (nanos >= 1e9) {
            return String.format("%.1f s", nanos / 1e9);
        }
        if (nanos >= 1e6) {
            return String.format("%.1f ms", nanos / 1e6);
        }
        return String.format("%.1f µs", nanos / 1e3);
    }

    private static int visit(CommandSourceStack source, IntUnaryOperator step) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var visits = VISITS.get(player.getUUID());
        if (visits == null || visits.found().isEmpty()) {
            source.sendFailure(Component.translatable("commands.ametrin_structures.visit.none"));
            return 0;
        }
        var level = source.getServer().getLevel(visits.dimension());
        if (level == null) {
            source.sendFailure(Component.translatable("commands.ametrin_structures.visit.none"));
            return 0;
        }

        int index = Math.floorMod(step.applyAsInt(visits.index()), visits.found().size());
        VISITS.put(player.getUUID(), visits.at(index));
        var spot = visits.found().get(index);
        var target = spot.visit();
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);

        var box = spot.box();
        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.visit.arrived",
                index + 1, visits.found().size(), Component.literal(spot.id().toString()).withColor(visits.color().textColor()), coordinates(spot.origin()),
                box.getXSpan() + "×" + box.getYSpan() + "×" + box.getZSpan()), false);
        return index + 1;
    }

    private static void line(CommandSourceStack source, Component line) {
        source.sendSuccess(() -> line, false);
    }

    private static Component reason(StructureSpread.Reason reason) {
        return Component.translatable("commands.ametrin_structures.spread.reason." + reason.key(), reason.argument());
    }

    private static MutableComponent coordinates(BlockPos pos) {
        return Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.AQUA);
    }
}
