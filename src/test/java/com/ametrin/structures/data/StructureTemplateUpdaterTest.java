package com.ametrin.structures.data;

import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(EphemeralTestServerProvider.class)
class StructureTemplateUpdaterTest {
    // 1.20.1, before `minecraft:grass` became `minecraft:short_grass`.
    private static final int OLD_VERSION = 3465;

    @Test
    void outdatedTemplatesAreFixedInPlace(MinecraftServer server, @TempDir Path source) throws IOException {
        var file = source.resolve("data/examplemod/structure/ruins/tower.nbt");
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(template(OLD_VERSION, "minecraft:grass"), file);

        new StructureTemplateUpdater(List.of(source)).run(CachedOutput.NO_CACHE).join();

        var updated = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        assertEquals(SharedConstants.getCurrentVersion().dataVersion().version(), NbtUtils.getDataVersion(updated));
        assertEquals("minecraft:short_grass", updated.getListOrEmpty("palette").getCompoundOrEmpty(0).getStringOr("id", ""));
    }

    @Test
    void currentTemplatesAreLeftAlone(MinecraftServer server, @TempDir Path source) throws IOException {
        var file = source.resolve("data/examplemod/structure/hut.nbt");
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(template(SharedConstants.getCurrentVersion().dataVersion().version(), "minecraft:stone"), file);
        var before = Files.readAllBytes(file);

        new StructureTemplateUpdater(List.of(source)).run(CachedOutput.NO_CACHE).join();

        assertArrayEquals(before, Files.readAllBytes(file));
    }

    private static CompoundTag template(int version, String block) {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", version);
        tag.put("size", list(1, 1, 1));
        var state = new CompoundTag();
        state.putString("Name", block);
        var palette = new ListTag();
        palette.add(state);
        tag.put("palette", palette);
        var info = new CompoundTag();
        info.put("pos", list(0, 0, 0));
        info.putInt("state", 0);
        var blocks = new ListTag();
        blocks.add(info);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static ListTag list(int... values) {
        var list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }
}
