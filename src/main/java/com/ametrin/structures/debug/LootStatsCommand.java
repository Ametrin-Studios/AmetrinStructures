package com.ametrin.structures.debug;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.util.ASLog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/// Rolls loot as if the caller opened chests, luck included, and reports how often each item comes up.
///
/// - `/ametrin loot_stats table <loot_table> [samples]`: one chest where the caller stands.
/// - `/ametrin loot_stats structure <structure> [radius] [layouts]`: every chest in the nearest layouts of the structure, see [StructureLoot].
///   For a structure tag, the layouts of all its structures together.
public final class LootStatsCommand {
    private static final int DEFAULT_SAMPLES = 10_000;
    private static final int MAX_SAMPLES = 100_000;
    private static final int DEFAULT_LAYOUTS = 100;
    private static final int MAX_LAYOUTS = 1_000;
    private static final int DEFAULT_RADIUS = 128;
    private static final int MAX_RADIUS = 16_384;
    private static final int SHOWN_ITEMS = 10;
    private static final int KEPT_REPORTS = 16;
    private static final Identifier INLINE_TABLE = AmetrinStructures.locate("inline");

    // The item lines `[+N more]` prints, for the latest few reports.
    private static final Map<Integer, List<Component>> HIDDEN_LINES = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<Component>> eldest) {
            return size() > KEPT_REPORTS;
        }
    };
    private static int nextReport;

    private LootStatsCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> lootStats(CommandBuildContext context) {
        return Commands.literal("loot_stats")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("table")
                        .then(Commands.argument("loot_table", ResourceOrIdArgument.lootTable(context))
                                .executes(command -> table(command, DEFAULT_SAMPLES))
                                .then(Commands.argument("samples", IntegerArgumentType.integer(1, MAX_SAMPLES))
                                        .executes(command -> table(command, IntegerArgumentType.getInteger(command, "samples"))))))
                .then(Commands.literal("structure")
                        .then(Commands.argument("structure", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.STRUCTURE))
                                .executes(command -> structure(command, DEFAULT_RADIUS, DEFAULT_LAYOUTS))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                                        .executes(command -> structure(command, IntegerArgumentType.getInteger(command, "radius"), DEFAULT_LAYOUTS))
                                        .then(Commands.argument("layouts", IntegerArgumentType.integer(1, MAX_LAYOUTS))
                                                .executes(command -> structure(command,
                                                        IntegerArgumentType.getInteger(command, "radius"), IntegerArgumentType.getInteger(command, "layouts")))))))
                .then(Commands.literal("more")
                        .then(Commands.argument("report", IntegerArgumentType.integer(0))
                                .executes(command -> more(command.getSource(), IntegerArgumentType.getInteger(command, "report")))));
    }

    /// @param total       over all samples
    /// @param samplesWith samples that had at least one
    /// @param min         fewest in one sample, 0 if any sample had none
    /// @param max         most in one sample
    record Counts(long total, int samplesWith, int min, int max) {}

    /// @param empty samples without any item
    record Stats(int samples, int empty, Map<Item, Counts> items) {}

    /// Rolls `table` `samples` times. Uses `random` instead of the table's random sequence, which would advance the world's.
    static Stats sample(LootTable table, LootParams params, int samples, RandomSource random) {
        return sample(samples, _ -> table.getRandomItems(params, random));
    }

    /// @param roll the items of one sample
    static Stats sample(int samples, IntFunction<List<ItemStack>> roll) {
        var perSample = new ArrayList<Map<Item, Integer>>(samples);
        for (int sample = 0; sample < samples; sample++) {
            var counts = new HashMap<Item, Integer>();
            for (var stack : roll.apply(sample)) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
            perSample.add(counts);
        }
        int empty = (int) perSample.stream().filter(Map::isEmpty).count();
        return new Stats(samples, empty, count(perSample));
    }

    /// Sums each key's counts over `samples`, where a sample without the key counts as 0.
    static <K> Map<K, Counts> count(List<Map<K, Integer>> samples) {
        var tallies = new HashMap<K, Tally>();
        samples.forEach(sample -> sample.forEach((key, count) -> tallies.computeIfAbsent(key, _ -> new Tally()).add(count)));
        var counts = new HashMap<K, Counts>();
        tallies.forEach((key, tally) -> counts.put(key, new Counts(tally.total, tally.samplesWith, tally.samplesWith < samples.size() ? 0 : tally.min, tally.max)));
        return counts;
    }

    private static final class Tally {
        long total;
        int samplesWith;
        int min = Integer.MAX_VALUE;
        int max;

        void add(int count) {
            total += count;
            samplesWith++;
            min = Math.min(min, count);
            max = Math.max(max, count);
        }
    }

    private static int table(CommandContext<CommandSourceStack> command, int samples) throws CommandSyntaxException {
        var source = command.getSource();
        var table = ResourceOrIdArgument.getLootTable(command, "loot_table");
        if (table.unwrapKey().isEmpty()) {
            // NeoForge's loot modifiers need an id, which an inline table doesn't have.
            table.value().setLootTableId(INLINE_TABLE);
        }
        var player = player(source);
        var params = chestParams(source, BlockPos.containing(source.getPosition()), player);
        var stats = sample(table.value(), params, samples, source.getLevel().getRandom().fork());

        var name = table.unwrapKey().map(key -> key.identifier().toString()).orElse("inline");
        var summary = Component.translatable("commands.ametrin_structures.loot_stats.summary", name, samples, format(luck(player)), percent(stats.empty(), samples));
        sendReport(source, summary, List.of(), stats, "chest", "");
        return stats.items().size();
    }

    private static int structure(CommandContext<CommandSourceStack> command, int radius, int count) throws CommandSyntaxException {
        var source = command.getSource();
        var level = source.getLevel();
        var structures = ResourceOrTagKeyArgument.getResourceOrTagKey(command, "structure", Registries.STRUCTURE, SpreadCommand.INVALID_STRUCTURE);
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        boolean exists = structures.unwrap().map(key -> registry.get(key).isPresent(), tag -> registry.get(tag).isPresent());
        if (!exists) {
            throw SpreadCommand.INVALID_STRUCTURE.create(structures.asPrintable());
        }
        var name = structures.asPrintable();
        var random = level.getRandom().fork();
        var center = ChunkPos.containing(BlockPos.containing(source.getPosition()));
        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.loot_stats.structure_started", count, name, radius), false);
        // Laying out structures far away can take a while.
        CompletableFuture.supplyAsync(() -> StructureLoot.layouts(level, structures, center, radius, count, random), Util.backgroundExecutor())
                .whenCompleteAsync((layouts, error) -> {
                    if (error != null) {
                        ASLog.error("structure loot stats failed", error);
                        source.sendFailure(Component.translatable("commands.ametrin_structures.loot_stats.failed", error.toString()));
                    } else if (layouts.layouts().isEmpty()) {
                        source.sendFailure(Component.translatable("commands.ametrin_structures.loot_stats.no_layouts", name, radius));
                    } else {
                        structureReport(source, structures, name, layouts, random);
                    }
                }, source.getServer());
        return 1;
    }

    private static void structureReport(CommandSourceStack source, ResourceOrTagKeyArgument.Result<Structure> structures, String name, StructureLoot.Layouts found, RandomSource random) {
        var level = source.getLevel();
        var layouts = found.layouts();
        var player = player(source);
        var lootTables = level.getServer().reloadableRegistries();
        var stats = sample(layouts.size(), layout -> {
            var contents = layouts.get(layout).contents();
            var items = new ArrayList<ItemStack>();
            for (var container : contents.containers()) {
                items.addAll(lootTables.getLootTable(container.lootTable()).getRandomItems(chestParams(source, container.pos(), player), random));
            }
            for (var display : contents.displays()) {
                items.addAll(display.drops(level, random));
            }
            return items;
        });
        // A display without an equipment table is listed by its entity type.
        var sources = count(layouts.stream().map(layout -> {
            var perSource = new HashMap<String, Integer>();
            layout.contents().containers().forEach(container -> perSource.merge(container.lootTable().identifier().toString(), 1, Integer::sum));
            layout.contents().displays().forEach(display -> perSource.merge(
                    display.equipment().map(table -> table.identifier().toString()).orElseGet(() -> BuiltInRegistries.ENTITY_TYPE.getKey(display.type()).toString()),
                    1, Integer::sum));
            return (Map<String, Integer>) perSource;
        }).toList());

        int samples = layouts.size();
        var summary = Component.translatable("commands.ametrin_structures.loot_stats.structure_summary",
                name, samples, found.radius(), format(luck(player)), percent(stats.empty(), samples));
        var extraLines = new ArrayList<Component>();
        if (structures.unwrap().right().isPresent()) {
            var mix = layouts.stream()
                    .collect(Collectors.groupingBy(layout -> layout.structure().unwrapKey().map(key -> key.identifier().toString()).orElse("?"), Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(entry -> entry.getKey() + " " + entry.getValue())
                    .collect(Collectors.joining(", "));
            extraLines.add(Component.translatable("commands.ametrin_structures.loot_stats.structure_mix", mix));
        }
        var sortedSources = sortedByTotal(sources);
        var sourceList = Component.empty();
        var sourceRows = new StringBuilder("\nloot source\tper structure\tshare of structures\n");
        for (var entry : sortedSources) {
            var id = entry.getKey();
            var counts = entry.getValue();
            if (!sourceList.getSiblings().isEmpty()) {
                sourceList.append("\n");
            }
            sourceList.append(Component.translatable("commands.ametrin_structures.loot_stats.line", average(counts, samples), id, percent(counts.samplesWith(), samples)));
            sourceRows.append(id).append('\t').append(average(counts, samples)).append('\t').append(percent(counts.samplesWith(), samples)).append('\n');
        }
        extraLines.add(Component.translatable("commands.ametrin_structures.loot_stats.sources", sortedSources.size())
                .withStyle(style -> style.withUnderlined(true).withHoverEvent(new HoverEvent.ShowText(sourceList))));
        double codePieces = layouts.stream().mapToInt(layout -> layout.contents().codePieces()).average().orElse(0);
        if (codePieces > 0) {
            extraLines.add(Component.translatable("commands.ametrin_structures.loot_stats.code_pieces", format(codePieces)));
        }
        double dataMarkers = layouts.stream().mapToInt(layout -> layout.contents().dataMarkers()).average().orElse(0);
        if (dataMarkers > 0) {
            extraLines.add(Component.translatable("commands.ametrin_structures.loot_stats.data_markers", format(dataMarkers)));
        }
        sendReport(source, summary, extraLines, stats, "structure", sourceRows.toString());
    }

    private static @Nullable Player player(CommandSourceStack source) {
        return source.getEntity() instanceof Player player ? player : null;
    }

    private static float luck(@Nullable Player player) {
        return player != null ? player.getLuck() : 0;
    }

    // Like RandomizableContainer#unpackLootTable when a player opens the chest.
    private static LootParams chestParams(CommandSourceStack source, BlockPos chest, @Nullable Player player) {
        var params = new LootParams.Builder(source.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(chest));
        if (player != null) {
            params.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
        }
        return params.create(LootContextParamSets.CHEST);
    }

    /// Sends the summary with a copy button, then the rarest items. The rest are kept for `[+N more]`.
    ///
    /// @param per         what one sample is, "chest" or "structure"
    /// @param extraTsv    appended to the copied items, e.g. the loot sources
    private static void sendReport(CommandSourceStack source, MutableComponent summary, List<Component> extraLines, Stats stats, String per, String extraTsv) {
        var items = sortedByTotal(stats.items()).reversed();
        var tsv = new StringBuilder("item\tper " + per + "\tshare of " + per + "s\tfewest\tmost\n");
        var lines = new ArrayList<Component>();
        for (var entry : items) {
            var id = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
            var counts = entry.getValue();
            tsv.append(id).append('\t').append(average(counts, stats.samples())).append('\t').append(percent(counts.samplesWith(), stats.samples()))
                    .append('\t').append(counts.min()).append('\t').append(counts.max()).append('\n');
            var name = entry.getKey().getDefaultInstance().getStyledHoverName();
            var details = Component.empty().append(name)
                    .append(Component.literal("\n" + id).withStyle(ChatFormatting.DARK_GRAY))
                    .append("\n").append(Component.translatable("commands.ametrin_structures.loot_stats.range_" + per, counts.min(), counts.max()));
            lines.add(Component.translatable("commands.ametrin_structures.loot_stats.line", average(counts, stats.samples()), name, percent(counts.samplesWith(), stats.samples()))
                    .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(details))));
        }
        tsv.append(extraTsv);

        var copy = Component.translatable("commands.ametrin_structures.loot_stats.copy").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.CopyToClipboard(tsv.toString()))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("commands.ametrin_structures.loot_stats.copy_hover"))));
        source.sendSuccess(() -> summary.append(" ").append(copy), false);
        extraLines.forEach(line -> source.sendSuccess(() -> line, false));
        lines.stream().limit(SHOWN_ITEMS).forEach(line -> source.sendSuccess(() -> line, false));
        if (lines.size() > SHOWN_ITEMS) {
            int report = nextReport++;
            HIDDEN_LINES.put(report, List.copyOf(lines.subList(SHOWN_ITEMS, lines.size())));
            source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.loot_stats.more", lines.size() - SHOWN_ITEMS).withStyle(style -> style
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.RunCommand("/ametrin loot_stats more " + report))), false);
        }
    }

    private static int more(CommandSourceStack source, int report) {
        var lines = HIDDEN_LINES.remove(report);
        if (lines == null) {
            source.sendFailure(Component.translatable("commands.ametrin_structures.loot_stats.gone"));
            return 0;
        }
        lines.forEach(line -> source.sendSuccess(() -> line, false));
        return lines.size();
    }

    private static <K> List<Map.Entry<K, Counts>> sortedByTotal(Map<K, Counts> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<K, Counts>comparingByValue(Comparator.comparingLong(Counts::total)).reversed())
                .toList();
    }

    private static String average(Counts counts, int samples) {
        return format((double) counts.total() / samples);
    }

    private static String percent(long part, int whole) {
        return format(100.0 * part / whole);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
