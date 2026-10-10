package com.ametrin.structures.data;

import com.ametrin.structures.util.ASLog;
import com.google.common.hash.Hashing;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/// Upgrades structure templates saved by an older game version, so the game doesn't have to upgrade them every time it loads them.
///
/// Rewrites every outdated `data/<namespace>/structure/**.nbt` under the source folders in place.
/// Pass the data generator's `--input` folders, from [GatherDataEvent#getInputs()]:
///
/// ```java
/// event.addProvider(new StructureTemplateUpdater(event.getInputs()));
/// ```
/// ```gradle
/// '--input', file('src/main/resources/').absolutePath
/// ```
/// The files are updated in place, not written to the generated output.
public final class StructureTemplateUpdater implements DataProvider {
    private static final String TEMPLATES = "structure";
    private static final String EXTENSION = ".nbt";
    // What the game assumes for templates without a data version, see TemplateSource.
    private static final int UNVERSIONED = 500;

    private final List<Path> sources;

    public StructureTemplateUpdater(Collection<Path> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        if (sources.isEmpty()) {
            ASLog.warn("{} has no source folders; give the data run an --input folder", getName());
            return CompletableFuture.completedFuture(null);
        }
        var executor = Util.backgroundExecutor().forName("StructureTemplateUpdater");
        return CompletableFuture.supplyAsync(this::templates, executor)
                .thenCompose(templates -> CompletableFuture.allOf(templates.stream()
                        .map(template -> CompletableFuture.runAsync(() -> update(template, output), executor))
                        .toArray(CompletableFuture[]::new)));
    }

    private List<Path> templates() {
        return sources.stream().flatMap(source -> {
            var data = source.resolve(PackType.SERVER_DATA.getDirectory());
            if (!Files.isDirectory(data)) {
                return Stream.empty();
            }
            try (var namespaces = Files.list(data)) {
                return namespaces.map(namespace -> namespace.resolve(TEMPLATES)).filter(Files::isDirectory).toList().stream()
                        .flatMap(StructureTemplateUpdater::filesIn);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }).toList();
    }

    private static Stream<Path> filesIn(Path folder) {
        try (var files = Files.walk(folder)) {
            return files.filter(file -> file.toString().endsWith(EXTENSION) && Files.isRegularFile(file)).toList().stream();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @SuppressWarnings("deprecation")
    private static void update(Path file, CachedOutput output) {
        try {
            var tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            int version = NbtUtils.getDataVersion(tag, UNVERSIONED);
            if (version >= SharedConstants.getCurrentVersion().dataVersion().version()) {
                return;
            }
            var bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(update(tag, version), bytes);
            var updated = bytes.toByteArray();
            output.writeIfNeeded(file, updated, Hashing.sha1().hashBytes(updated));
            ASLog.info("Updated structure template {} from data version {}", file, version);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not update structure template " + file, exception);
        }
    }

    // Load and save the template like vanilla's StructureUpdater. That rebuilds the palette and stamps the current version.
    private static CompoundTag update(CompoundTag tag, int version) {
        var template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK, DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), tag, version));
        return template.save(new CompoundTag());
    }

    @Override
    public String getName() {
        return "Structure template updater";
    }
}
