package com.ametrin.structures.debug;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.network.ASPayloads;
import com.ametrin.structures.util.ASLog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
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
/// `spread set <structure_set> placement <placement>` reports with that placement instead of the set's, and
/// `spread placement <placement> [radius] [color]` only reports the chunks a placement picks.
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
    private static final DynamicCommandExceptionType INVALID_PLACEMENT = new DynamicCommandExceptionType(
            error -> Component.translatableEscape("commands.ametrin_structures.spread.invalid_placement", error));
    // The rings are worked out once per world, for the sets' own placements.
    private static final SimpleCommandExceptionType RINGS_PLACEMENT = new SimpleCommandExceptionType(
            Component.translatable("commands.ametrin_structures.spread.rings_placement"));
    // Numbered, so each placement report keeps its own waypoints for comparison.
    private static final AtomicInteger PLACEMENT_REPORTS = new AtomicInteger();

    /// Per kind, nearest first, so a map mod is not flooded by a dense structure.
    private static final int MAX_WAYPOINTS = 1000;
    /// Slowest first.
    private static final int MAX_TIMINGS = 5;

    private static final List<ChatFormatting> WAYPOINT_COLORS = Arrays.stream(ChatFormatting.values())
            .filter(ChatFormatting::isColor)
            .filter(color -> color != ChatFormatting.BLACK && color != ChatFormatting.GRAY && color != ChatFormatting.DARK_GRAY && color != ChatFormatting.WHITE)
            .toList();

    /// Each player's last report, for `visit`.
    private static final Map<UUID, Visits> VISITS = new ConcurrentHashMap<>();

    private SpreadCommand() {}

    private record Visits(ResourceKey<Level> dimension, ChatFormatting color, List<Stop> stops, int index) {
        Visits at(int index) {
            return new Visits(dimension, color, stops, index);
        }
    }

    /// @param box empty for a chunk a placement picked
    private record Stop(Identifier id, BlockPos origin, BlockPos target, Optional<BoundingBox> box) {
        static Stop of(StructureSpread.Found spot) {
            return new Stop(spot.id(), spot.origin(), spot.visit(), Optional.of(spot.box()));
        }
    }

    private record Target(Kind kind, Identifier id, Predicate<Holder<StructureSet>> sets,
                          Predicate<Holder<Structure>> structures, @Nullable StructurePlacement placement) {
        enum Kind {
            STRUCTURE,
            TAG,
            SET
        }

        static Target of(Kind kind, Identifier id, Predicate<Holder<Structure>> structures) {
            return new Target(kind, id, set -> set.value().structures().stream().anyMatch(entry -> structures.test(entry.structure())), structures, null);
        }

        Component name(ChatFormatting color) {
            return Component.literal(kind == Kind.TAG ? "#" + id : id.toString()).withStyle(color);
        }
    }

    @FunctionalInterface
    private interface TargetArgument {
        Target read(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    static LiteralArgumentBuilder<CommandSourceStack> spread() {
        return Commands.literal("spread")
                .then(Commands.literal("clear").executes(context -> clear(context.getSource())))
                .then(Commands.literal("placement")
                        .then(withCandidateOptions(Commands.argument("placement", CompoundTagArgument.compoundTag()))))
                .then(Commands.literal("set")
                        .then(withOptions(Commands.argument("set", ResourceKeyArgument.key(Registries.STRUCTURE_SET)), SpreadCommand::set)
                                .then(Commands.literal("placement")
                                        .then(withOptions(
                                                Commands.argument("placement", CompoundTagArgument.compoundTag()).suggests(SpreadCommand::suggestSetPlacement),
                                                SpreadCommand::setWithPlacement)))))
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
                        .then(withRejected(Commands.argument("color", ColorArgument.color()), target, SpreadCommand::color)));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withCandidateOptions(T node) {
        return node.executes(context -> candidates(context, DEFAULT_RADIUS, null))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                        .executes(context -> candidates(context, radius(context), null))
                        .then(Commands.argument("color", ColorArgument.color())
                                .executes(context -> candidates(context, radius(context), color(context)))));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withRejected(
            T node, TargetArgument target, Function<CommandContext<CommandSourceStack>, @Nullable ChatFormatting> color) {
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
        return new Target(Target.Kind.SET, key.identifier(), set -> set.is(key), _ -> true, null);
    }

    private static Target setWithPlacement(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var target = set(context);
        return new Target(target.kind(), target.id(), target.sets(), target.structures(), placement(context));
    }

    private static CompletableFuture<Suggestions> suggestSetPlacement(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        var registries = context.getSource().registryAccess();
        try {
            var key = ResourceKeyArgument.getRegistryKey(context, "set", Registries.STRUCTURE_SET, INVALID_SET);
            registries.lookupOrThrow(Registries.STRUCTURE_SET).get(key)
                    .map(set -> set.value().placement())
                    .filter(placement -> !(placement instanceof ConcentricRingsStructurePlacement))
                    .flatMap(placement -> StructurePlacement.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), placement).result())
                    .map(Tag::toString)
                    .filter(snbt -> snbt.startsWith(builder.getRemaining()))
                    .ifPresent(builder::suggest);
        } catch (CommandSyntaxException ignored) {
            // No set to start from.
        }
        return builder.buildFuture();
    }

    private static StructurePlacement placement(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var ops = context.getSource().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var placement = StructurePlacement.CODEC.parse(ops, CompoundTagArgument.getCompoundTag(context, "placement"))
                .getOrThrow(INVALID_PLACEMENT::create);
        if (placement instanceof ConcentricRingsStructurePlacement) {
            throw RINGS_PLACEMENT.create();
        }
        return placement;
    }

    public static void forget(PlayerEvent.PlayerLoggedOutEvent event) {
        VISITS.remove(event.getEntity().getUUID());
    }

    private static int radius(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "radius");
    }

    private static ChatFormatting color(CommandContext<CommandSourceStack> context) {
        return ColorArgument.getColor(context, "color");
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

    private static int spread(CommandContext<CommandSourceStack> context, Target target, int radius, @Nullable ChatFormatting color, Rejected rejected) {
        var source = context.getSource();
        var waypointColor = color != null ? color : defaultColor(target.id());
        var name = target.name(waypointColor);
        var level = source.getLevel();
        var center = ChunkPos.containing(BlockPos.containing(source.getPosition()));
        var player = source.getPlayer();

        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.spread.started", name, radius), false);
        CompletableFuture.supplyAsync(() -> StructureSpread.analyze(
                        level, target.sets(), target.structures(), center, radius, rejected != Rejected.NONE, target.placement()), Util.backgroundExecutor())
                .whenCompleteAsync((report, error) -> {
                    if (error != null) {
                        ASLog.error("structure spread report failed", error);
                        source.sendFailure(Component.translatable("commands.ametrin_structures.spread.failed", error.toString()));
                        return;
                    }
                    // A player who left meanwhile was already forgotten; storing their report would leak it.
                    if (player != null && !player.hasDisconnected()) {
                        VISITS.put(player.getUUID(), new Visits(level.dimension(), waypointColor, report.found().stream().map(Stop::of).toList(), -1));
                        sendWaypoints(player, target.id(), level.dimension(), waypointColor, rejected, report);
                    }
                    send(source, target, name, radius, rejected, report);
                }, source.getServer());
        return 1;
    }

    private static int candidates(CommandContext<CommandSourceStack> context, int radius, @Nullable ChatFormatting color) throws CommandSyntaxException {
        var placement = placement(context);
        var source = context.getSource();
        int number = PLACEMENT_REPORTS.incrementAndGet();
        var report = Identifier.fromNamespaceAndPath(AmetrinStructures.MOD_ID, "placement/" + number);
        var waypointColor = color != null ? color : WAYPOINT_COLORS.get(ThreadLocalRandom.current().nextInt(WAYPOINT_COLORS.size()));
        var name = Component.translatable("commands.ametrin_structures.spread.placement", number).withStyle(waypointColor);
        var level = source.getLevel();
        var center = ChunkPos.containing(BlockPos.containing(source.getPosition()));
        var player = source.getPlayer();

        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.spread.placement_started", name, radius), false);
        CompletableFuture.supplyAsync(() -> StructureSpread.candidates(level, placement, center, radius), Util.backgroundExecutor())
                .whenCompleteAsync((candidates, error) -> {
                    if (error != null) {
                        ASLog.error("placement spread report failed", error);
                        source.sendFailure(Component.translatable("commands.ametrin_structures.spread.failed", error.toString()));
                        return;
                    }
                    var stops = candidates.spots().stream().map(spot -> new Stop(report, spot, spot, Optional.<BoundingBox>empty())).toList();
                    if (player != null && !player.hasDisconnected()) {
                        VISITS.put(player.getUUID(), new Visits(level.dimension(), waypointColor, stops, -1));
                        sendWaypoints(player, report, level.dimension(), waypointColor, Rejected.NONE, stops, List.of());
                    }
                    line(source, Component.translatable("commands.ametrin_structures.spread.candidates", name, candidates.spots().size(), radius));
                    sendSpacing(source, candidates.spacing());
                    if (!candidates.spots().isEmpty()) {
                        sendNearest(source, candidates.spots().getFirst());
                    }
                }, source.getServer());
        return 1;
    }

    static ChatFormatting defaultColor(Identifier id) {
        return WAYPOINT_COLORS.get(Math.floorMod(id.hashCode(), WAYPOINT_COLORS.size()));
    }

    private static void sendWaypoints(
            ServerPlayer player, Identifier report, ResourceKey<Level> dimension, ChatFormatting color, Rejected shownRejected,
            StructureSpread.Report spread) {
        sendWaypoints(player, report, dimension, color, shownRejected, spread.found().stream().map(Stop::of).toList(), spread.rejectedSpots());
    }

    private static void sendWaypoints(
            ServerPlayer player, Identifier report, ResourceKey<Level> dimension, ChatFormatting color, Rejected shownRejected,
            List<Stop> stops, List<StructureSpread.RejectedSpot> rejectedSpots) {
        if (!player.connection.hasChannel(ASPayloads.SpreadWaypoints.TYPE)) {
            return;
        }
        var found = stops.stream()
                .limit(MAX_WAYPOINTS)
                .map(stop -> new ASPayloads.SpreadWaypoints.FoundSpot(stop.origin(), stop.id()))
                .toList();
        var rejected = rejectedSpots.stream()
                .filter(shownRejected::accepts)
                .limit(MAX_WAYPOINTS)
                .map(spot -> new ASPayloads.SpreadWaypoints.RejectedSpot(spot.position(), spot.reason().key(), spot.reason().argument()))
                .toList();
        PacketDistributor.sendToPlayer(player, new ASPayloads.SpreadWaypoints(
                report, dimension, color.getId(), shownRejected != Rejected.NONE, found, rejected));
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
        sendSpacing(source, report.spacing());
        sendNearest(source, report.found().getFirst().origin());
    }

    private static void sendSpacing(CommandSourceStack source, Optional<DoubleSummaryStatistics> spacing) {
        spacing.ifPresent(stats -> line(source, Component.translatable(
                "commands.ametrin_structures.spread.spacing", Math.round(stats.getAverage()), Math.round(stats.getMin()))));
    }

    private static void sendNearest(CommandSourceStack source, BlockPos nearest) {
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
        if (visits == null || visits.stops().isEmpty()) {
            source.sendFailure(Component.translatable("commands.ametrin_structures.visit.none"));
            return 0;
        }
        var level = source.getServer().getLevel(visits.dimension());
        if (level == null) {
            source.sendFailure(Component.translatable("commands.ametrin_structures.visit.none"));
            return 0;
        }

        int count = visits.stops().size();
        int index = Math.floorMod(step.applyAsInt(visits.index()), count);
        VISITS.put(player.getUUID(), visits.at(index));
        var stop = visits.stops().get(index);
        var target = stop.target();
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);

        source.sendSuccess(() -> stop.box()
                .map(box -> Component.translatable("commands.ametrin_structures.visit.arrived",
                        index + 1, count, Component.literal(stop.id().toString()).withStyle(visits.color()), coordinates(stop.origin()),
                        box.getXSpan() + "×" + box.getYSpan() + "×" + box.getZSpan()))
                .orElseGet(() -> Component.translatable("commands.ametrin_structures.visit.arrived_candidate", index + 1, count, coordinates(stop.origin()))), false);
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
