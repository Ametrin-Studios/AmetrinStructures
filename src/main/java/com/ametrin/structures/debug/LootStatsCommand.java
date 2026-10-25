package com.ametrin.structures.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/// `/ametrin loot_stats <loot_table> [samples]` rolls a loot table as if the caller opened a chest where they stand, luck included,
/// and reports how often each item comes up.
public final class LootStatsCommand {
    private static final int DEFAULT_SAMPLES = 10_000;
    private static final int MAX_SAMPLES = 100_000;

    private LootStatsCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> lootStats(CommandBuildContext context) {
        return Commands.literal("loot_stats")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("loot_table", ResourceOrIdArgument.lootTable(context))
                        .executes(command -> report(command.getSource(), ResourceOrIdArgument.getLootTable(command, "loot_table"), DEFAULT_SAMPLES))
                        .then(Commands.argument("samples", IntegerArgumentType.integer(1, MAX_SAMPLES))
                                .executes(command -> report(command.getSource(), ResourceOrIdArgument.getLootTable(command, "loot_table"),
                                        IntegerArgumentType.getInteger(command, "samples")))));
    }

    /// @param total     items over all samples
    /// @param withItem  samples that had at least one
    /// @param min       fewest in one sample, 0 if any sample had none
    /// @param max       most in one sample
    record ItemStats(long total, int withItem, int min, int max) {}

    /// @param empty samples without any item
    record Stats(int samples, int empty, Map<Item, ItemStats> items) {}

    /// Rolls `table` `samples` times. Uses `random` instead of the table's random sequence, which would advance the world's.
    static Stats sample(LootTable table, LootParams params, int samples, RandomSource random) {
        var tallies = new HashMap<Item, Tally>();
        int empty = 0;
        for (int sample = 0; sample < samples; sample++) {
            var counts = new HashMap<Item, Integer>();
            for (var stack : table.getRandomItems(params, random)) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
            if (counts.isEmpty()) {
                empty++;
            }
            counts.forEach((item, count) -> tallies.computeIfAbsent(item, _ -> new Tally()).add(count));
        }
        var items = new HashMap<Item, ItemStats>();
        tallies.forEach((item, tally) -> items.put(item, new ItemStats(tally.total, tally.withItem, tally.withItem < samples ? 0 : tally.min, tally.max)));
        return new Stats(samples, empty, items);
    }

    private static final class Tally {
        long total;
        int withItem;
        int min = Integer.MAX_VALUE;
        int max;

        void add(int count) {
            total += count;
            withItem++;
            min = Math.min(min, count);
            max = Math.max(max, count);
        }
    }

    private static int report(CommandSourceStack source, Holder<LootTable> table, int samples) {
        var level = source.getLevel();
        var player = source.getEntity() instanceof Player caller ? caller : null;
        float luck = player != null ? player.getLuck() : 0;
        var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(BlockPos.containing(source.getPosition())));
        // Like RandomizableContainer#unpackLootTable when a player opens the chest.
        if (player != null) {
            params.withLuck(luck).withParameter(LootContextParams.THIS_ENTITY, player);
        }
        var stats = sample(table.value(), params.create(LootContextParamSets.CHEST), samples, level.getRandom().fork());

        var name = table.unwrapKey().map(key -> key.identifier().toString()).orElse("inline");
        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.loot_stats.summary",
                name, samples, format(luck), percent(stats.empty(), samples)), false);
        stats.items().entrySet().stream()
                .sorted(Map.Entry.<Item, ItemStats>comparingByValue(Comparator.comparingLong(ItemStats::total)).reversed())
                .forEach(entry -> {
                    var item = entry.getValue();
                    source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.loot_stats.item",
                            entry.getKey().getDefaultInstance().getHoverName(),
                            format((double) item.total() / samples),
                            percent(item.withItem(), samples),
                            item.min(),
                            item.max()), false);
                });
        return stats.items().size();
    }

    private static String percent(int part, int whole) {
        return format(100.0 * part / whole);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
