package com.ametrin.structures.debug;

import com.ametrin.structures.util.ASLog;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/// `/ametrin structures check [namespace]` looks for broken references in every structure, or in those of one namespace, see [StructureCheck].
///
/// Chat shows the first problems, the log all of them.
public final class CheckCommand {
    private static final int MAX_LINES = 50;

    private CheckCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> check() {
        return Commands.literal("check")
                .executes(context -> check(context.getSource(), null))
                .then(Commands.argument("namespace", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                context.getSource().registryAccess().lookupOrThrow(Registries.STRUCTURE).listElementIds()
                                        .map(key -> key.identifier().getNamespace())
                                        .distinct(),
                                builder))
                        .executes(context -> check(context.getSource(), StringArgumentType.getString(context, "namespace"))));
    }

    private static int check(CommandSourceStack source, @Nullable String namespace) {
        Predicate<Holder.Reference<Structure>> structures = namespace == null
                ? _ -> true
                : structure -> structure.key().identifier().getNamespace().equals(namespace);
        var count = source.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().filter(structures).count();
        if (count == 0) {
            source.sendFailure(Component.translatable("commands.ametrin_structures.check.none", String.valueOf(namespace)));
            return 0;
        }

        var server = source.getServer();
        source.sendSuccess(() -> Component.translatable("commands.ametrin_structures.check.started", count), false);
        CompletableFuture.supplyAsync(() -> StructureCheck.run(server, structures), Util.backgroundExecutor())
                .whenCompleteAsync((report, error) -> {
                    if (error != null) {
                        ASLog.error("structure check failed", error);
                        source.sendFailure(Component.translatable("commands.ametrin_structures.check.failed", error.toString()));
                        return;
                    }
                    send(source, report);
                }, server);
        return (int) count;
    }

    private static void send(CommandSourceStack source, StructureCheck.Report report) {
        var problems = report.problems();
        if (problems.isEmpty()) {
            line(source, Component.translatable("commands.ametrin_structures.check.clean", report.structures(), report.templates())
                    .withStyle(ChatFormatting.GREEN));
        } else {
            line(source, Component.translatable("commands.ametrin_structures.check.summary", report.structures(), report.templates(), problems.size()));
        }
        if (!report.skipped().isEmpty()) {
            var types = report.skipped().entrySet().stream()
                    .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                    .collect(Collectors.joining(", "));
            line(source, Component.translatable("commands.ametrin_structures.check.skipped",
                    report.skipped().values().stream().mapToInt(Integer::intValue).sum(), types));
        }

        for (int i = 0; i < problems.size(); i++) {
            var problem = problems.get(i);
            var subject = Component.translatable(
                    "commands.ametrin_structures.check.subject." + problem.subject().name().toLowerCase(Locale.ROOT),
                    Component.literal(problem.id().toString()).withStyle(ChatFormatting.AQUA));
            ASLog.warn("structure check: {}: {}", subject.getString(), problem.message().getString());
            if (i < MAX_LINES) {
                line(source, Component.translatable("commands.ametrin_structures.check.problem", subject, problem.message()));
            }
        }
        if (problems.size() > MAX_LINES) {
            line(source, Component.translatable("commands.ametrin_structures.check.more", problems.size() - MAX_LINES));
        }
    }

    private static void line(CommandSourceStack source, Component line) {
        source.sendSuccess(() -> line, false);
    }
}
