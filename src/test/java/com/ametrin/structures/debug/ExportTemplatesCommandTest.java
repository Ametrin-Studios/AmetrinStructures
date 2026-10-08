package com.ametrin.structures.debug;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ExportTemplatesCommandTest {
    @Test
    void savedTemplatesAreCopiedIntoTheResourcesOfTheirNamespace(@TempDir Path folder) throws IOException {
        var saved = folder.resolve("generated");
        var resources = folder.resolve("resources");
        Files.createDirectories(resources.resolve("data/example"));
        write(saved.resolve("example/structure/houses/hut.nbt"), 1);
        write(saved.resolve("minecraft/structure/igloo/top.nbt"), 1);

        var first = ExportTemplatesCommand.export(saved, List.of(resources), null);
        assertEquals(new ExportTemplatesCommand.Result(1, 0, 0, List.of("minecraft")), first);
        var exported = resources.resolve("data/example/structure/houses/hut.nbt");
        assertEquals(1, NbtIo.readCompressed(exported, NbtAccounter.unlimitedHeap()).getIntOr("version", 0));
        assertTrue(Files.exists(saved.resolve("example/structure/houses/hut.nbt")), "the world keeps its copy");

        assertEquals(new ExportTemplatesCommand.Result(0, 0, 1, List.of()), ExportTemplatesCommand.export(saved, List.of(resources), "example"));

        write(saved.resolve("example/structure/houses/hut.nbt"), 2);
        assertEquals(new ExportTemplatesCommand.Result(0, 1, 0, List.of()), ExportTemplatesCommand.export(saved, List.of(resources), "example"));
        assertEquals(2, NbtIo.readCompressed(exported, NbtAccounter.unlimitedHeap()).getIntOr("version", 0));
    }

    @Test
    void theCommandsJoinAnAmetrinCommandRegisteredBefore() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(Commands.literal("ametrin").then(Commands.literal("other")));
        StructuresCommand.register(dispatcher);
        var ametrin = dispatcher.getRoot().getChild("ametrin");
        assertNotNull(ametrin.getChild("other"));
        assertNotNull(ametrin.getChild("structures").getChild("check"));
    }

    private static void write(Path file, int version) throws IOException {
        Files.createDirectories(file.getParent());
        var tag = new CompoundTag();
        tag.putInt("version", version);
        NbtIo.writeCompressed(tag, file);
    }
}
