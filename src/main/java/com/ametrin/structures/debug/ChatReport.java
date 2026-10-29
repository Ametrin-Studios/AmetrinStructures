package com.ametrin.structures.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.*;

/// Sends a report to chat: a summary with a button that copies the report as tab-separated values, some extra lines, the first few lines, and the rest behind `[+N more]`.
final class ChatReport {
    static final int SHOWN_LINES = 10;
    private static final int KEPT_REPORTS = 16;

    // The lines `[+N more]` prints, for the latest few reports.
    private static final Map<Integer, List<Component>> HIDDEN_LINES = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, List<Component>> eldest) {
            return size() > KEPT_REPORTS;
        }
    };
    private static int nextReport;

    private ChatReport() {}

    /// The `more <report>` subcommand that `[+N more]` runs.
    static LiteralArgumentBuilder<CommandSourceStack> more() {
        return Commands.literal("more")
                .then(Commands.argument("report", IntegerArgumentType.integer(0))
                        .executes(command -> more(command.getSource(), IntegerArgumentType.getInteger(command, "report"))));
    }

    /// @param moreCommand the command that has the [#more()] subcommand, like `/ametrin loot_stats`
    static void send(CommandSourceStack source, MutableComponent summary, List<Component> extraLines, List<Component> lines, String tsv, String moreCommand) {
        var copy = Component.translatable("commands.ametrin_structures.report.copy").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.CopyToClipboard(tsv))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("commands.ametrin_structures.report.copy_hover"))));
        source.sendSuccess(() -> summary.append(" ").append(copy), false);
        extraLines.forEach(line -> source.sendSuccess(() -> line, false));
        lines.stream().limit(SHOWN_LINES).forEach(line -> source.sendSuccess(() -> line, false));
        if (lines.size() > SHOWN_LINES) {
            int report = nextReport++;
            HIDDEN_LINES.put(report, List.copyOf(lines.subList(SHOWN_LINES, lines.size())));
            source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.report.more", lines.size() - SHOWN_LINES).withStyle(style -> style
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.RunCommand(moreCommand + " more " + report))), false);
        }
    }

    private static int more(CommandSourceStack source, int report) {
        var lines = HIDDEN_LINES.remove(report);
        if (lines == null) {
            source.sendFailure(Component.translatable("commands.ametrin_structures.report.gone"));
            return 0;
        }
        lines.forEach(line -> source.sendSuccess(() -> line, false));
        return lines.size();
    }

    /// A line like ` 1.50 × name (75.00%)`.
    static MutableComponent line(LootStatsCommand.Counts counts, int samples, Object name) {
        return Component.translatable("commands.ametrin_structures.report.line", average(counts, samples), name, percent(counts.samplesWith(), samples));
    }

    /// A breakdown shown when hovering a line, and the same as tab-separated rows under `header`.
    record Breakdown(MutableComponent hover, String tsv) {}

    static Breakdown breakdown(String header, List<Map.Entry<String, LootStatsCommand.Counts>> entries, int samples) {
        var hover = Component.empty();
        var tsv = new StringBuilder("\n").append(header).append('\n');
        for (var entry : entries) {
            if (!hover.getSiblings().isEmpty()) {
                hover.append("\n");
            }
            hover.append(line(entry.getValue(), samples, entry.getKey()));
            tsv.append(entry.getKey()).append('\t').append(average(entry.getValue(), samples)).append('\t').append(percent(entry.getValue().samplesWith(), samples)).append('\n');
        }
        return new Breakdown(hover, tsv.toString());
    }

    static <K> List<Map.Entry<K, LootStatsCommand.Counts>> sortedByTotal(Map<K, LootStatsCommand.Counts> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<K, LootStatsCommand.Counts>comparingByValue(Comparator.comparingLong(LootStatsCommand.Counts::total)).reversed())
                .toList();
    }

    static String average(LootStatsCommand.Counts counts, int samples) {
        return format((double) counts.total() / samples);
    }

    static String percent(long part, int whole) {
        return format(100.0 * part / whole);
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
