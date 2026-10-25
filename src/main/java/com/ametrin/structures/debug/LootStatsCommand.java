package com.ametrin.structures.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import java.util.function.IntFunction;

/// Rolls loot as if the caller opened chests, luck included, and reports how often each item comes up.
///
/// - `/ametrin loot_stats table <loot_table> [samples]`: one chest where the caller stands.
/// - `/ametrin loot_stats structure <structure> [layouts] [radius]`: every chest in layouts of the structure near the caller, see [StructureLoot].
public final class LootStatsCommand {
    private static final int DEFAULT_SAMPLES = 10_000;
    private static final int MAX_SAMPLES = 100_000;
    private static final int DEFAULT_LAYOUTS = 100;
    private static final int MAX_LAYOUTS = 1_000;
    private static final int DEFAULT_RADIUS = 128;
    private static final int MAX_RADIUS = 512;

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
                        .then(Commands.argument("structure", ResourceKeyArgument.key(Registries.STRUCTURE))
                                .executes(command -> structure(command, DEFAULT_LAYOUTS, DEFAULT_RADIUS))
                                .then(Commands.argument("layouts", IntegerArgumentType.integer(1, MAX_LAYOUTS))
                                        .executes(command -> structure(command, IntegerArgumentType.getInteger(command, "layouts"), DEFAULT_RADIUS))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                                                .executes(command -> structure(command,
                                                        IntegerArgumentType.getInteger(command, "layouts"), IntegerArgumentType.getInteger(command, "radius")))))));
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
        var player = player(source);
        var params = chestParams(source, BlockPos.containing(source.getPosition()), player);
        var stats = sample(table.value(), params, samples, source.getLevel().getRandom().fork());

        var name = table.unwrapKey().map(key -> key.identifier().toString()).orElse("inline");
        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.loot_stats.summary",
                name, samples, format(luck(player)), percent(stats.empty(), samples)), false);
        sendItems(source, stats, "commands.ametrin_structures.loot_stats.item");
        return stats.items().size();
    }

    private static int structure(CommandContext<CommandSourceStack> command, int count, int radius) throws CommandSyntaxException {
        var source = command.getSource();
        var level = source.getLevel();
        Holder<Structure> structure = ResourceKeyArgument.getStructure(command, "structure");
        var name = structure.unwrapKey().map(key -> key.identifier().toString()).orElse("?");
        var random = level.getRandom().fork();
        var layouts = StructureLoot.layouts(level, structure, ChunkPos.containing(BlockPos.containing(source.getPosition())), radius, count, random);
        if (layouts.isEmpty()) {
            source.sendFailure(Component.translatable("commands.ametrin_structures.loot_stats.no_layouts", name, radius));
            return 0;
        }

        var player = player(source);
        var lootTables = level.getServer().reloadableRegistries();
        var stats = sample(layouts.size(), layout -> {
            var items = new ArrayList<ItemStack>();
            for (var container : layouts.get(layout)) {
                items.addAll(lootTables.getLootTable(container.lootTable()).getRandomItems(chestParams(source, container.pos(), player), random));
            }
            return items;
        });
        var tables = count(layouts.stream().map(layout -> {
            var perTable = new HashMap<ResourceKey<LootTable>, Integer>();
            layout.forEach(container -> perTable.merge(container.lootTable(), 1, Integer::sum));
            return (Map<ResourceKey<LootTable>, Integer>) perTable;
        }).toList());

        int samples = layouts.size();
        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.loot_stats.structure_summary",
                name, samples, radius, format(luck(player)), percent(stats.empty(), samples)), false);
        tables.entrySet().stream()
                .sorted(Map.Entry.<ResourceKey<LootTable>, Counts>comparingByValue(Comparator.comparingLong(Counts::total)).reversed())
                .forEach(entry -> source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.loot_stats.table",
                        entry.getKey().identifier().toString(),
                        format((double) entry.getValue().total() / samples),
                        percent(entry.getValue().samplesWith(), samples)), false));
        sendItems(source, stats, "commands.ametrin_structures.loot_stats.structure_item");
        return stats.items().size();
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

    private static void sendItems(CommandSourceStack source, Stats stats, String key) {
        stats.items().entrySet().stream()
                .sorted(Map.Entry.<Item, Counts>comparingByValue(Comparator.comparingLong(Counts::total)).reversed())
                .forEach(entry -> {
                    var counts = entry.getValue();
                    source.sendSuccess(() -> Component.translatable(key,
                            entry.getKey().getDefaultInstance().getHoverName(),
                            format((double) counts.total() / stats.samples()),
                            percent(counts.samplesWith(), stats.samples()),
                            counts.min(),
                            counts.max()), false);
                });
    }

    private static String percent(long part, int whole) {
        return format(100.0 * part / whole);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
