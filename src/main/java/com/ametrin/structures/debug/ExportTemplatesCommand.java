package com.ametrin.structures.debug;

import com.ametrin.structures.util.ASLog;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// `/ametrin structures export_templates [namespace]` copies the templates structure blocks saved in this world into the project's resources at `data/<namespace>/structure/`.
/// Only namespaces that already have a `data/<namespace>` folder in the resources are exported, so edited vanilla or other mods' templates stay out.
///
/// Only exists in a development environment, because it writes into the project.
/// The resources are `src/main/resources` next to the run folder, or the folders listed in the [#SOURCES_PROPERTY] system property, separated like a classpath.
public final class ExportTemplatesCommand {
    public static final String SOURCES_PROPERTY = "ametrin_structures.template_sources";
    private static final String TEMPLATES = "structure";
    private static final String EXTENSION = ".nbt";

    private ExportTemplatesCommand() {}

    private enum Outcome {
        ADDED,
        CHANGED,
        UNCHANGED
    }

    /// The command, or empty outside a development environment or without resource folders.
    static Optional<LiteralArgumentBuilder<CommandSourceStack>> exportTemplates() {
        var roots = resourceRoots();
        if (roots.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Commands.literal("export_templates")
                .executes(context -> export(context.getSource(), roots, null))
                .then(Commands.argument("namespace", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(savedNamespaces(context.getSource().getServer()), builder))
                        .executes(context -> export(context.getSource(), roots, StringArgumentType.getString(context, "namespace")))));
    }

    /// The resource folders templates are exported to; empty outside a development environment.
    static List<Path> resourceRoots() {
        if (FMLEnvironment.isProduction()) {
            return List.of();
        }
        var declared = System.getProperty(SOURCES_PROPERTY);
        Stream<Path> candidates = declared != null
                ? Arrays.stream(declared.split(File.pathSeparator)).filter(path -> !path.isBlank()).map(Path::of)
                // The standard project layout runs the game in `<project>/run`.
                : Stream.ofNullable(FMLPaths.GAMEDIR.get().toAbsolutePath().getParent()).map(project -> project.resolve("src/main/resources"));
        return candidates.filter(Files::isDirectory).toList();
    }

    /// What an export did.
    ///
    /// @param skipped the namespaces the resources have no `data` folder for
    record Result(int added, int changed, int unchanged, List<String> skipped) {
        boolean isEmpty() {
            return added + changed + unchanged == 0 && skipped.isEmpty();
        }
    }

    /// @param namespace only exports it, or all when null
    private static int export(CommandSourceStack source, List<Path> roots, @Nullable String namespace) {
        Result result;
        try {
            result = export(source.getServer().getWorldPath(LevelResource.GENERATED_DIR), roots, namespace);
        } catch (IOException exception) {
            ASLog.error("exporting templates failed", exception);
            source.sendFailure(Component.translatable("commands.ametrin_structures.export_templates.failed", exception.toString()));
            return 0;
        }
        if (result.isEmpty()) {
            source.sendFailure(Component.translatable("commands.ametrin_structures.export_templates.none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.ametrin_structures.export_templates.exported", result.added(), result.changed(), result.unchanged()), true);
        if (!result.skipped().isEmpty()) {
            source.sendFailure(Component.translatable("commands.ametrin_structures.export_templates.skipped", String.join(", ", result.skipped())));
        }
        return result.added() + result.changed();
    }

    /// Copies the templates saved under `saved`, a world's `generated` folder, into the first of
    /// `roots` that has a `data` folder for their namespace.
    ///
    /// @param namespace only exports it, or all when null
    static Result export(Path saved, List<Path> roots, @Nullable String namespace) throws IOException {
        int added = 0;
        int changed = 0;
        int unchanged = 0;
        var skipped = new ArrayList<String>();
        for (var exported : savedNamespaces(saved)) {
            if (namespace != null && !namespace.equals(exported)) {
                continue;
            }
            var root = roots.stream().filter(candidate -> Files.isDirectory(dataFolder(candidate, exported))).findFirst();
            if (root.isEmpty()) {
                skipped.add(exported);
                continue;
            }
            var from = saved.resolve(exported).resolve(TEMPLATES);
            var to = dataFolder(root.get(), exported).resolve(TEMPLATES);
            for (var template : templatesIn(from)) {
                switch (copy(template, to.resolve(from.relativize(template)))) {
                    case ADDED -> added++;
                    case CHANGED -> changed++;
                    case UNCHANGED -> unchanged++;
                }
            }
        }
        return new Result(added, changed, unchanged, List.copyOf(skipped));
    }

    // Compares the templates' contents rather than their compressed bytes.
    private static Outcome copy(Path template, Path target) throws IOException {
        var exists = Files.isRegularFile(target);
        if (exists && NbtIo.readCompressed(template, NbtAccounter.unlimitedHeap()).equals(NbtIo.readCompressed(target, NbtAccounter.unlimitedHeap()))) {
            return Outcome.UNCHANGED;
        }
        Files.createDirectories(target.getParent());
        Files.copy(template, target, StandardCopyOption.REPLACE_EXISTING);
        ASLog.info("exported template {} to {}", template, target);
        return exists ? Outcome.CHANGED : Outcome.ADDED;
    }

    private static Path dataFolder(Path root, String namespace) {
        return root.resolve("data").resolve(namespace);
    }

    private static List<String> savedNamespaces(MinecraftServer server) {
        return savedNamespaces(server.getWorldPath(LevelResource.GENERATED_DIR));
    }

    // The namespaces templates were saved for.
    private static List<String> savedNamespaces(Path saved) {
        if (!Files.isDirectory(saved)) {
            return List.of();
        }
        try (var namespaces = Files.list(saved)) {
            return namespaces.filter(namespace -> Files.isDirectory(namespace.resolve(TEMPLATES)))
                    .map(namespace -> namespace.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            ASLog.warn("could not list the saved templates in {}: {}", saved, exception.toString());
            return List.of();
        }
    }

    private static List<Path> templatesIn(Path folder) throws IOException {
        try (var files = Files.walk(folder)) {
            return files.filter(file -> file.toString().endsWith(EXTENSION) && Files.isRegularFile(file)).sorted().toList();
        }
    }
}
