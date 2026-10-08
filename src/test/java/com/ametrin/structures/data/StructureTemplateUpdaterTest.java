package com.ametrin.structures.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.data.CachedOutput;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
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

    @Test
    void fixtureMarkersLeaveTheOldBlockStateForm(MinecraftServer server, @TempDir Path source) throws Exception {
        var file = source.resolve("data/examplemod/structure/crypt.nbt");
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(TagParser.parseCompoundFully("""
                {DataVersion: %d, size: [1, 1, 1], entities: [], palette: [{id: "ametrin_structures:fixture"}],
                 blocks: [{pos: [0, 0, 0], state: 0, nbt: {id: "ametrin_structures:fixture",
                   becomes: {Name: "minecraft:oak_stairs", Properties: {facing: "east"}},
                   fixtures: [
                     {type: "ametrin_structures:block_state", state: {Name: "minecraft:stone"}},
                     {type: "ametrin_structures:loot_container", loot_table: "minecraft:chests/simple_dungeon", block: {id: "minecraft:chest"}},
                     {type: "ametrin_structures:entity", entity: "minecraft:zombie", nbt: {Name: "kept"}},
                     {type: "othermod:unknown", state: {Name: "minecraft:stone"}}]}}]}
                """.formatted(SharedConstants.getCurrentVersion().dataVersion().version())), file);

        new StructureTemplateUpdater(List.of(source)).run(CachedOutput.NO_CACHE).join();

        var marker = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getListOrEmpty("blocks").getCompoundOrEmpty(0).getCompoundOrEmpty("nbt");
        assertEquals(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST), BlockState.CODEC.parse(NbtOps.INSTANCE, marker.get("becomes")).getOrThrow());
        var fixtures = marker.getListOrEmpty("fixtures");
        assertEquals(StringTag.valueOf("minecraft:stone"), fixtures.getCompoundOrEmpty(0).get("state"));
        assertEquals(TagParser.parseCompoundFully("{id: \"minecraft:chest\"}"), fixtures.getCompoundOrEmpty(1).get("block"), "the current form stays as written");
        assertEquals(TagParser.parseCompoundFully("{Name: \"kept\"}"), fixtures.getCompoundOrEmpty(2).get("nbt"), "only block state fields change");
        assertEquals(TagParser.parseCompoundFully("{Name: \"minecraft:stone\"}"), fixtures.getCompoundOrEmpty(3).get("state"), "an unknown type stays as stored");
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
